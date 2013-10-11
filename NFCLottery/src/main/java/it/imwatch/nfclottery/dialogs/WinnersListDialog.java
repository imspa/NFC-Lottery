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
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import it.imwatch.nfclottery.DataHelper;
import it.imwatch.nfclottery.MainActivity;
import it.imwatch.nfclottery.R;
import it.imwatch.nfclottery.data.provider.NFCMLContent;

import static it.imwatch.nfclottery.Const.DEBUG;

/**
 * A dialog that shows the list of winners drawn so far in the database.
 */
public class WinnersListDialog extends DialogFragment {

    private static String TAG = WinnersListDialog.class.getSimpleName();

    private ViewSwitcher mSwitcher;
    private Cursor mWinnersCursor;
    private int mPendingRevokeId = -1;
    private SimpleCursorAdapter mWinnersAdapter;
    private View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final Activity activity = getActivity();
            if (activity == null) {
                Log.e(TAG, "Not attached to Activity: cannot handle clicks");
                return;
            }

            if (activity instanceof MainActivity) {
                final MainActivity a = (MainActivity) activity;

                a.post(new Runnable() {
                    @Override
                    public void run() {
                        a.drawWinner();
                    }
                });
            }
            else {
                Log.e(TAG, "The parent Activity is not MainActivity! Wat is this I don't even");
                if (DEBUG) Log.d(TAG, "Activity class: " + activity.getLocalClassName());
                Toast.makeText(activity, activity.getString(R.string.insert_failed_wrong_parent),
                               Toast.LENGTH_SHORT)
                     .show();
            }
            dismiss();
        }
    };

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        final Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Not attached to Activity: cannot build dialog");
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        mWinnersCursor = updateWinnersCursors();

        String[] inVal = new String[] {
            NFCMLContent.Geeks.Columns.NAME.getName(),
            NFCMLContent.Geeks.Columns.EMAIL.getName()
        };

        int[] outVal = new int[] {
            R.id.txt_name,
            R.id.txt_email
        };

        // Create the adapter and assign it to the list
        mWinnersAdapter = new SimpleCursorAdapter(getActivity(), R.layout.winners_row, mWinnersCursor,
                                                  inVal, outVal);
        mWinnersAdapter.setViewBinder(new ContactsViewBinder());

        LayoutInflater inflater = LayoutInflater.from(activity);
        final View rootView = inflater.inflate(R.layout.winners_dialog_layout, null);
        if (rootView == null) {
            Log.e(TAG, "Cannot inflate the dialog layout!");
            return null;
        }

        ListView listView = (ListView) rootView.findViewById(R.id.list_winners);

        listView.setAdapter(mWinnersAdapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showRevokeVictoryUi(position);
                return true;
            }
        });

        TextView emptyTxt = (TextView) rootView.findViewById(android.R.id.empty);
        emptyTxt.setOnClickListener(mClickListener);

        listView.setEmptyView(emptyTxt);
        builder.setView(rootView);

        mSwitcher = (ViewSwitcher) rootView.findViewById(R.id.switcher);

        Button btn_cancel = (Button) mSwitcher.findViewById(R.id.btn_cancel);
        btn_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                endRevokeVictory();
            }
        });

        Button btn_revoke = (Button) mSwitcher.findViewById(R.id.btn_revoke);
        btn_revoke.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                revokeVictory();
            }
        });

        Button btn_ok = (Button) mSwitcher.findViewById(android.R.id.button1);
        btn_ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Dialog thisDialog = WinnersListDialog.this.getDialog();
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
     * Queries the ContentProvider for winners and returns the
     * newly created Cursor.
     *
     * @return Returns the winners cursor, or null if there was any error
     */
    private Cursor updateWinnersCursors() {
        // Load the winners from the ContentProvider
        final Activity activity = getActivity();
        if (activity == null) {
            Log.e(TAG, "Not attached to Activity: cannot update winners cursor");
            return null;
        }

        ContentResolver cr = activity.getContentResolver();

        String[] projection = new String[] {
            NFCMLContent.Geeks.Columns.ID.getName(),
            NFCMLContent.Geeks.Columns.NAME.getName(),
            NFCMLContent.Geeks.Columns.EMAIL.getName()
        };

        return cr.query(NFCMLContent.Geeks.CONTENT_URI, projection,
                        NFCMLContent.Geeks.Columns.TIMEWINNER.getName() + "!=0", null,
                        NFCMLContent.Geeks.Columns.TIMEWINNER.getName() + " ASC"
        );
    }

    /**
     * Shows the UI that asks the user to confirm revoking the
     * victory for the specified position in the list.
     *
     * @param position The position in the list to show the UI for
     */
    private void showRevokeVictoryUi(int position) {
        if (mWinnersCursor.moveToPosition(position)) {
            final int nameColIndex = mWinnersCursor.getColumnIndex(NFCMLContent.Geeks.Columns.NAME.getName());
            final int idColIndex = mWinnersCursor.getColumnIndex(NFCMLContent.Geeks.Columns.ID.getName());

            String name = mWinnersCursor.getString(nameColIndex);
            TextView txt_prompt = (TextView) mSwitcher.findViewById(R.id.txt_prompt);
            txt_prompt.setText(getString(R.string.dialog_winners_delete_prompt, name));

            mPendingRevokeId = mWinnersCursor.getInt(idColIndex);

            mSwitcher.showNext();
        }
        else {
            final Activity activity = getActivity();
            if (activity != null) {
                Toast.makeText(activity, activity.getString(R.string.err_revoke_failed_toast), Toast.LENGTH_SHORT)
                     .show();
            }
            Log.e(TAG, "Unable to revoke victory for contact #" + position + ": unable to move Cursor");
        }
    }

    /**
     * Ends the "revoke victory" mode for a contact, hiding the
     * relative UI.
     */
    private void endRevokeVictory() {
        mSwitcher.showPrevious();
        mPendingRevokeId = -1;
    }

    /**
     * Revokes victory to the contact with the ID specified in the
     * {@link #mPendingRevokeId} field.
     */
    private void revokeVictory() {
        final Activity activity = getActivity();
        if (mPendingRevokeId < 0) {
            if (activity != null) {
                Toast.makeText(activity, activity.getString(R.string.error_revoke_no_id), Toast.LENGTH_SHORT)
                     .show();
            }
            Log.e(TAG, "Unable to revoke victory for contact #" + mPendingRevokeId + ": invalid ID");
            return;
        }

        int updatedCount = DataHelper.clearWinner(getActivity(), mPendingRevokeId);
        if (updatedCount > 0) {
            if (activity != null) {
                Toast.makeText(activity, activity.getString(R.string.victory_revoked), Toast.LENGTH_SHORT).show();
            }
            Log.i(TAG, "Revoked victory for contact #" + mPendingRevokeId);
        }
        else {
            if (activity != null) {
                Toast.makeText(activity, activity.getString(R.string.error_revoke_db_err), Toast.LENGTH_SHORT).show();
            }
            Log.e(TAG, "Unable to revoke victory for contact #" + mPendingRevokeId + ": 0 rows updated");
        }

        mWinnersCursor = updateWinnersCursors();
        mWinnersAdapter.changeCursor(mWinnersCursor);

        endRevokeVictory();
    }

    /**
     * A simple view binder that copes with packed values coming
     * from the database (only showing the first value for each field)
     */
    private class ContactsViewBinder implements SimpleCursorAdapter.ViewBinder {
        @Override
        public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
            if (view.getId() == R.id.txt_name || view.getId() == R.id.txt_email) {
                TextView textView = (TextView) view;

                // Remove all trailing separators
                String value = cursor.getString(columnIndex);
                if (value != null) {
                    int separatorPos = value.indexOf(DataHelper.VALUES_SEPARATOR, 1); // Skip the heading separator
                    if (separatorPos >= 0) {
                        value = value.substring(0, separatorPos);     // Only show the first value, trim separator
                    }
                    value = DataHelper.cleanupSeparators(value);
                }
                else {
                    if (DEBUG) {
                        Log.d(TAG, String.format("Value for position %d, column %d is null",
                                                 cursor.getPosition(), columnIndex));
                    }
                }

                textView.setText(value);
                return true;
            }
            return false;
        }
    }
}
