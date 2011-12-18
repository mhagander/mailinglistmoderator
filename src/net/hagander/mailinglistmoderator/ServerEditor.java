/*
 * ServerEditor.java - This class holds the activity for editing preferences.
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmlpull.v1.XmlSerializer;

import net.hagander.mailinglistmoderator.backend.ListServer;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Xml;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.EditText;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public class ServerEditor extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private SharedPreferences prefs;

	/* Menu constants */
	private final int MENU_NEW_SERVER = 1;
	private final int MENU_EXPORT_SERVERS = 2;
	private final int MENU_IMPORT_SERVERS = 3;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		setPreferenceScreen(getRootPreferenceScreen());

		registerForContextMenu(getListView());

        prefs.registerOnSharedPreferenceChangeListener(this);
	}

	private final int MENU_COPY_SERVER = 1;
	private final int MENU_DELETE_SERVER = 2;
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		menu.add(Menu.NONE, MENU_COPY_SERVER, 1, "Copy server");
		menu.add(Menu.NONE, MENU_DELETE_SERVER, 2, "Delete server");
	}
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item
		.getMenuInfo();

		PreferenceScreen ps = (PreferenceScreen)getListView().getAdapter().getItem(menuInfo.position);
		final String name = (String) ps.getTitle();

		if (item.getItemId() == MENU_DELETE_SERVER) {
			SharedPreferences.Editor editor = prefs.edit();
			editor.remove(name + "_listname");
			editor.remove(name + "_baseurl");
			editor.remove(name + "_password");
			editor.commit();

			for (ListServer s: MailinglistModerator.servers){
				if (s.getName().equals(name)) {
					MailinglistModerator.servers.remove(s);
					break;
				}
			}
			setPreferenceScreen(getRootPreferenceScreen());
			return true;
		}
		if (item.getItemId() == MENU_COPY_SERVER) {
			final EditText edit = new EditText(this);
			new AlertDialog.Builder(this).setTitle("Copy server").setMessage(
			"Enter list name").setView(edit).setPositiveButton(
			"Create", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					String newname = edit.getText().toString();
					if (newname.length() < 2)
						return;

					SharedPreferences.Editor editor = prefs.edit();
					editor.putString(newname + "_listname", newname);
					editor.putString(newname + "_baseurl", prefs.getString(name + "_baseurl", ""));
					editor.putString(newname + "_password", prefs.getString(name + "_password", ""));
					editor.commit();

					MailinglistModerator.servers.add(ListServer.CreateFromPreference(prefs, newname));
					setPreferenceScreen(getRootPreferenceScreen());
				}
			}).show();
		}

		return true;
	}

	/**
	 * Return the root of the preferences screen that we want to show. The main
	 * thing here is the list of all the servers.
	 */
	private PreferenceScreen getRootPreferenceScreen() {
		PreferenceScreen root = getPreferenceManager().createPreferenceScreen(
				this);

		root.setTitle("Servers");
		for (int i = 0; i < MailinglistModerator.servers.size(); i++)
			root.addPreference(getOneServerSet(MailinglistModerator.servers
					.get(i).getName()));
		return root;
	}

	/**
	 * Return a preference screen for one individual server.
	 */
	private PreferenceScreen getOneServerSet(String name) {
		PreferenceScreen screen = getPreferenceManager()
				.createPreferenceScreen(this);
		screen.setTitle(name);

		/* Create textbox for the base URL */
		EditTextPreference e_baseurl = new EditTextPreference(this);
		e_baseurl.setKey(name + "_baseurl");
		e_baseurl.getEditText().setInputType(InputType.TYPE_TEXT_VARIATION_URI);
		e_baseurl.setTitle("Base URL");
		e_baseurl.setDialogTitle("Base URL");
		e_baseurl.setSummary(prefs.getString(name+"_baseurl",""));
		screen.addPreference(e_baseurl);

		/* Create textbox for password */
		EditTextPreference e_password = new EditTextPreference(this);
		e_password.setKey(name + "_password");
		e_password.getEditText().setInputType(
				InputType.TYPE_TEXT_VARIATION_PASSWORD);
		e_password.getEditText().setTransformationMethod(
				new PasswordTransformationMethod());
		e_password.setTitle("Password");
		e_password.setDialogTitle("Password");
		screen.addPreference(e_password);

		return screen;
	}

	/**
	 * Create the main menu.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_NEW_SERVER, 0, "New server");
		menu.add(0, MENU_EXPORT_SERVERS, 1, "Export");
		menu.add(0, MENU_IMPORT_SERVERS, 2, "Import");
		return true;
	}

	/**
	 * Handle clicks in the main menu.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_NEW_SERVER:
			final EditText edit = new EditText(this);
			new AlertDialog.Builder(this).setTitle("New server").setMessage(
					"Enter list name").setView(edit).setPositiveButton(
					"Create", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							String name = edit.getText().toString();
							if (name.length() < 2)
								return;

							SharedPreferences.Editor editor = prefs.edit();
							editor.putString(name + "_listname", name);
							editor.putString(name + "_baseurl", "");
							editor.putString(name + "_password", "");
							editor.commit();

							MailinglistModerator.servers.add(ListServer.CreateFromPreference(prefs, name));
							setPreferenceScreen(getRootPreferenceScreen());
						}
					}).show();
			return true;
		case MENU_EXPORT_SERVERS:
			ExportServers();
			return true;
		case MENU_IMPORT_SERVERS:
			ImportServers();
			return true;
		}
		return false;
	}

	private void ExportServers() {
		final EditText edit = new EditText(this);
		edit.setText("mailinglists.xml");
		new AlertDialog.Builder(this).setTitle("Export servers").setMessage(
				"Enter filename").setView(edit).setPositiveButton(
				"Export", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						String filename = edit.getText().toString();
						if (filename.length() < 2)
							return;

						/* We're going to stick this in the default path always */
						filename = "/sdcard/" + filename;
						try {
							FileOutputStream strm = new FileOutputStream(filename);
							XmlSerializer xml = Xml.newSerializer();
							xml.setOutput(strm, "utf-8");
							xml.startDocument("utf-8", false);
							xml.startTag(null, "mailinglists");
							for (int i = 0; i < MailinglistModerator.servers.size(); i++) {
								ListServer s = MailinglistModerator.servers.get(i);
								s.writeXmlElement(xml);
							}
							xml.endTag(null,  "mailinglists");
							xml.endDocument();
							xml.flush();
							strm.close();
						}
						catch (IOException ex) {
							new AlertDialog.Builder(ServerEditor.this).setTitle("Failed to write file").setMessage(ex.getMessage()).show();
						}
					}
				}).show();
	}

	private void ImportServers() {
		final EditText edit = new EditText(this);
		edit.setText("mailinglists.xml");
		new AlertDialog.Builder(this).setTitle("Import servers").setMessage(
				"Enter filename").setView(edit).setPositiveButton(
				"Import", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						String filename = edit.getText().toString();
						if (filename.length() < 2)
							return;

						int duplicates = 0;
						int loaded = 0;

						Document doc = null;
						/* We're going to stick this in the default path always */
						filename = "/sdcard/" + filename;
						try {
							FileInputStream strm = new FileInputStream(filename);
							DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
							doc = builder.parse(strm);
						}
						catch (Exception ex) {
							new AlertDialog.Builder(ServerEditor.this).setTitle("Failed to write file").setMessage(ex.getMessage()).show();
						}

						Element root = doc.getDocumentElement();
						if (!root.getNodeName().equals("mailinglists")) {
							new AlertDialog.Builder(ServerEditor.this).setTitle("Failed to parse file").setMessage(String.format("Root node is not of type mailinglists (%s)!", root.getNodeName())).show();
							return;
						}
						NodeList listElements = root.getElementsByTagName("list");
						/* Now that we knew we could parse everything, do the actual loop */
						elementsloop:
						for (int i = 0; i < listElements.getLength(); i++) {
							Element node = (Element) listElements.item(i);
							String name = node.getAttribute("name");
							String baseurl = node.getAttribute("url");
							String password = node.getAttribute("password");

							/* Find out if this node already exists */
							for (ListServer s : MailinglistModerator.servers) {
								if (s.getName().equals(name)) {
									duplicates++;
									continue elementsloop;
								}
							}

							/* Does not exist, so let's add it */
							SharedPreferences.Editor editor = prefs.edit();
							editor.putString(name + "_listname", name);
							editor.putString(name + "_baseurl", baseurl);
							editor.putString(name + "_password", password);
							editor.commit();

							MailinglistModerator.servers.add(ListServer.CreateFromPreference(prefs, name));

							loaded++;
						}

						/* Let the user know what happened */
						String msg = String.format("Imported %d new servers, ignored %d duplicates.", loaded, duplicates);
						new AlertDialog.Builder(ServerEditor.this).setTitle("Import complete.").setMessage(msg).show();

						if (loaded > 0) {
							setPreferenceScreen(getRootPreferenceScreen());
						}
					}
				}).show();
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		if (key.endsWith("_baseurl")) {
			// This is a text field, so update the value when it has been edited
			Preference pref = findPreference(key);
			if (pref != null) {
				pref.setSummary(sharedPreferences.getString(key,""));
			}
		}
	}
}
