/*
 * MessageViewActivity.java - This class holds the activity to view an individual message
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator;

import net.hagander.mailinglistmoderator.backend.MailMessage;
import net.hagander.mailinglistmoderator.backend.MailMessage.statuslevel;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
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
		((TextView) findViewById(R.id.TextView_Sender)).setText(
				String.format("From: %s\n", message.getSender()));
		((Button) findViewById(R.id.Button_Accept)).setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				message.setStatus(statuslevel.Accept);
				finish();
			}});
		((Button) findViewById(R.id.Button_Reject)).setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				message.setStatus(statuslevel.Reject);
				finish();
			}});
	}
}
