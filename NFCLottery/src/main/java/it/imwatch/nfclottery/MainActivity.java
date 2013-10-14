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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
import it.imwatch.nfclottery.data.provider.NFCMLContent;
import it.imwatch.nfclottery.dialogs.ClearDBAlertDialog;
import it.imwatch.nfclottery.dialogs.ClearWinnersDialog;
import it.imwatch.nfclottery.dialogs.InsertContactDialog;
import it.imwatch.nfclottery.dialogs.WinnersListDialog;
import net.sourceforge.cardme.engine.VCardEngine;
import net.sourceforge.cardme.vcard.VCard;
import net.sourceforge.cardme.vcard.features.EmailFeature;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import static it.imwatch.nfclottery.Const.DEBUG;

/**
 * The main Activity for the app, that handles all the NFC events
 * and shows feedbacks to the users.
 */
public class MainActivity extends ActionBarActivity implements DropboxHelper.DropboxOperationsListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String MIME_VCARD = "text/vcard";
    private static final String MIME_XVCARD = "text/x-vcard";
    private static final String EXTRA_NO_NFC_CHECK_ERR = "no_nfc_check_err";

    private TextView mTxtStatus, mTxtNfcScan;
    private NfcAdapter mNfcAdapter;         // Late-initialized within isNfcAvailable()
    private VCardEngine mVCardEngine;

    private boolean mHasRefreshUi;

    private Handler mHandler;

    // NFC checker interval (ms)
    private static final int NFC_CHECK_INTERVAL = 1000;

    private Thread mNfcCheckerThread;
    private final Runnable mNfcChecker = new Runnable() {
        private boolean mLastNfcEnabled = false;

        @Override
        public void run() {
            try {
                // This keeps running until stopped. Its task is to detect
                // changes in the NFC status while the Activity is showing,
                // because Android doesn't have any broadcast for those events
                while (true) {
                    if (Thread.interrupted()) {
                        return;
                    }

                    if (mNfcAdapter != null) {
                        boolean nfcEnabled = mNfcAdapter.isEnabled();

                        if (nfcEnabled != mLastNfcEnabled) {
                            mHandler.postAtFrontOfQueue(new Runnable() {
                                @Override
                                public void run() {
                                    updateNfcUi();
                                }
                            });
                        }
                        mLastNfcEnabled = nfcEnabled;
                    }

                    Thread.sleep(NFC_CHECK_INTERVAL);
                }
            }
            catch (InterruptedException e) {
                if (DEBUG) Log.d("NFC_checker", "Checker execution interrupted");
            }
        }
    };

    private Crouton mCurrentCrouton;

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @SuppressLint("ResourceAsColor")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Initialize the progress bar on the actionbar
        // Must be done BEFORE setContentView, then hidden
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mTxtStatus = (TextView) findViewById(R.id.txt_status);
        mTxtNfcScan = (TextView) findViewById(R.id.txt_nfc_scan);

        getSupportActionBar().setIcon(getResources().getDrawable(R.drawable.ic_actionbar_logo));

        mHandler = new Handler();

        // Only show the error toast (when necessary) if the saved instance state
        // doesn't tell us not to show it
        final boolean showUi = savedInstanceState == null ||
                               !savedInstanceState.getBoolean(EXTRA_NO_NFC_CHECK_ERR, false);

        if (isNfcAvailable(showUi)) {
            mTxtNfcScan.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(new Intent(android.provider.Settings.ACTION_NFC_SETTINGS));
                }
            });
        }
        else {
            // isNfcAvailable() shows an error Toast by itself in case of need
            Log.w(TAG, "This device doesn't support NFC");
        }

        updateParticipantsCount();
        setShowsRefreshUi(false);

        addDropboxMenuFragment();

        handleIntent(getIntent());
    }

    /**
     * Updates the NFC-related UI elements to reflect the status of the NFC chip.
     */
    private void updateNfcUi() {
        if (mNfcAdapter != null) {
            if (!mNfcAdapter.isEnabled()) {
                mTxtNfcScan.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.nfc_off, 0, 0);
                mTxtNfcScan.setText(R.string.hint_nfc_off);
                mTxtNfcScan.setClickable(true);
                showCroutonNao(getString(R.string.error_nfc_off), Style.INFO);
            }
            else {
                mTxtNfcScan.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.nfc_scan, 0, 0);
                mTxtNfcScan.setText(R.string.hint_nfc_scan);
                mTxtNfcScan.setClickable(false);
            }
        }
        else {
            mTxtNfcScan.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.nfc_off, 0, 0);
            mTxtNfcScan.setText(R.string.hint_nfc_not_available);
            mTxtNfcScan.setClickable(false);
        }
    }

    /**
     * Determines if the NFC adapter is available on the device.
     *
     * @return Returns true if the NFC adapter is available, false otherwise
     */
    private boolean isNfcAvailable() {
        return isNfcAvailable(true);
    }

    /**
     * Determines if the NFC adapter is available on the device.
     *
     * @param showUi True to show some feedback to the user when needed, false
     *               to avoid showing feedbacks
     *
     * @return Returns true if the NFC adapter is available, false otherwise
     */
    private boolean isNfcAvailable(boolean showUi) {
        if (mNfcAdapter == null) {
            // Try to retrieve the default NFC adapter (might not have been initialized yet)
            mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        }

        if (mNfcAdapter == null) {
            // This means the device doesn't have NFC, and we don't support this: finish here
            if (showUi) {
                Toast.makeText(this, getString(R.string.error_nfc_not_available), Toast.LENGTH_LONG)
                     .show();
            }
            return false;
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // It's important that the Activity is in the foreground (resumed) when we
        // setup the dispatching. If it's not, this will throw an IllegalStateException.
        setupForegroundDispatch(this, mNfcAdapter);

        updateNfcUi();

        // Start checking the NFC status periodically
        mNfcCheckerThread = new Thread(mNfcChecker);
        mNfcCheckerThread.setName("NfcChecker");
        mNfcCheckerThread.setPriority(Thread.MIN_PRIORITY);
        mNfcCheckerThread.start();
    }

    @Override
    protected void onPause() {
        // It's important that the Activity is still in the foreground (resumed) when we
        // stop the dispatching. If it's not, this will throw an IllegalStateException.
        stopForegroundDispatch(this, mNfcAdapter);

        try {
            mNfcCheckerThread.interrupt();
            mNfcCheckerThread.join();
        }
        catch (InterruptedException e) {
            Log.w(TAG, "Thread stopping join interrupted.");
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Crouton.cancelAllCroutons();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // This method gets called when the user scans a Tag with the device
        handleIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_NO_NFC_CHECK_ERR, true);
    }

    /**
     * Handles an intent as received by the Activity, be it with a MAIN or an
     * NDEF_DISCOVERED action.
     *
     * @param intent The intent to handle
     */
    private void handleIntent(Intent intent) {
        String action = intent.getAction();

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            // This is an "NFC tag scanned" intent
            String type = intent.getType();
            Log.d(TAG, "Read tag with type: " + type);

            if (MIME_VCARD.equals(type) || MIME_XVCARD.equals(type)) {
                if (mVCardEngine == null) {
                    mVCardEngine = new VCardEngine();
                }

                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                if (tag != null) {
                    Ndef ndefTag = Ndef.get(tag);
                    NdefMessage ndefMessage;

                    // ndefTag can be null if the tag was INITIALIZED
                    // but not actually written to
                    if (ndefTag != null) {
                        ndefMessage = ndefTag.getCachedNdefMessage();
                        parseAndInsertVCard(ndefMessage);
                    }
                }
            }
            else {
                Log.i(TAG, "Ignoring NDEF with mime type: " + type);
            }
        }

        // Nothing else to do if this is a MAIN intent...
    }

    /**
     * Parses a discovered NDEF vCard message and stores it
     * in the app's ContentProvider. If the card contains more than one
     * for each detail type (name, organization, title, email) then they
     * are merged when inserting them in the database in a list string
     * for each type. Values in these lists are separated by  Only the first non-empty value for each type of
     * field will be shown on the UI, anyway.
     *
     * @param ndefMessage The NDEF message to parse and store data from
     */
    private void parseAndInsertVCard(NdefMessage ndefMessage) {
        NdefRecord[] records = ndefMessage.getRecords();
        ArrayList<String> names = new ArrayList<String>();
        ArrayList<String> emails = new ArrayList<String>();
        ArrayList<String> organizations = new ArrayList<String>();
        ArrayList<String> titles = new ArrayList<String>();

        // Tags might contain more than one record, each of which might contain
        // one or more of the elements (or no record, if malformed...)!
        for (NdefRecord record : records) {
            String vCardString = new String(record.getPayload());

            try {
                readDetailsFromvCard(mVCardEngine, vCardString, names, emails,
                                     organizations, titles);
            }
            catch (IOException e) {
                Log.e(TAG, "Error while parsing a vCard!", e);
                if (DEBUG) Log.v(TAG, "Malformed vCard contents:\n" + vCardString);
            }
        }

        if (emails.isEmpty()) {
            // It's mandatory to have at least one email address
            showCroutonNao(getString(R.string.error_email_not_present), Style.ALERT);
            Log.e(TAG, "The tag doesn't contain any email address");
            return;
        }

        // Now check and consolidate all emails in a CSV list
        // (to avoid looping though this list all over again later on)
        StringBuilder emailCsv = new StringBuilder();
        for (String email : emails) {
            if (DataHelper.isEmailAlreadyPresent(this, email)) {
                // we add only once every email
                showCroutonNao(getString(R.string.error_email_already_present, email), Style.ALERT);
                return;
            }

            emailCsv.append(email).append(DataHelper.VALUES_SEPARATOR);
        }

        if (DataHelper.insertContact(this, names, emailCsv.toString(), organizations, titles)) {
            showCroutonNao(getString(R.string.new_contact_added, emails.get(0)), Style.CONFIRM);
            updateParticipantsCount();
        }
    }

    /**
     * Parses the details from the specified vCard string and adds them to the right
     * details lists.
     *
     * @param vCardEngine   The vCard parsing engine to use
     * @param vCardString   The vCard contents as a string
     * @param names         The "formatted names" list
     * @param emails        The "email addresses" list
     * @param organizations The "organization names" list
     * @param titles        The "titles" list
     *
     * @throws IOException Thrown if the vCard string is malformed
     */
    private static void readDetailsFromvCard(VCardEngine vCardEngine, String vCardString, ArrayList<String> names,
                                             ArrayList<String> emails, ArrayList<String> organizations,
                                             ArrayList<String> titles) throws IOException {

        String tmp;
        VCard vCard = vCardEngine.parse(vCardString);

        // Read the formatted name and title from the vCard first
        if (vCard.getFormattedName() != null) {
            tmp = vCard.getFormattedName().getFormattedName();
            names.add(tmp);
            if (DEBUG) Log.v(TAG, "Tag formatted name: " + tmp);
        }

        if (vCard.getTitle() != null) {
            tmp = vCard.getTitle().getTitle();
            titles.add(tmp);
            if (DEBUG) Log.v(TAG, "Tag title: " + tmp);
        }

        // Read the emails and organizations (there can be more than one per vCard)
        if (vCard.getEmails() != null) {
            final Iterator<EmailFeature> emailIterator = vCard.getEmails();
            while (emailIterator.hasNext()) {
                tmp = emailIterator.next().getEmail();
                emails.add(tmp);
                if (DEBUG) Log.v(TAG, "Tag email: " + tmp);
            }
        }

        if (vCard.getOrganizations() != null && vCard.getOrganizations().getOrganizations() != null) {
            final Iterator<String> orgsIterator = vCard.getOrganizations().getOrganizations();
            while (orgsIterator.hasNext()) {
                tmp = orgsIterator.next();
                organizations.add(tmp);
                if (DEBUG) Log.v(TAG, "Tag organization: " + tmp);
            }
        }
    }

    /**
     * Sets up the dispatching of NFC events a the foreground Activity.
     *
     * @param activity The {@link android.app.Activity} to setup the foreground dispatch for
     * @param adapter  The {@link android.nfc.NfcAdapter} to setup the foreground dispatch for
     */
    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        if (adapter != null) {
            final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

            final PendingIntent pendingIntent =
                PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);
            IntentFilter[] filters = new IntentFilter[1];
            String[][] techList = new String[][] {};

            // Notice that this is the same filter we define in our manifest.
            // This ensures we're not stealing events for other types of NDEF.
            filters[0] = new IntentFilter();
            IntentFilter filter = filters[0];
            filter.addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            try {
                filter.addDataType(MIME_VCARD);
                filter.addDataType(MIME_XVCARD);
            }
            catch (IntentFilter.MalformedMimeTypeException e) {
                throw new RuntimeException("Mime type not supported.");
            }

            adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
        }
    }

    /**
     * Stops the dispatching of NFC events for a foreground Activity.
     *
     * @param activity The {@link android.app.Activity} to stop the foreground dispatch for
     * @param adapter  The {@link android.nfc.NfcAdapter} to stop the foreground dispatch for
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        if (adapter != null) {
            adapter.disableForegroundDispatch(activity);
        }
    }

    /** Adds the fragment including the Dropbox menu options */
    protected void addDropboxMenuFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();

        Fragment dropboxOptionsFrag =
            fragmentManager.findFragmentByTag(DropboxOptionsFragment.class.getSimpleName());

        if (dropboxOptionsFrag == null) {
            // Add an instance of the Fragment
            Log.d(TAG, "Fragment doesn't exist, adding it.");
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            dropboxOptionsFrag = new DropboxOptionsFragment();
            transaction.add(dropboxOptionsFrag, DropboxOptionsFragment.class.getSimpleName());
            transaction.commitAllowingStateLoss();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_insert:
                showManualInsertNewContactDialog();
                return true;

            case R.id.action_draw:
                drawWinner();
                return true;

            case R.id.action_winners:
                showWinners();
                return true;

            case R.id.action_export:
                exportDB();
                return true;

            case R.id.action_clear_db:
                showClearDbConfirmation();
                return true;

            case R.id.action_clear_winners:
                showClearWinnersConfirmation();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Shows a dialog that asks the user the confirmation to clear the
     * winners' status from the database.
     */
    private void showClearWinnersConfirmation() {
        DialogFragment newFragment = new ClearWinnersDialog();
        newFragment.show(getSupportFragmentManager(), "clearwinnersdialog");
    }

    /**
     * Draws a winner from the stored contacts in the ContentProvider
     * that haven't already won.
     */
    public void drawWinner() {
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(NFCMLContent.Geeks.CONTENT_URI, NFCMLContent.Geeks.PROJECTION,
                            NFCMLContent.Geeks.Columns.TIMEWINNER.getName() + "=0",
                            null, null);

        int count = c != null ? c.getCount() : 0;
        if (count == 0) {
            // There is nobody to draw from
            if (c != null) {
                c.close();
            }

            showCroutonNao(getString(R.string.no_more_nonwinning_contacts), Style.ALERT);
            return;
        }

        int winnerPosition = (new Random()).nextInt(count);
        c.moveToPosition(winnerPosition);

        String winnerEmail = c.getString(NFCMLContent.Geeks.Columns.EMAIL.getIndex());
        String winnerName = c.getString(NFCMLContent.Geeks.Columns.NAME.getIndex());

        if (DEBUG) {
            Log.d(TAG, String.format("The winner is %s (position: %d)",
                                     DataHelper.cleanupSeparators(winnerEmail), c.getPosition()));
        }
        c.close();

        showWinner(winnerPosition, winnerEmail, winnerName);
    }

    /**
     * Shows the winner with the specified email and name,
     * after validating that data.
     *
     * @param winnerPosition The position in the drawing cursor of the winner
     * @param winnerEmail    The email (can be a list) of the winner
     * @param winnerName     The name (can be a list) of the winner
     */
    private boolean showWinner(int winnerPosition, String winnerEmail, String winnerName) {
        if (winnerEmail == null) {
            showCroutonNao(getString(R.string.error_cant_draw), Style.ALERT);
            Log.e(TAG, "Unable to retrieve the contact email for position " + winnerPosition);
            return false;
        }
        int separatorPos = winnerEmail.indexOf(DataHelper.VALUES_SEPARATOR, 1); // Skip the heading separator
        if (separatorPos >= 0) {
            winnerEmail = winnerEmail.substring(0, separatorPos);     // Only show the first email
        }
        winnerEmail = DataHelper.cleanupSeparators(winnerEmail);

        if (winnerName == null) {
            showCroutonNao(getString(R.string.error_cant_draw), Style.ALERT);
            Log.e(TAG, "Unable to retrieve the contact name for position " + winnerPosition);
            return false;
        }
        separatorPos = winnerName.indexOf(DataHelper.VALUES_SEPARATOR, 1); // Skip the heading separator
        if (separatorPos >= 0) {
            winnerName = winnerName.substring(0, separatorPos);     // Only show the first name
        }
        winnerName = DataHelper.cleanupSeparators(winnerName);

        // Store that this contact has won
        if (!DataHelper.markWinner(this, winnerEmail, true)) {
            showCroutonNao(getString(R.string.error_cant_draw), Style.ALERT);
            Log.e(TAG, "Unable to mark the winner at position " + winnerPosition);
            return false;
        }

        // Open the WinnerActivity to show who's won
        Intent winnerActivityIntent = new Intent(this, WinnerActivity.class);
        winnerActivityIntent.putExtra(WinnerActivity.EXTRA_WINNER_NAME, winnerName);
        winnerActivityIntent.putExtra(WinnerActivity.EXTRA_WINNER_EMAIL, winnerEmail);
        startActivity(winnerActivityIntent);

        return true;
    }

    /**
     * Shows a dialog containing a list of everybody that has won so far.
     */
    private void showWinners() {
        DialogFragment newFragment = new WinnersListDialog();
        newFragment.show(getSupportFragmentManager(), "winnersDialog");
    }

    /**
     * Exports the database to a CSV file.
     */
    private void exportDB() {
        new ExportDbAsyncTask(this).execute();
    }

    /** Shows a dialog to let the user to manually insert a contact into DB */
    private void showManualInsertNewContactDialog() {
        DialogFragment newFragment = new InsertContactDialog();
        newFragment.show(getSupportFragmentManager(), "insertdialog");
    }

    /** Shows the alert dialog asking the user the confirmation to delete all the contacts from DB */
    private void showClearDbConfirmation() {
        DialogFragment newFragment = new ClearDBAlertDialog();
        newFragment.show(getSupportFragmentManager(), "cleardialog");
    }

    /**
     * Utility method to quickly swap an already showing crouton with a new one,
     * whitout waiting for the default timeout.
     *
     * @param text  The text to display in the new crouton
     * @param style the new crouton style
     */
    public void showCroutonNao(CharSequence text, Style style) {
        if (mCurrentCrouton != null) {
            mCurrentCrouton.cancel();
        }

        mCurrentCrouton = Crouton.makeText(MainActivity.this, text, style);
        mCurrentCrouton.show();
    }

    /** Updates the number of participants to the lottery on the UI */
    public void updateParticipantsCount() {
        ContentResolver cr = getContentResolver();
        Cursor result = cr.query(
            NFCMLContent.Geeks.CONTENT_URI,
            null,
            null,
            null,
            null
        );

        int count = result != null ? result.getCount() : 0;
        if (result != null) {
            result.close();
        }

        if (count > 0) {
            mTxtStatus.setText(getResources().getQuantityString(R.plurals.label_participants_count, count, count));
        }
        else {
            mTxtStatus.setText(getString(R.string.label_participants_empty));
        }
    }

    @Override
    public void onStartRestoreOperation() {
        Log.d(TAG, "Starting restore. Showing indeterminate progress...");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setShowsRefreshUi(true);
            }
        });
    }

    @Override
    public void onRemoteDBRestored(final boolean success) {
        Log.d(TAG, "Restore ended with status " + success + ". Hiding indeterminate progress");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (success) {
                    showCroutonNao(getString(R.string.confirm_dropbox_restore), Style.CONFIRM);
                }
                else {
                    showCroutonNao(getString(R.string.dropbox_restore_failed), Style.ALERT);
                }
                setShowsRefreshUi(false);
                updateParticipantsCount();
            }
        });
    }

    /**
     * Sets wether this Activity shows the refresh UI (progressbar).
     *
     * @param hasRefreshUi True to show the Refresh UI, false to hide it.
     */
    protected void setShowsRefreshUi(boolean hasRefreshUi) {
        if (mHasRefreshUi != hasRefreshUi) {
            mHasRefreshUi = hasRefreshUi;

            setProgressBarIndeterminateVisibility(mHasRefreshUi);
            supportInvalidateOptionsMenu();
        }
    }

    /**
     * Posts a Runnable on the Activity's Handler.
     *
     * @param runnable The Runnable to post on the Handler
     */
    public void post(Runnable runnable) {
        mHandler.post(runnable);
    }

    private class ExportDbAsyncTask extends AsyncTask<Void, Void, String> {

        WeakReference<MainActivity> mActivity;

        private ExportDbAsyncTask(MainActivity activity) {
            mActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        protected String doInBackground(Void... params) {
            // Just launch the operation in the worker thread and dispatch the result
            // to the UI thread, where it will be handled
            final MainActivity mainActivity = mActivity.get();

            if (DEBUG) Log.d(TAG, "ExportDbAsyncTask is starting its background job");

            if (mainActivity != null) {
                return DBExporter.exportDB(mainActivity);
            }
            else {
                Log.e(TAG, "The Activity was destroyed before the AsyncTask begun working");
                return null;
            }
        }

        @Override
        protected void onPostExecute(String outputFilePath) {
            if (DEBUG) Log.d(TAG, "ExportDbAsyncTask has completed its background job");
            super.onPostExecute(outputFilePath);

            final MainActivity mainActivity = mActivity.get();
            if (mainActivity != null) {
                if (outputFilePath != null) {
                    mainActivity.showCroutonNao(getString(R.string.success_db_export, outputFilePath), Style.CONFIRM);
                }
                else {
                    mainActivity.showCroutonNao(getString(R.string.error_db_export), Style.ALERT);
                }
            }
            else {
                Log.w(TAG, "The Activity was destroyed while the AsyncTask was working");
            }
        }
    }
}
