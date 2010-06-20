/*
 * Dummy.java - This class implements a dummy mailinglist provider, to make debugging easier.
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator.backend.providers;

import java.util.ArrayList;

import net.hagander.mailinglistmoderator.backend.ListServer;
import net.hagander.mailinglistmoderator.backend.MailMessage;

/**
 * 
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public class Dummy extends ListServer {
	public Dummy(String name, String rooturl, String password) {
		super(name, rooturl, password);
	}

	/**
	 * Enumerate all messages on the list, and return them as an ArrayList.
	 */
	@Override
	protected ArrayList<MailMessage> EnumerateMessages() {
		ArrayList<MailMessage> messages = new ArrayList<MailMessage>();

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
		for (int i = 0; i < messages.size(); i++) {
			callbacks.SetStatusMessage(String.format(
					"Moderating message %d of %d", i, messages.size()));
			callbacks.SetProgressbarPercent(i * 100 / messages.size());

			try {
				Thread.sleep(750);
			} catch (InterruptedException e) {
			}
		}

		// Make sure we exit with a full progressbar.
		callbacks.SetProgressbarPercent(100);

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
