/*
 * MessageViewActivity.java - This class holds the activity to view an individual message
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator;

import net.hagander.mailinglistmoderator.backend.MailMessage;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * 
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public class MessageViewActivity extends Activity {
	private MailMessage message;

	// Ugly hack to pass message to the activity when it's started
	private static MailMessage _passedMessage;

	public static void setMessage(MailMessage message) {
		_passedMessage = message;
	}

	public MessageViewActivity() {
		super();
		message = _passedMessage;
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setTitle(message.getSubject());

		setContentView(R.layout.mailview);
		((TextView) findViewById(R.id.TextView_Body)).setText(message
				.getContent());
	}
}
