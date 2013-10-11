/*
 * Copyright 2013 i'm SpA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.imwatch.nfclottery.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.support.v4.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import de.keyboardsurfer.android.widget.crouton.Style;
import it.imwatch.nfclottery.DataHelper;
import it.imwatch.nfclottery.MainActivity;
import it.imwatch.nfclottery.R;

import static it.imwatch.nfclottery.Const.DEBUG;

/**
 * A dialog that can start a winner flag clearing operation in the
 * database, after asking for confirmation to the user.
 */
public class ClearWinnersDialog extends DialogFragment {

    private static final String TAG = ClearWinnersDialog.class.getSimpleName();

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        final Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Not attached to Activity: cannot build dialog");
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.action_clear_winners)
               .setMessage(R.string.dialog_clear_winners_message)
               .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       clearWinners();
                   }
               })
               .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       final Dialog thisDialog = ClearWinnersDialog.this.getDialog();
                       if (thisDialog != null) {
                           thisDialog.cancel();
                       }
                       else {
                           Log.w(TAG, "Can't get the Dialog instance.");
                       }
                   }
               });

        // Create the AlertDialog object and return it
        return builder.create();
    }

    /**
     * Clears the winner flag from all contacts in the database.
     */
    private void clearWinners() {
        final Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Not attached to Activity: cannot clear winners in DB");
            return;
        }

        DataHelper.clearAllWinners(activity);

        if (activity instanceof MainActivity) {
            final MainActivity mainActivity = (MainActivity) activity;
            mainActivity.showCroutonNao(mainActivity.getString(R.string.info_winners_cleared),
                                        Style.INFO);
            mainActivity.updateParticipantsCount();
        }
        else {
            Log.e(TAG, "The parent Activity is not MainActivity! Wat is this I don't even");
            if (DEBUG) Log.d(TAG, "Activity class: " + activity.getLocalClassName());
            Toast.makeText(activity, activity.getString(R.string.clear_winners_failed_wrong_parent), Toast.LENGTH_SHORT)
                 .show();
        }
    }
}
