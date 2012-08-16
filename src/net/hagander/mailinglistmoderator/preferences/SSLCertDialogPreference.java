/*
 * SSLCertDialogPreference.java - This class holds a dialog preference for polling SSL certs
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator.preferences;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

import net.hagander.mailinglistmoderator.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.DialogPreference;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

public class SSLCertDialogPreference extends DialogPreference implements OnClickListener {
	private class StringTuple {
		String description;
		String value;
		public StringTuple(String description, String value) {
			this.description = description;
			this.value = value;
		}
	}

	private String sslCertError = null;
	private String sslCertFingerprint = null;
	private ArrayList<StringTuple> sslCertDetails = null;

	private EditText fingerprintField;
	private ProgressBar progressSpinner;
	private TableLayout certDetailsTableLayout;
	private TextView certErrorText;
	private Button copyButton;

	public SSLCertDialogPreference(Context context) {
		super(context, null);

	    setDialogLayoutResource(R.layout.sslcertdialogprefview);
	}

	@Override
	protected void onBindDialogView(final View view) {
		fingerprintField = (EditText) view.findViewById(R.id.editCertFingerprint);
		progressSpinner = (ProgressBar) view.findViewById(R.id.progressBar1);
		certErrorText = (TextView) view.findViewById(R.id.textViewCertError);
		certDetailsTableLayout = (TableLayout) view.findViewById(R.id.tableLayoutCertDetails);
		copyButton = (Button) view.findViewById(R.id.buttonGetFromServer);
		copyButton.setOnClickListener(this);
		((Button) view.findViewById(R.id.buttonClear)).setOnClickListener(this);


	    SharedPreferences pref = getSharedPreferences();
	    fingerprintField.setText(pref.getString(getKey(), ""));

	    super.onBindDialogView(view);

	    new Thread(new Runnable() {
			public void run() {
				FetchSSLCertDescription();
				view.post(new Runnable() {
					public void run() {
						progressSpinner.setVisibility(View.GONE);
						if (sslCertError != null) {
							certErrorText.setText(sslCertError);
							certErrorText.setVisibility(View.VISIBLE);
							return;
						}
						for (StringTuple r : sslCertDetails) {
							TableRow tr = new TableRow(certDetailsTableLayout.getContext());
							tr.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
							TextView tv_d = new TextView(certDetailsTableLayout.getContext());
							tv_d.setText(r.description + ":");
							TextView tv_v = new TextView(certDetailsTableLayout.getContext());
							tv_v.setText(r.value);
							tr.addView(tv_d);
							tr.addView(tv_v);
							certDetailsTableLayout.addView(tr, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
						}
						if (sslCertFingerprint != null) {
							copyButton.setEnabled(true);
						}
					}
				});
			}
	    }).start();
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
	    if(!positiveResult)
	        return;

	    SharedPreferences.Editor editor = getEditor();
	    editor.putString(getKey(), fingerprintField.getText().toString());
	    editor.commit();
	    setSummary(fingerprintField.getText());
	}

	public void onClick(View view) {
		String btn = ((Button)view).getText().toString();
		if (btn.equals("Copy from info below")) {
			if (sslCertFingerprint != null)
				fingerprintField.setText(sslCertFingerprint);
		}
		else if (btn.equals("Clear")) {
			fingerprintField.setText("");
		}
	}

	private void FetchSSLCertDescription() {
		/*
		 * Fetch the description and the fingerprint for the currently configured
		 * BASE URL.
		 */

		/* 
		 * We need to read the other preference, by stripping it out from our own key.
		 * Do that by removing the " 
		 */
	    SharedPreferences pref = getSharedPreferences();
	    String baseurl = pref.getString(getKey().replace("_whitelistedcert", "_baseurl"), "FAIL");
	    if (baseurl.equals("")) {
	    	sslCertError = "Base URL must be set before this preference can be fetched.";
	    	return;
	    }

	    URLConnection c;
	    try {
	    	final URL u = new URL(baseurl);
	    	c = u.openConnection();
	    }
	    catch (Exception e) {
	    	sslCertError = "Failed to parse or open URL!";
	    	return;
	    }

		SSLContext context;
		try {
			context = SSLContext.getInstance("TLS");
		} catch (NoSuchAlgorithmException e) {
			sslCertError =  "Could not find TLS context!";
			return;
		}
		HttpsURLConnection sslconn  = (HttpsURLConnection) c;
		try {
			context.init(null,
					new X509TrustManager[] { new X509TrustManager() {
						public void checkClientTrusted(
								X509Certificate[] chain, String authType)
								throws CertificateException {
						}

						public void checkServerTrusted(
								X509Certificate[] chain, String authType)
								throws CertificateException {
							MessageDigest md;
							try {
								md = MessageDigest.getInstance("SHA-1");
							} catch (NoSuchAlgorithmException e) {
								sslCertError = "Could not find SHA-1 digest";
								throw new CertificateException();
							}
							md.update(chain[0].getEncoded());
							byte[] digest = md.digest();
							BigInteger bi = new BigInteger(1, digest);
						    String fingerprint = String.format("%0" + (digest.length << 1) + "X", bi);

						    ArrayList<StringTuple> r = new ArrayList<StringTuple>();
						    r.add(new StringTuple("Subject", chain[0].getSubjectDN().toString()));
						    r.add(new StringTuple("Valid from", chain[0].getNotBefore().toLocaleString()));
						    r.add(new StringTuple("Valid to", chain[0].getNotAfter().toLocaleString()));
						    r.add(new StringTuple("Fingerprint", fingerprint));
						    r.add(new StringTuple("Chain length", String.format("%d",chain.length)));

						    sslCertDetails = r;
							sslCertFingerprint = fingerprint;
						}

						public X509Certificate[] getAcceptedIssuers() {
							return new X509Certificate[0];
						}
					} }, null);
		} catch (KeyManagementException e) {
			sslCertError = String.format("Unable to set up key management: %s", e.toString());
			return;
		}
		sslconn.setSSLSocketFactory(context.getSocketFactory());

		/*
		 * We must also turn off hostname verification so we don't get an exception
		 * when connecting.
		 */
		sslconn.setHostnameVerifier(new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		});

		try {
			sslconn.connect(); /* This should trigger our callback */
			sslconn.disconnect();
		}
		catch (IOException e) {
			sslCertError = String.format("Unable to connect to server: %s", e.toString());
		}
	}
}
