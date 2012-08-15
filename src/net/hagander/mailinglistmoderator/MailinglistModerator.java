/*
 * MailinglistModerator.java - This class holds the main activity for the program.
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Vector;

import net.hagander.mailinglistmoderator.backend.ListServer;
import net.hagander.mailinglistmoderator.glue.ListServerAdapter;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public class MailinglistModerator extends ListActivity {
	public static Vector<ListServer> servers;
	private ListServerAdapter serverAdapter;
	private SharedPreferences prefs;

	/* Menu constants */
	private final int MENU_EDIT_SERVERS = 1;
	private final int MENU_REFRESH = 2;

	/* Return codes when calling sub-actions */
	private final int REQUEST_CODE_EDITSERVERS = 7;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

		servers = new Vector<ListServer>();
		LoadServers();

		serverAdapter = new ListServerAdapter(this, R.layout.main_item, servers);
		setListAdapter(serverAdapter);

		ListView lv = getListView();

		/* Handle server clicks by launching the QueueListActivity */
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				ListServer s = serverAdapter.getItem(position);
				if (s.isExceptioned()) {
					/* Show the complete error message */
					new AlertDialog.Builder(MailinglistModerator.this)
					  .setTitle("Failed to load")
					  .setMessage(String.format("This list failed to load with the following exception:\n\n%s", s.getStatus()))
					  .setPositiveButton("Ok", null)
					  .show();
					return;
				}
				if (!s.isPopulated())
					return;

				/*
				 * Ugly way to pass information to the QueueListActivity we're
				 * going to start - pass it through a static method.
				 */
				QueueListActivity.setServerInfo(s, MailinglistModerator.this);
				startActivity(new Intent(getApplicationContext(),
						QueueListActivity.class));
			}
		});

		/* Populate list of unmoderated messages in the background */
		populateServers();
	}

	/**
	 * Load the list of servers from the application preferences. Doesn't
	 * actually connect and populate information about the server, just loads
	 * the list.
	 * 
	 * If there is a server list already, it's cleared and replaced with the new
	 * one.
	 */
	private void LoadServers() {
		servers.clear();

		for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
			// Any property that ends in _listname is considered one of our
			// servers.
			if (entry.getKey().endsWith("_listname")) {
				try {
					servers
							.add(ListServer.CreateFromPreference(prefs, entry
									.getKey().substring(0,
											entry.getKey().length() - 9)));
				} catch (Exception ex) {
					final String msg = ex.toString();
					/* FIXME: replace with alertbox! */
					runOnUiThread(new Runnable() {
						public void run() {
							Toast.makeText(getApplicationContext(), msg,
									Toast.LENGTH_SHORT).show();
						}
					});
				}
			}

		}
	}

	/**
	 * Notify that the list of servers has changed, and do so on the UI thread
	 * to make it safe for calling from anywhere.
	 */
	public void notifyServersChanged() {
		runOnUiThread(new Runnable() {
			public void run() {
				serverAdapter.notifyDataSetChanged();
			}
		});
	}

	/**
	 * Populate all our servers with information about unmoderated messages, by
	 * connecting to the server and enumerating.
	 * 
	 * All checking will be run in a background thread.
	 */
	private void populateServers() {
		/*
		 * Update the server list before we get started, since it may take a
		 * while...
		 */
		notifyServersChanged();

		Runnable r = new Runnable() {
			public void run() {
				Vector<Thread> threads = new Vector<Thread>();
				
				for (int i = 0; i < servers.size(); i++) {
					final ListServer s = servers.get(i);
					Thread t = new Thread(new Runnable() {
						public void run() {
							// TODO Auto-generated method stub
							try {
								s.Populate();
							} catch (Exception e) {
								final String msg = String.format("%s", e);
								runOnUiThread(new Runnable() {
									public void run() {
										// FIXME: find better way to report errors
										Toast.makeText(getApplicationContext(), msg,
												Toast.LENGTH_SHORT).show();
									}
								});
							}

							/*
							 * Since servers are sorted by number of messages, re-sort
							 * the list when it has updated.
							 * 
							 * We run this once in each loop so that servers with
							 * messages to moderate on will "bubble up" to the top as we
							 * run.
							 */
							sortServers();
						}
					});
					threads.add(t);
					t.start();
				}

				/*
				 * Now wait until all threads are done, but join()ing on them
				 */
				for (int i = 0; i < threads.size(); i++) {
					try {
						threads.get(i).join();
					} catch (InterruptedException e) {
					}
				}
			}
		};
		Thread t = new Thread(r, "ServerPopulatingThread");
		t.start();
	}

	private void sortServers() {
		Collections.sort(servers, new Comparator<ListServer>() {
			public int compare(ListServer server1,
					ListServer server2) {
				if (!server1.isPopulated()) {
					if (!server2.isPopulated()) {
						// Neither server is populated, sort by name
						return server1.getName().compareTo(server2.getName());
					}
					// server1 is not populated, server2 is ==> server2 is bigger
					return 1;
				}
				else if (!server2.isPopulated()) {
					// server2 is not populated, server1 is ==> server1 is bigger.
					return -1;
				}
				// Both servers are populated
				if (server2.count() == server1.count()) {
					// Count is identical, so compare by name
					return server1.getName().compareTo(server2.getName());
				}
				// Both servers have valid values, and they are not the same,
				// so return the comparison of them.
				return server2.count() - server1.count();
				}
		});

		notifyServersChanged();
	}

	/**
	 * Create the menu for when the Menu button is pressed.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_REFRESH, 0, "Refresh");
		menu.add(0, MENU_EDIT_SERVERS, 1, "Servers...");
		return true;
	}

	/**
	 * Handle selections in the main menu.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_EDIT_SERVERS:
			/*
			 * Edit servers - so launch the ServerEditor activity. We need to
			 * track the result of this activity, so we can reload the list when
			 * it returns.
			 */
			Intent i = new Intent(getApplicationContext(), ServerEditor.class);
			startActivityForResult(i, REQUEST_CODE_EDITSERVERS);
			return true;
		case MENU_REFRESH:
			/* Refresh the server list */
			LoadServers();
			populateServers();
			return true;
		}
		return false;
	}

	/**
	 * Callback for whenever a sub-activity finishes.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_EDITSERVERS) {
			/*
			 * The closed activity is our ServerEditor one.
			 * 
			 * It will return resultCode=2 if it has added a new server, in
			 * which case we just want to restart the Activity so it'll pick up
			 * the new entry.
			 * 
			 * In all other cases, just reload all the servers in case any vital
			 * configuration has changed.
			 */
			if (resultCode == 2) {
				Intent i = new Intent(getApplicationContext(),
						ServerEditor.class);
				startActivityForResult(i, REQUEST_CODE_EDITSERVERS);
			} else {
				LoadServers();
				populateServers();
			}
		}
	}
}