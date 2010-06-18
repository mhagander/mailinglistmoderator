/*
 * ServerEditor.java - This class holds the activity for editing preferences.
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

/**
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public class ServerEditor extends PreferenceActivity {

	/* Menu constants */
	private final int MENU_NEW_SERVER = 1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setPreferenceScreen(getRootPreferenceScreen());
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

							SharedPreferences.Editor editor = PreferenceManager
									.getDefaultSharedPreferences(
											getBaseContext()).edit();
							editor.putString(name + "_listname", name);
							editor.putString(name + "_baseurl", "");
							editor.putString(name + "_password", "");
							editor.commit();

							// Return with resultCode = 2 to indicate we want
							// the parent to re-launch this Activity
							// (this doesn't really work)
							setResult(RESULT_CANCELED);
							finish();
						}
					}).show();
			return true;
		}
		return false;
	}
}
