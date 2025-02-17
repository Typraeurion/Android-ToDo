/*
 * $Id: RepeatEditorDialog.java,v 1.2 2011/07/18 00:46:26 trevin Exp trevin $
 * Copyright © 2011 Trevin Beattie
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * $Log: RepeatEditorDialog.java,v $
 * Revision 1.2  2011/07/18 00:46:26  trevin
 * Added the copyright notice
 *
 * Revision 1.1  2010/11/23 03:33:09  trevin
 * Initial revision
 *
 */
package com.xmission.trevin.android.todo;

import android.app.AlertDialog;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

/**
 * A simple dialog containing a complex {@link RepeatEditor}.
 */
public class RepeatEditorDialog extends AlertDialog implements OnClickListener {
    private static final String LOG_TAG = "RepeatEditorDialog";

    private final RepeatEditor repeatEditor;
    private final OnRepeatSetListener callback;

    /** The callback used to indicate the user has finished setting the repeat. */
    public interface OnRepeatSetListener {
	/**
	 * @param view The view associated with this listener.
	 * @param repeat The repeat settings.
	 */
	void onRepeatSet(RepeatEditor view, RepeatSettings repeat);
    }

    /** Create a new repeat editor dialog for the given settings */
    public RepeatEditorDialog(Context context, OnRepeatSetListener callback) {
	super(context);
	this.callback = callback;
	Log.d(LOG_TAG, "creating");
	setTitle(context.getResources().getString(R.string.RepeatTitle));

	setButton(context.getText(R.string.ConfirmationButtonOK), this);
	setButton2(context.getText(R.string.ConfirmationButtonCancel), this);
	setIcon(R.drawable.ic_dialog_time);

	LayoutInflater inflater = (LayoutInflater)
		context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	View view = inflater.inflate(R.layout.repeat_dialog, null);
	setView(view);
	repeatEditor = (RepeatEditor)
		view.findViewById(R.id.RepeatEditor);
	Log.d(LOG_TAG, "created");
    }

    /** Reset the repeat settings in the dialog */
    public void setRepeatSettings(RepeatSettings settings) {
	repeatEditor.setRepeatSettings(settings);
    }

    /** @return the repeat settings currently set in this dialog */
    public RepeatSettings getRepeatSettings() {
	return repeatEditor.getRepeatSettings();
    }

    /** Called when the user clicks either the Cancel or the OK button */
    @Override
    public void onClick(DialogInterface dialog, int which) {
	Log.d(LOG_TAG, ".onClick(dialog,"
		+ ((which == DialogInterface.BUTTON1) ? "OK"
			: ((which == DialogInterface.BUTTON2) ? "Cancel"
				: Integer.toString(which))) + ")");
	if ((which == DialogInterface.BUTTON1) && (callback != null)) {
	    callback.onRepeatSet(repeatEditor, repeatEditor.getRepeatSettings());
	}
	dismiss();
    }

}
