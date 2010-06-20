/*
 * ListServerAdapter.java - This class holds an ArrayAdapter for the main activity.
 * 
 * Copyright (C) 2010 Magnus Hagander <magnus@hagander.net>
 * 
 * This software is released under the BSD license.
 */
package net.hagander.mailinglistmoderator.glue;

import java.util.Vector;

import net.hagander.mailinglistmoderator.R;
import net.hagander.mailinglistmoderator.backend.ListServer;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * @author Magnus Hagander <magnus@hagander.net>
 * 
 */
public class ListServerAdapter extends ArrayAdapter<ListServer> {
	private Vector<ListServer> items;

	public ListServerAdapter(Context context, int textViewResourceId,
			Vector<ListServer> servers) {
		super(context, textViewResourceId, servers);

		items = servers;
	}

	/**
	 * Create a view that contains the information we want to show about each
	 * list.
	 */
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = convertView;

		if (v == null) {
			LayoutInflater vi = (LayoutInflater) getContext().getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
			v = vi.inflate(R.layout.main_item, null);
		}
		ListServer o = items.get(position);
		if (o != null) {
			TextView n = (TextView) v.findViewById(R.id.TextView_ServerName);
			TextView s = (TextView) v.findViewById(R.id.TextView_ServerStatus);

			n.setText(o.getName());
			s.setText(o.getStatus());
		}
		return v;
	}
}
