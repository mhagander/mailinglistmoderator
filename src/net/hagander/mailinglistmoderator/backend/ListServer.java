/*
 * ListServer.java - This class holds an abstract base class for implementing a mailinglist server
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator.backend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

import org.xmlpull.v1.XmlSerializer;

import net.hagander.mailinglistmoderator.backend.MailMessage.statuslevel;
import net.hagander.mailinglistmoderator.backend.providers.Dummy;
import net.hagander.mailinglistmoderator.backend.providers.Mailman;
import net.hagander.mailinglistmoderator.backend.providers.Majordomo2;
import net.hagander.mailinglistmoderator.backend.providers.Unconfigured;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public abstract class ListServer {
	protected String listname;
	protected String rooturl;
	protected String password;
	protected String override_certname;
	protected String whitelisted_cert;

	protected boolean populated;
	protected boolean exceptioned;
	protected String status;
	protected Vector<MailMessage> messages;

	/**
	 * 
	 * @param name
	 *            Name of the list
	 * @param rooturl
	 *            URL of the root mailinglist server installation (not including
	 *            list name)
	 * @param password
	 *            Password to access the server (both read and write)
	 */
	public ListServer(String name, String rooturl, String password, String override_certname, String whitelisted_cert) {
		this.listname = name;
		this.rooturl = rooturl;
		this.password = password;
		this.override_certname = override_certname;
		this.whitelisted_cert = whitelisted_cert;

		this.populated = false;
		this.exceptioned = false;
		this.messages = new Vector<MailMessage>();
	}

	/**
	 * Create a list server instance, by figuring out which type of list it is,
	 * and instantiating the proper class.
	 * 
	 * @param name
	 *            Name of the list
	 * @param rooturl
	 *            Root URL of the server, not including the list name
	 * @param password
	 *            Password to access the server
	 * @param override_certname
	 *            SSL certificate name to accept
	 * @param whitelisted_cert
	 *            SSL certificate fingerprint to whitelist
	 * @return A ListServer instance representing this server.
	 */
	public static ListServer Create(String name, String rooturl, String password, String override_certname, String whitelisted_cert) {
		if (rooturl.startsWith("dummy:"))
			return new Dummy(name, rooturl, password);
		if (rooturl.contains("/admindb"))
			return new Mailman(name, rooturl, password, override_certname, whitelisted_cert);
		if (rooturl.contains("mj_wwwadm"))
			return new Majordomo2(name, rooturl, password, override_certname, whitelisted_cert);
		return new Unconfigured(name, rooturl, password);
	}

	/**
	 * Create a ListServer instance by reading the application preferences for
	 * it.
	 * 
	 * @param pref
	 *            Instance of SharedPreferences to use.
	 * @param name
	 *            Name of the list.
	 * @return A ListServer instance representing this server.
	 */
	public static ListServer CreateFromPreference(SharedPreferences pref,
			String name) {
		String baseurl = pref.getString(name + "_baseurl", "");
		String password = pref.getString(name + "_password", "");
		String override_certname = pref.getString(name+"_overridecertname", "");
		String whitelisted_cert = pref.getString(name+"_whitelistedcert", "");

		return Create(name, baseurl, password, override_certname, whitelisted_cert);
	}

	/*
	 * Abstract methods, do be implemented by child classes.
	 */
	protected abstract Vector<MailMessage> EnumerateMessages();

	public abstract boolean doesIndividualModeration();

	public abstract boolean applyChanges(ListServerStatusCallbacks callbacks);

	/**
	 * Return the name of the list.
	 * 
	 * @return the name of the list
	 */
	public String getName() {
		return listname;
	}

	/**
	 * Get the current status for this list (loading, number of messages etc).
	 * 
	 * @return a string representing the current status for this list
	 */
	public String getStatus() {
		if (exceptioned)
			return String.format("Exception: %s", status);
		else if (populated)
			return status;
		else
			return "loading...";
	}

	/**
	 * Check if this has been queried and populated with a list of mails to
	 * moderate.
	 * 
	 * @return if the list is populated.
	 */
	public boolean isPopulated() {
		return populated;
	}

	/**
	 * Check if an exception has occurred when processing this list.
	 * 
	 * @return if the list is exceptioned.
	 */
	public boolean isExceptioned() {
		// TODO Auto-generated method stub
		return exceptioned;
	}

	/**
	 * Get all messages in the moderation queue on the list.
	 * 
	 * @return a list of all messages on this list
	 */
	public Vector<MailMessage> getMessages() {
		return messages;
	}

	/**
	 * Populate the list with messages by querying the server.
	 * 
	 * Also sets the local status string.
	 */
	public void Populate() {
		messages.clear();
		populated = exceptioned = false;
		try {
			Vector<MailMessage> msglist = EnumerateMessages();
			if (msglist != null) {
				messages.addAll(EnumerateMessages());
				populated = true;
				status = String.format("%d unmoderated messages", messages.size());
			}
			else {
				// Status is assumed to be set by by the routine that failed
				populated = false;
				exceptioned = true;
			}
		}
		catch (RuntimeException e) {
			this.exceptioned = true;
			this.status = String.format("%s", e.getMessage());
			throw e;
		}
	}

	/**
	 * Get the number of messages in the queue.
	 * 
	 * @return number of messages
	 */
	public int count() {
		return messages.size();
	}

	/**
	 * Check if the queue has any changes to apply (any messages marked as
	 * Accept or Reject, not Defer)
	 * 
	 * @return if there are any queued changes.
	 */
	public boolean hasChanges() {
		for (int i = 0; i < messages.size(); i++)
			if (messages.get(i).getStatus() != statuslevel.Defer)
				return true;
		return false;
	}

	/**
	 * Interface listing callbacks from moderation operations that may take
	 * time.
	 */
	public interface ListServerStatusCallbacks {
		public void SetStatusMessage(String msg);

		public void SetProgressbarValue(int value);

		public void ShowError(String msg);

		public void SetMessageCount(int size);
	}

	/*
	 * Utility functions for implementations to call.
	 */

	/**
	 * Connect and fetch an URL, returning a string with the contents of the
	 * URL.
	 */
	private Pattern DNPattern = Pattern.compile("^CN=([^,]+),", Pattern.CASE_INSENSITIVE);
	protected String FetchUrl(String url) {
		try {
			final URL u = new URL(url);
			URLConnection c = u.openConnection(java.net.Proxy.NO_PROXY);

			if (u.getProtocol().equals("https")) {
				HttpsURLConnection sslconn  = (HttpsURLConnection) c;
				if (override_certname != null && !override_certname.equals("")) {
					sslconn.setHostnameVerifier(new HostnameVerifier() {
						public boolean verify(String hostname, SSLSession session) {
							/* For each certificate, check */
							X509Certificate cert;
							try {
								cert = (X509Certificate)session.getPeerCertificates()[0];
							}
							catch (SSLPeerUnverifiedException e) {
								throw new RuntimeException(String.format(
										"Failed to verify peer for url: %s (%s)", e, u.toString()));
							}
							Matcher m = DNPattern.matcher(cert.getSubjectDN().getName());
							if (!m.find()) {
								throw new RuntimeException(String.format(
										"Could not extract hostname from '%s' for url %s", cert.getSubjectDN(), u.toString()));
							}
							String sslname = m.group(1);
							if (sslname.equals(override_certname)) {
								/* Matched the overridden certname, so allow this connection */
								return true;
							}

							/*
							 * Could return false here, but that won't show as
							 * useful error message, so throw RuntimeEception
							 * instead
							 */
							throw new RuntimeException(String.format(
									"Certificate hostname '%s' does not match expected hostname '%s'", sslname, override_certname));
						}
					});
				}

				/* Let's see if we should also check the actual certificate */
				if (whitelisted_cert != null && !whitelisted_cert.equals("")) {
					SSLContext context;
					try {
						context = SSLContext.getInstance("TLS");
					} catch (NoSuchAlgorithmException e) {
						throw new RuntimeException("Could not find TLS context!");
					}
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
											throw new RuntimeException("Could not find SHA-1 digest");
										}
										md.update(chain[0].getEncoded());
										byte[] digest = md.digest();
										BigInteger bi = new BigInteger(1, digest);
									    String fingerprint = String.format("%0" + (digest.length << 1) + "X", bi);

									    if (!fingerprint.equals(whitelisted_cert)) {
									    	throw new CertificateException("Certificate fingerprint does not match the configured one!");
									    }

									    /* Otherwise, if it matches, we said override, so trust everything */
									}

									public X509Certificate[] getAcceptedIssuers() {
										return new X509Certificate[0];
									}
								} }, null);
					} catch (KeyManagementException e) {
						throw new RuntimeException(String.format("Unable to set up key management: %s", e.toString()));
					}
					sslconn.setSSLSocketFactory(context.getSocketFactory());
				}
			}
			InputStreamReader isr = new InputStreamReader(c.getInputStream());
			BufferedReader r = new BufferedReader(isr);
			StringWriter sw = new StringWriter();
			String line;
			while ((line = r.readLine()) != null) {
				sw.write(line);
				sw.write("\n");
			}
			r.close();
			isr.close();
			return sw.toString();
		} catch (MalformedURLException e) {
			throw new RuntimeException(String.format(
					"Failed to fetch url: %s (%s)", e, url));
		} catch (IOException e) {
			throw new RuntimeException(String.format(
					"Failed to fetch url: %s (%s)", e, url));
		}
	}

	public void writeXmlElement(XmlSerializer xml) throws IOException {
		xml.startTag(null, "list");
		xml.attribute(null, "name", listname);
		xml.attribute(null, "url", rooturl);
		xml.attribute(null, "password", password);
		if (override_certname != null && !override_certname.equals(""))
			xml.attribute(null, "overridecertname", override_certname);
		if (whitelisted_cert != null && !whitelisted_cert.equals(""))
			xml.attribute(null, "whitelistedcert", whitelisted_cert);
		xml.endTag(null, "list");
	}
}
