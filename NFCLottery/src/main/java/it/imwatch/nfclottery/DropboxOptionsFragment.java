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

package it.imwatch.nfclottery;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;
import com.dropbox.sync.android.DbxAccountManager;
import de.keyboardsurfer.android.widget.crouton.Style;

/**
 * A fragment that provides the option menu items for the Dropbox interaction.
 */
public class DropboxOptionsFragment extends Fragment {

    private static final String TAG = DropboxOptionsFragment.class.getSimpleName();

    /** Dropbox account manager */
    private static final int REQUEST_LINK_TO_DBX = 0;
    private MenuItem mActionDropboxMenuItem;
    private MenuItem mActionDropboxRestoreMenuItem;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_dropbox, menu);

        mActionDropboxMenuItem = menu.findItem(R.id.action_dropbox);
        mActionDropboxRestoreMenuItem = menu.findItem(R.id.action_dropbox_restore);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        DbxAccountManager dbxAcctMgr = DropboxHelper.getAccountManager(getActivity());
        if (dbxAcctMgr.hasLinkedAccount()) {
            mActionDropboxMenuItem.setTitle(R.string.action_dropbox_unlink);
            mActionDropboxRestoreMenuItem.setVisible(true);
        }
        else {
            mActionDropboxMenuItem.setTitle(R.string.action_dropbox_link);
            mActionDropboxRestoreMenuItem.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_dropbox:
                toggleDropboxLink();
                return true;

            case R.id.action_dropbox_restore:
                // If attached to an Activity that listens for Dropbox callbacks,
                // route callbacks to it; otherwise, there will be no listener
                DropboxHelper.pullDB(getActivity(),
                                     getActivity() instanceof DropboxHelper.DropboxOperationsListener ?
                                     (DropboxHelper.DropboxOperationsListener) getActivity() :
                                     null);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** Ask the user to link/unlink his Dropbox account */
    private void toggleDropboxLink() {
        final FragmentActivity activity = getActivity();
        if (activity != null) {
            DbxAccountManager dbxAcctMgr = DropboxHelper.getAccountManager(activity);
            if (dbxAcctMgr == null) {
                showFeedback(activity, getString(R.string.dropbox_unavailable), Style.ALERT);
                return;
            }

            if (!dbxAcctMgr.hasLinkedAccount()) {
                dbxAcctMgr.startLink(activity, REQUEST_LINK_TO_DBX);
            }
            else {
                dbxAcctMgr.unlink();
                // The options menu has changed after unlinking from Dropbox
                activity.supportInvalidateOptionsMenu();
            }
        }
        else {
            Log.e(TAG, "Not attached to an Activity; cannot toggle Dropbox link");
        }
    }

    /**
     * Shows a message to the user, preferably in a Crouton.
     *
     * @param activity The Activity to show the message on
     * @param message  The message to show
     * @param style    The style the message has to be shown in
     */
    private void showFeedback(Activity activity, String message, Style style) {
        if (activity instanceof MainActivity) {
            final MainActivity mainActivity = (MainActivity) activity;
            mainActivity.showCroutonNao(message, style);
        }
        else if (activity != null) {
            // Fallback to Toasts if the Activity is not the main Activity
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        }
        else {
            Log.e(TAG, "Unable to show a message. The activity is null");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        final FragmentActivity activity = getActivity();

        if (activity != null) {
            if (requestCode == REQUEST_LINK_TO_DBX) {
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "Dropbox account successfully linked");
                    showFeedback(activity, "Dropbox account successfully linked", Style.CONFIRM);
                    mActionDropboxMenuItem.setTitle(R.string.action_dropbox_unlink);
                }
                else {
                    Log.e(TAG, "Link failed or cancelled by the user");
                }
            }
            else {
                super.onActivityResult(requestCode, resultCode, data);
            }
            activity.supportInvalidateOptionsMenu();
        }
        else {
            Log.e(TAG, String.format("Not attached to an Activity: unable to handle an Activity result " +
                                     "(request %d, result %d)", requestCode, resultCode));
        }
    }
}
