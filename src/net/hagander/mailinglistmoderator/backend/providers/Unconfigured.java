/*
 * Unconfirued.java - This class holds implements a placeholder mailinglist manager for a list
 *                    that hasn't been configured yet and as such can't have it's type
 *                    identified.
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator.backend.providers;

import java.util.Vector;

import net.hagander.mailinglistmoderator.backend.ListServer;
import net.hagander.mailinglistmoderator.backend.MailMessage;

/**
 * 
 * @author Magnus Hagander <magnus@hagander.net>
 *
 */
public class Unconfigured extends ListServer {

	public Unconfigured(String name, String rooturl, String password) {
		super(name, rooturl, password, null, null);
	}

	@Override
	protected Vector<MailMessage> EnumerateMessages() {
		return new Vector<MailMessage>();
	}

	@Override
	public boolean applyChanges(ListServerStatusCallbacks callbacks) {
		return false;
	}

	@Override
	public boolean doesIndividualModeration() {
		return false;
	}

	@Override
	public void Populate() {
		populated = true;
		status = String.format("Unconfigured list");
	}
}
