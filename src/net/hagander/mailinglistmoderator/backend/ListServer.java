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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Vector;

import org.xmlpull.v1.XmlSerializer;

import net.hagander.mailinglistmoderator.backend.MailMessage.statuslevel;
import net.hagander.mailinglistmoderator.backend.providers.Dummy;
import net.hagander.mailinglistmoderator.backend.providers.Mailman;
import net.hagander.mailinglistmoderator.backend.providers.Majordomo2;
import net.hagander.mailinglistmoderator.backend.providers.Unconfigured;
import android.content.SharedPreferences;

/**
 * 
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public abstract class ListServer {
	protected String listname;
	protected String rooturl;
	protected String password;

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
	public ListServer(String name, String rooturl, String password) {
		this.listname = name;
		this.rooturl = rooturl;
		this.password = password;

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
	 * @return A ListServer instance representing this server.
	 */
	public static ListServer Create(String name, String rooturl, String password) {
		if (rooturl.startsWith("dummy:"))
			return new Dummy(name, rooturl, password);
		if (rooturl.contains("/admindb"))
			return new Mailman(name, rooturl, password);
		if (rooturl.contains("mj_wwwadm"))
			return new Majordomo2(name, rooturl, password);
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

		return Create(name, baseurl, password);
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
	protected String FetchUrl(String url) {
		try {
			URL u = new URL(url);
			URLConnection c = u.openConnection();
			InputStreamReader isr = new InputStreamReader(c.getInputStream());
			BufferedReader r = new BufferedReader(isr);
			StringWriter sw = new StringWriter();
			String line;
			while ((line = r.readLine()) != null) {
				sw.write(line);
				sw.write("\n");
			}
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
		xml.endTag(null, "list");
	}
}
