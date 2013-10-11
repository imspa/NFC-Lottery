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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import it.imwatch.nfclottery.data.provider.NFCMLContent;

import java.util.ArrayList;
import java.util.List;

import static it.imwatch.nfclottery.Const.DEBUG;

/**
 * An helper for the data-handling operations.
 */
public class DataHelper {

    private static final String TAG = DataHelper.class.getSimpleName();

    public static final String VALUES_SEPARATOR = "§";

    /**
     * Inserts the given contact into the local Content Provider.
     *
     * @param context       The current context
     * @param names         The names of the contact
     * @param emailsList    The email address of the contact to insert
     * @param organizations The organization of the contact to insert
     * @param titles        The title of the contact to insert
     */
    public static boolean insertContact(Context context, ArrayList<String> names, String emailsList,
                                        ArrayList<String> organizations, ArrayList<String> titles) {

        if (DEBUG) Log.v(TAG, "Preparing contact data for insertion");

        String namesString, orgStrings, titlesString;
        namesString = packList(names);
        orgStrings = packList(organizations);
        titlesString = packList(titles);

        // Remove all heading and trailing separators in the emails field (ensures there's only one at each end)
        emailsList = cleanupSeparators(emailsList);
        emailsList = VALUES_SEPARATOR + emailsList + VALUES_SEPARATOR;  // See isEmailAlreadyPresent for info

        if (DEBUG) {
            Log.v(TAG, String.format("Inserting contact into DB.\n\t> Name(s): %s\n\t> Email(s): %s\n" +
                                     "\t> Organization(s): %s\n\t> Title(s): %s",
                                     namesString, emailsList, orgStrings, titlesString));
        }

        ContentResolver cr = context.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(NFCMLContent.Geeks.Columns.EMAIL.getName(), emailsList);
        cv.put(NFCMLContent.Geeks.Columns.NAME.getName(), namesString);
        cv.put(NFCMLContent.Geeks.Columns.ORGANIZATION.getName(), orgStrings);
        cv.put(NFCMLContent.Geeks.Columns.TITLE.getName(), titlesString);
        cv.put(NFCMLContent.Geeks.Columns.TIMEWINNER.getName(), 0);

        final Uri rowUri = cr.insert(NFCMLContent.Geeks.CONTENT_URI, cv);
        boolean success = rowUri != null;
        if (success) {
            // Push the updated DB file to Dropbox
            DropboxHelper.pushDB(context);
        }

        if (DEBUG) Log.d(TAG, String.format("Contact added: %s (URI: %s)", success, rowUri));
        return success;
    }

    /**
     * Removes all heading and trailing separators ({@link #VALUES_SEPARATOR})
     * from a list of values.
     *
     * @param valuesList The list of values to cleanup
     *
     * @return Returns the cleaned up list
     */
    public static String cleanupSeparators(String valuesList) {
        while (valuesList.startsWith(VALUES_SEPARATOR)) {
            valuesList = valuesList.substring(1);
        }

        while (valuesList.endsWith(VALUES_SEPARATOR)) {
            valuesList = valuesList.substring(0, valuesList.length() - 1);
        }

        return valuesList;
    }

    /**
     * Packs a list of strings into a single string containing
     * all those strings, separated by {@link #VALUES_SEPARATOR}.
     * <p/>
     * It will then be splittable by using {@link String#split(String)}.
     * For example:
     * <code>String[] items = packedString.split(DataHelper.VALUES_SEPARATOR);</code>
     *
     * @param list The list of strings to pack
     *
     * @return Returns the packed string (empty if the list is empty, or null)
     */
    private static String packList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String item : list) {
            if (item != null && TextUtils.isGraphic(item)) {
                sb.append(item).append(VALUES_SEPARATOR);
            }
        }

        if (sb.length() == 0) {
            return "";
        }

        sb = sb.deleteCharAt(sb.length() - 1);  // Remove trailing separator

        return sb.toString();
    }

    /**
     * Checks if the given email has already been inserted into database.
     * This is used before inserting a new contact, in order to avoid duplicates.
     *
     * @param context The current context
     * @param email   The email to check for in the database
     *
     * @return Returns true if the given email is present, false otherwise
     */
    public static boolean isEmailAlreadyPresent(Context context, String email) {
        if (DEBUG) {
            Log.v(TAG, String.format("Checking for duplicates in the DB. Email: '%s'", email));
        }

        if (TextUtils.isEmpty(email)) {
            return false;
        }

        // Note that we surround each email with VALUES_SEPARATOR to avoid having
        // the 'LIKE' SQL operator find subsets of emails in the DB and incorrectly
        // report them as already used.
        // For example, if the database contains the address cba@example.com, and
        // we search for ba@example.com without the separators surrounding the query,
        // we would be told that ba@example.com is already in the database, when
        // it's clearly not. This is a problem with, say, Gmail, Yahoo! Mail, ...
        ContentResolver cr = context.getContentResolver();
        Cursor result = cr.query(
            NFCMLContent.Geeks.CONTENT_URI,
            NFCMLContent.Geeks.PROJECTION,
            NFCMLContent.Geeks.Columns.EMAIL.getName() + " LIKE ?",
            new String[] {"%" + VALUES_SEPARATOR + email + VALUES_SEPARATOR + "%"},
            null
        );

        boolean found = result != null && result.getCount() > 0;
        if (result != null) {
            result.close();
        }

        if (DEBUG) Log.d(TAG, String.format("Email '%s' already present in DB: %s", email, found));
        return found;
    }

    /**
     * Update the "iswinner" field for the contact with the given email in
     * the local Content Provider, if present.
     *
     * @param context     The current context
     * @param email       The email address of the winner to update
     * @param winnerValue The new "iswinner" value
     */
    public static boolean markWinner(Context context, String email, boolean winnerValue) {
        if (DEBUG) {
            Log.v(TAG, String.format("Updating 'iswinner' value. Email: %s, value: %s", email, winnerValue));
        }

        ContentResolver cr = context.getContentResolver();
        ContentValues cv = new ContentValues();
        // The winner has a non-zero timestamp (the current one); a non-winner has a zero timestamp
        cv.put(NFCMLContent.Geeks.Columns.TIMEWINNER.getName(), winnerValue ? System.currentTimeMillis() : 0);

        int updatedRecords = cr.update(NFCMLContent.Geeks.CONTENT_URI, cv,
                                       NFCMLContent.Geeks.Columns.EMAIL.getName() + " LIKE ?",
                                       new String[] {"%" + VALUES_SEPARATOR + email + VALUES_SEPARATOR + "%"});

        if (updatedRecords > 0) {
            // Push the updated DB file to Dropbox
            DropboxHelper.pushDB(context);

            if (updatedRecords > 1) {
                Log.w(TAG, updatedRecords + " row(s) updated in a markWinner operation. Something's fishy!");
            }
            else if (DEBUG) {
                Log.d(TAG, String.format("Winner status updated for email %s (isWinner: %s)", email, winnerValue));
            }
        }
        else {
            Log.w(TAG, "Unable to update winner status. Contact not in DB?");
        }

        return updatedRecords > 0;
    }

    /**
     * Update the "iswinner" field for the contact related to the given email into the local Content Provider
     *
     * @param context  The current context
     * @param winnerId The ID of the winner to clear
     *
     * @return Returns the count of rows the victory was cleared for.
     */
    public static int clearWinner(Context context, int winnerId) {
        if (DEBUG) Log.v(TAG, String.format("Clearing 'iswinner' value. WinnerID: %d", winnerId));

        ContentResolver cr = context.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(NFCMLContent.Geeks.Columns.TIMEWINNER.getName(), 0);

        int updatedRecords = cr.update(NFCMLContent.Geeks.CONTENT_URI, cv,
                                       NFCMLContent.Geeks.Columns.ID.getName() + "=?",
                                       new String[] {String.valueOf(winnerId)});

        if (updatedRecords > 0) {
            // Push the updated DB file to Dropbox
            DropboxHelper.pushDB(context);

            if (updatedRecords > 1) {
                Log.w(TAG, updatedRecords + " row(s) updated in a clearWinner operation. Something's fishy!");
            }
            else if (DEBUG) {
                Log.d(TAG, "Winner status cleared for ID " + winnerId);
            }
        }
        else {
            Log.w(TAG, "Unable to update winner status for ID " + winnerId + ". Contact not in DB?");
        }

        return updatedRecords;
    }

    /**
     * Clears the "iswinner" field for all contacts in the database.
     *
     * @param context The current context
     *
     * @return Returns the count of rows the victory was cleared for
     */
    public static int clearAllWinners(Context context) {
        if (DEBUG) Log.v(TAG, String.format("Clearing 'iswinner' value for all contacts"));

        ContentResolver cr = context.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(NFCMLContent.Geeks.Columns.TIMEWINNER.getName(), 0);

        int updatedRecords = cr.update(NFCMLContent.Geeks.CONTENT_URI, cv, null, null);

        if (DEBUG) Log.d(TAG, "Cleared victory flag for all winners; updated " + updatedRecords + " record(s)");
        return updatedRecords;
    }
}
