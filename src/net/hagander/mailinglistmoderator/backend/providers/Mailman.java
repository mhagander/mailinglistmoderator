/*
 * Mailman.java - This class implements mailinglist management for GNU Mailman lists.
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator.backend.providers;

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.hagander.mailinglistmoderator.backend.ListServer;
import net.hagander.mailinglistmoderator.backend.MailMessage;
import net.hagander.mailinglistmoderator.backend.MailMessage.statuslevel;
import android.util.Log;

/**
 * 
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public class Mailman extends ListServer {
	public Mailman(String name, String rooturl, String password, String override_certname, String whitelisted_cert) {
		super(name, rooturl, password, override_certname, whitelisted_cert);
	}

	/*
	 * Regular expressions for matching message lists and contents.
	 */
	private static final Pattern enumMailPattern = Pattern
			.compile(
					"<table CELLPADDING=\"0\" WIDTH=\"100%\" CELLSPACING=\"0\">(.*?)</table>\\s+<p>",
					Pattern.DOTALL);
	private static final Pattern messageContentPattern = Pattern
			.compile(
					"<td ALIGN=\"right\"><strong>From:</strong></td>\\s+<td>([^<]+)</td>.*?<td ALIGN=\"right\"><strong>Subject:</strong></td>\\s+<td>([^<]*)</td>.*?<td><TEXTAREA NAME=fulltext-(\\d+) ROWS=10 COLS=76 WRAP=soft READONLY>([^<]+)</TEXTAREA></td>",
					Pattern.DOTALL);
	private static final Pattern authorizationFailedPattern = Pattern
			.compile(
					"<strong><font size=\"\\+1\">Authorization\\s+failed.</font></strong>",
					Pattern.DOTALL);

	/**
	 * Enumerate all messages on the list, and return them as an Vector.
	 */
	@Override
	protected Vector<MailMessage> EnumerateMessages() {
		Vector<MailMessage> messages = new Vector<MailMessage>();

		// Fetch the details=all page which contains everything we need.
		String page = FetchUrl(String.format("%s/%s/?details=all&adminpw=%s",
				rooturl, listname, password));

		/*
		 * Check for no such list
		 */
		if (page.contains("<h2>Mailman Administrative Database Error</h2>No such list <em>")) {
			status = "List does not exist on server";
			return null;
		}
		/*
		 * Check for login failure
		 */
		if (authorizationFailedPattern.matcher(page).find()) {
			status = "Authorization failed - invalid password?";
			return null;
		}

		/*
		 * Attempt to locate all the messages in the queue.
		 */
		Matcher m = enumMailPattern.matcher(page);
		while (m.find()) {
			Matcher sm = messageContentPattern.matcher(m.group(1));
			if (sm.find()) {
				// Got a message
				// group(1) == from
				// group(2) == subject
				// group(3) == id
				// group(4) == contents
				messages.add(new MailmanMessage(Integer.parseInt(sm.group(3)),
						sm.group(1), sm.group(2), sm.group(4)));
			}
		}
		return messages;
	}

	/**
	 * In mailman we moderate a whole batch of messages in a single call.
	 */
	@Override
	public boolean doesIndividualModeration() {
		return false;
	}

	/**
	 * Apply any queued moderations to this list.
	 */
	@Override
	public boolean applyChanges(ListServerStatusCallbacks callbacks) {
		// The whole moderation operation will go in a single querystring
		StringBuilder str = new StringBuilder();
		str.append(rooturl);
		str.append("/");
		str.append(listname);
		str.append("/?");

		// Collect a querystring with all the message ids we are moderating and
		// what to do with them.
		int count = 0;
		for (int i = 0; i < messages.size(); i++) {
			MailmanMessage msg = (MailmanMessage) messages.get(i);
			if (msg.getStatus() != statuslevel.Defer) {
				str.append(String.format("%d=%d&", msg.id, msg
						.getStatusPostCode()));
				count++;
			}
		}

		if (count == 0)
			/*
			 * Should never happen, but just in case, so we don't construct a
			 * bad URL
			 */
			return false;

		callbacks.SetStatusMessage(String.format("Moderating %d messages...",
				count));

		// Append our password to the request
		str.append("adminpw=");
		str.append(password);

		/*
		 * Unfortunately mailman doesn't actually tell us if our modifications
		 * succeeded or not. We'll get an exception if the call failed, of
		 * course, but not if we passed invalid data.
		 */
		try {
			FetchUrl(str.toString());
		} catch (Exception ex) {
			callbacks.ShowError(ex.toString());
			return false;
		}

		return true;
	}

	/**
	 * Implementation of MailMessage holding additional mailman-specific
	 * properties.
	 */
	private class MailmanMessage extends MailMessage {
		private int id;

		public MailmanMessage(int id, String sender, String subject,
				String content) {
			super(sender, subject, content);
			this.id = id;
		}

		/**
		 * Map the status code to the POST value in a mailman form.
		 */
		public int getStatusPostCode() {
			switch (getStatus()) {
			case Accept:
				return 1;
			case Reject:
				return 3; // map reject to discard, consider supporting both in
							// the future
			}
			return 0; // default to defer if we're called with something
						// unexpected.
		}
	}
}
