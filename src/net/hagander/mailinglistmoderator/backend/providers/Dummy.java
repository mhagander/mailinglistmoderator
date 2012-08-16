/*
 * Dummy.java - This class implements a dummy mailinglist provider, to make debugging easier.
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator.backend.providers;

import java.util.Vector;

import net.hagander.mailinglistmoderator.backend.ListServer;
import net.hagander.mailinglistmoderator.backend.MailMessage;
import net.hagander.mailinglistmoderator.backend.MailMessage.statuslevel;

/**
 * 
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public class Dummy extends ListServer {
	public Dummy(String name, String rooturl, String password) {
		super(name, rooturl, password, null, null);
	}

	/**
	 * Enumerate all messages on the list, and return them as an Vector.
	 */
	@Override
	protected Vector<MailMessage> EnumerateMessages() {
		Vector<MailMessage> messages = new Vector<MailMessage>();

		// One in 5 will cause an error
		if (Math.random() > 0.8)
			throw new RuntimeException("Something bad happened.");

		for (int i = 0; i < Math.random() * 8 + 2; i++) {
			messages.add(new DummyMessage());
		}
		
		// Now let's also make it take some time, to emulate a real run
		try {
			Thread.sleep((long) (1000 * Math.random()*4));
		} catch (InterruptedException e) {
		}
		return messages;
	}

	/**
	 * This dummy provider will implement individual message moderation.
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
		Vector<DummyMessage> msglist = new Vector<DummyMessage>();

		for (int i = 0; i < messages.size(); i++) {
			DummyMessage msg = (DummyMessage) messages.get(i);
			if (msg.getStatus() != statuslevel.Defer) {
				msglist.add(msg);
			}
		}
		callbacks.SetMessageCount(msglist.size());

		for (int i = 0; i < msglist.size(); i++) {
			callbacks.SetStatusMessage(String.format(
					"Moderating message %d of %d", i+1, msglist.size()));

			try {
				Thread.sleep(750);
			} catch (InterruptedException e) {
			}
			callbacks.SetProgressbarValue(i+1);
		}

		return true;
	}

	/**
	 * Dummy implementation of MailMessage.
	 */
	private class DummyMessage extends MailMessage {
		public DummyMessage() {
			super("sender@dummy.dummy", "Dummy message subject", "Contents of a dummy message\nContents of a dummy message\n");
		}
	}
}
