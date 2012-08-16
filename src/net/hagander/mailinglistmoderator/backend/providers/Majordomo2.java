/*
 * Majordomo2.java - This class implements mailinglist management for Majordomo2 lists.
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

/**
 * 
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public class Majordomo2 extends ListServer {
	public Majordomo2(String name, String rooturl, String password, String override_certname, String whitelisted_cert) {
		super(name, rooturl, password, override_certname, whitelisted_cert);
	}

	/*
	 * Regular expressions for matching message lists and contents.
	 */

	private static final Pattern enumMailPattern = Pattern
			.compile(
					"<td><input type=\"checkbox\" name=\"extra\"\\s+value=\"([^\"]+)\"><a href=\"[^\"]+\"\\s+target=\"_token\">[^<]+</a>\\s*</td>\\s*<td>\\s*post",
					Pattern.DOTALL);
	private static final Pattern mailDetailsPattern = Pattern
			.compile(
					"<tr><td>From\\s+</td><td>([^<]+)</td>.*?<tr><td>Subject\\s+</td><td>([^<]+)</td>.*?<pre>\\s+([^<]+)\\s*</pre>",
					Pattern.DOTALL);
	private static final Pattern mailDetailsNoSubjectPattern = Pattern
	.compile(
			"<tr><td>From\\s+</td><td>([^<]+)</td>.*?<pre>\\s+([^<]+)\\s*</pre>",
			Pattern.DOTALL);
	private static final Pattern mailDetailsNoTextPattern = Pattern
			.compile(
			"<tr><td>From\\s+</td><td>([^<]+)</td>.*?<tr><td>Subject\\s+</td><td>([^<]+)</td>.*?<p>\\s\\[Part",
			Pattern.DOTALL);
	private static final Pattern nolistPattern = Pattern
			.compile(
			"<pre>\\*{4} The &quot;(.*?)&quot; mailing list is not supported at",
			Pattern.DOTALL);

	/**
	 * Enumerate all messages on the list, and return them as an Vector.
	 */
	@Override
	protected Vector<MailMessage> EnumerateMessages() {
		Vector<MailMessage> messages = new Vector<MailMessage>();

		// Fetcha list of all the tokens in "consult" mode
		String page = FetchUrl(String.format(
				"%s?passw=%s&list=%s&func=showtokens-consult", rooturl,
				password, listname));

		/*
		 * Check for no such list
		 */
		if (nolistPattern.matcher(page).find()) {
			status = "List does not exist on server";
			return null;
		}
		/*
		 * Check for login failure
		 */
		if (page.contains("<pre>The password is invalid.  Some common reasons for this error are:")) {
			status = "Authorization failed - invalid password?";
			return null;
		}


		Matcher m = enumMailPattern.matcher(page);
		while (m.find()) {
			/*
			 * Majordomo2, in it's infinite wisdom, doesn't include the subject
			 * line on the list. So we need to fetch the actual message contents
			 * once for each to get it.
			 */
			String url = String.format(
					"%s?passw=%s&list=%s&func=tokeninfo&extra=%s", rooturl,
					password, listname, m.group(1));
			String subpage = FetchUrl(url);
			if (subpage == null) {
				/*
				 * No tokeninfo returned here. Just ignore this message - maybe
				 * somebody moderated it while we were looking at others.
				 */
				continue;
			}
			/*
			 * Attempt to match a mail that has a text part, which is what majordomo will show us.
			 */
			Matcher sm = mailDetailsPattern.matcher(subpage);
			if (sm.find()) {
				messages.add(new Majordomo2Message(m.group(1), sm.group(1), sm
						.group(2), sm.group(3)));
				continue;
			}
			sm = mailDetailsNoSubjectPattern.matcher(subpage);
			if (sm.find()) {
				messages.add(new Majordomo2Message(m.group(1), sm.group(1),
						"No subject", sm.group(2)));
				continue;
			}

			/*
			 * Attempt to match a mail that *doesn't* have a text part.
			 */
			sm = mailDetailsNoTextPattern.matcher(subpage);
			if (sm.find()) {
				/*
				 * This will require yet another fetch in order to get the text of the first part.
				 */
				url = String.format(
						"%s?passw=%s&list=%s&func=tokeninfo-part&extra=%s%%201", rooturl,
						password, listname, m.group(1));
				subpage = FetchUrl(url);
				if (subpage == null) {
					/*
					 * Couldn't get text, just ignore it.
					 */
					continue;
				}
				messages.add(new Majordomo2Message(m.group(1), sm.group(1),
						sm.group(2), subpage));
				continue;
			}
		}
		return messages;
	}

	/**
	 * Extremely trivial implementation of decoding some HTML escapes for nicer
	 * viewing.
	 */
	private static String trivialDecode(String s) {
		return s.replaceAll("&quot;", "\"").replaceAll("&lt;", "<").replaceAll(
				"&gt;", ">");
	}

	/**
	 * In majordomo2 we moderate each message individually.
	 */
	@Override
	public boolean doesIndividualModeration() {
		return true;
	}

	/**
	 * Apply any moderations to this list.
	 */
	@Override
	public boolean applyChanges(ListServerStatusCallbacks callbacks) {
		/*
		 * Collect all the messages we're actually going to do moderation on in
		 * it's own list.
		 */
		Vector<Majordomo2Message> msglist = new Vector<Majordomo2Message>();

		for (int i = 0; i < messages.size(); i++) {
			Majordomo2Message msg = (Majordomo2Message) messages.get(i);
			if (msg.getStatus() != statuslevel.Defer) {
				msglist.add(msg);
			}
		}
		if (msglist.size() == 0)
			/*
			 * Should never happen, but just in case, so we don't construct a
			 * bad URL
			 */
			return false;

		callbacks.SetMessageCount(msglist.size());
		/*
		 * Now that we know how many, moderate each individual one.
		 */
		for (int i = 0; i < msglist.size(); i++) {
			callbacks.SetStatusMessage(String.format(
					"Moderating message %d of %d", i+1, msglist.size()));

			try {
				FetchUrl(String.format("%s?passw=%s&list=%s&func=%s&extra=%s",
						rooturl, password, listname, msglist.get(i)
								.getMajordomoFunc(), msglist.get(i).token));
			} catch (Exception ex) {
				callbacks.ShowError(ex.toString());
				return false;
			}
			callbacks.SetProgressbarValue(i+1);
		}

		return true;
	}

	/**
	 * Implementation of MailMessage holding additional majordomo2-specific
	 * properties.
	 */
	private class Majordomo2Message extends MailMessage {
		private String token;

		public Majordomo2Message(String token, String sender, String subject,
				String content) {
			super(trivialDecode(sender), trivialDecode(subject), content);
			this.token = token;
		}

		/**
		 * Map the status code to the text representation to be used on a
		 * Majordomo2 form
		 */
		public String getMajordomoFunc() {
			switch (getStatus()) {
			case Accept:
				return "accept";
			case Reject:
				return "reject-quiet"; // configurable in the future to allow
										// non-quiet?
			}
			return "thisisnotafunctionandshouldneverbecalled";
		}
	}
}
