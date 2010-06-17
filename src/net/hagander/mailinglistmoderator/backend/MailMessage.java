/*
 * MailMessage.java - This class holds an abstract base class for implementing a mailinglist-specific
 *                    message with required identifiers and content.
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator.backend;

/**
 * 
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 *         This base class is simply a container for some common properties.
 */
public abstract class MailMessage {
	private String sender;
	private String subject;
	private String content;
	private statuslevel status = statuslevel.Defer;

	public enum statuslevel {
		Accept, Reject, Defer
	};

	public MailMessage(String sender, String subject, String content) {
		this.sender = sender;
		this.subject = subject;
		this.content = content;
	}

	public String getSender() {
		return sender;
	}

	public String getSubject() {
		return subject;
	}

	public String getContent() {
		return content;
	}

	public void setStatus(statuslevel status) {
		this.status = status;
	}

	public statuslevel getStatus() {
		return status;
	}
}
