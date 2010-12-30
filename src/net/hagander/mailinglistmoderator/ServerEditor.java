/*
 * ServerEditor.java - This class holds the activity for editing preferences.
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator;

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
		}
		return false;
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
