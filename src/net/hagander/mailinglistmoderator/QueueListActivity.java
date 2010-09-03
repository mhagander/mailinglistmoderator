/*
 * QueueListActivity.java - This class holds the activity for listing messages on a single server.
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator;

import net.hagander.mailinglistmoderator.backend.ListServer;
import net.hagander.mailinglistmoderator.backend.MailMessage;
import net.hagander.mailinglistmoderator.backend.ListServer.ListServerStatusCallbacks;
import net.hagander.mailinglistmoderator.backend.MailMessage.statuslevel;
import net.hagander.mailinglistmoderator.glue.MailMessageAdapter;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

/**
 * 
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public class QueueListActivity extends ListActivity implements
		ListServerStatusCallbacks {
	private ListServer server;
	private MailinglistModerator parent;
	private MailMessageAdapter messageAdapter;

	/* Menu constants */
	private final int MENU_ACCEPT = 1;
	private final int MENU_REJECT = 2;
	private final int MENU_DEFER = 3;
	private final int MENU_APPLY = 4;

	// Ugly hack to pass server and parent information to newly created activity
	private static ListServer _passedServer;
	private static MailinglistModerator _passedParent;

	public static void setServerInfo(ListServer server,
			MailinglistModerator parent) {
		_passedServer = server;
		_passedParent = parent;
	}

	public QueueListActivity() {
		super();
		server = _passedServer;
		parent = _passedParent;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setTitle(String
				.format("Moderation queue for %s", server.getName()));

		messageAdapter = new MailMessageAdapter(this, R.layout.mail_item,
				server.getMessages());
		setListAdapter(messageAdapter);

		ListView lv = getListView();

		/*
		 * Handle clicks on an individual item in the queue by starting the
		 * MessageViewActivity to show the contents of the message.
		 */
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				MailMessage m = messageAdapter.getItem(position);

				MessageViewActivity.setMessage(m);
				startActivityForResult(new Intent(getApplicationContext(),
						MessageViewActivity.class), 1);
				/*
				 * FIXME Intent i = new Intent();
				 * i.setClassName("net.hagander.mailinglistmoderator",
				 * "net.hagander.mailinglistmoderator.MessageViewActivity");
				 * startActivity(i);
				 */
			}
		});

		/*
		 * Set up a press-and-hold context menu for each message
		 */
		lv.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
			public void onCreateContextMenu(ContextMenu menu, View v,
					ContextMenuInfo menuInfo) {
				menu.add(Menu.NONE, MENU_ACCEPT, 1, "Accept");
				menu.add(Menu.NONE, MENU_REJECT, 2, "Reject");
				menu.add(Menu.NONE, MENU_DEFER, 3, "Defer");
			}

		});
	}

	private class QueueListActivityThread extends Thread {
		protected QueueListActivity activity;
		public QueueListActivityThread(QueueListActivity activity) {
			super();
			this.activity = activity;
		}
	}
	/*
	 * Apply any changes that are queued up - that is, any action that's Accept
	 * or Reject, not defer.
	 * 
	 * The changes are made by a background thread, but a status dialog is shown
	 * while they run.
	 */
	private ProgressDialog progressDialog;

	private void applyAllChanges() {
		if (server.hasChanges()) {
			progressDialog = new ProgressDialog(this);
			if (server.doesIndividualModeration()) {
				/*
				 * If the backend does moderation on an individual message
				 * basis, we enable toe progressbar in the dialog to show how
				 * far along we are.
				 */
				progressDialog
						.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			}
			progressDialog.setTitle("Applying...");
			progressDialog.setMessage("Sending moderation requests...");
			progressDialog.show();

			new QueueListActivityThread(this) {
				@Override
				public void run() {
					if (server.applyChanges(activity)) {
						// Reload the moderation queue (for this server only)
						SetStatusMessage("Reloading moderation queue...");
						server.Populate();

						/* Move up to 100%, for a microsecond or two */
						if (server.doesIndividualModeration()) {
							progressDialog.setProgress(progressDialog.getMax());
						}

						// Turn off progress window
						progressDialog.dismiss();

						/*
						 * Since the count of messages may have changed, we need to
						 * tell the parent view as well.
						 */
						parent.notifyServersChanged();

						/*
						 * Did we moderate all the messages?
						 */
						if (server.count() == 0)
							// Drop out to main screen if there are no more
							// messages
							finish();
						else {
							// Otherwise, refresh our list
							runOnUiThread(new Runnable() {
								public void run() {
									messageAdapter.notifyDataSetChanged();
								}
							});
						}
					} else {
						// Changes failed, error message already shown, just get
						// rid of the dialog.
						progressDialog.dismiss();
					}
				}
			}.start();
		}
	}

	/*
	 * Methods to implement ListServerStatusCallback
	 */
	public void SetProgressbarValue(final int value) {
		runOnUiThread(new Runnable() {
			public void run() {
				progressDialog.setProgress(value);
			}
		});
	}

	public void SetMessageCount(final int size) {
		runOnUiThread(new Runnable() {
			public void run() {
				progressDialog.setMax(size+1);
			}
		});
	}

	public void SetStatusMessage(final String msg) {
		runOnUiThread(new Runnable() {
			public void run() {
				progressDialog.setMessage(msg);
			}
		});
	}

	public void ShowError(final String msg) {
		runOnUiThread(new Runnable() {
			public void run() {
				new AlertDialog.Builder(parent).setCancelable(false)
						.setMessage(msg).setNegativeButton("OK",
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int which) {
										return;
									}
								}).show();
			}
		});
	}

	/* End of ListServerStatusCallback implementation */

	/**
	 * return a "statuslevel" value that corresponds to the menu item that was
	 * chosen.
	 */
	private statuslevel getStatusLevelFromMenuId(int menuId) {
		switch (menuId) {
		case MENU_ACCEPT:
			return statuslevel.Accept;
		case MENU_REJECT:
			return statuslevel.Reject;
		case MENU_DEFER:
			return statuslevel.Defer;
		}
		return statuslevel.Defer;
	}

	/**
	 * Handle clicks on the press-and-hold context menu, by simply setting the
	 * message status to the one corresponding to the chosen level.
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item
				.getMenuInfo();
		MailMessage message = messageAdapter.getItem(menuInfo.position);
		message.setStatus(getStatusLevelFromMenuId(item.getItemId()));
		messageAdapter.notifyDataSetChanged();
		return true;
	}

	/**
	 * Create the main menu.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_ACCEPT, 0, "Accept all");
		menu.add(1, MENU_REJECT, 1, "Reject all");
		menu.add(2, MENU_DEFER, 2, "Defer all");
		menu.add(3, MENU_APPLY, 3, "Apply moderation");
		return true;
	}

	/**
	 * Handle clicks in the main menu.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == MENU_ACCEPT || item.getItemId() == MENU_REJECT
				|| item.getItemId() == MENU_DEFER) {
			statuslevel l = getStatusLevelFromMenuId(item.getItemId());

			for (int i = 0; i < messageAdapter.getCount(); i++) {
				messageAdapter.getItem(i).setStatus(l);
			}
			messageAdapter.notifyDataSetChanged();
			return true;
		}
		if (item.getItemId() == MENU_APPLY) {
			applyAllChanges();
			return true;
		}
		return false;

	}

	/**
	 * Refresh when child activity returns
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		/* We only have one child activity now, so just notify always.. */
		messageAdapter.notifyDataSetChanged();
	}
}