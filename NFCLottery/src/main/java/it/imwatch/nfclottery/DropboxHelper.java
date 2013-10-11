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

import android.content.Context;
import android.util.Log;
import com.dropbox.sync.android.*;
import it.imwatch.nfclottery.data.provider.NFCMLProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static it.imwatch.nfclottery.Const.DEBUG;

/**
 * An helper class to perform Dropbox syncing operations.
 */
public class DropboxHelper {

    private static final String TAG = DropboxHelper.class.getSimpleName();

    // TODO: define the ApiKeys.java file with DROPBOX_APP_KEY and DROPBOX_APP_SECRET to use Dropbox

    private static final String DB_SYNC_FILE = "db_file.db";
    private static final String LOCAL_DB_FILE = "../databases/" + NFCMLProvider.DATABASE_NAME;

    /**
     * Pulls the database from Dropbox (restores a backup).
     * The main operations are performed on a background thread.
     *
     * @param context  The current context
     * @param listener The Dropbox operations listener for this operation
     */
    public static void pullDB(final Context context, final DropboxOperationsListener listener) {
        if (DEBUG) Log.d(TAG, "Beginning a DB pull operation");

        if (context == null) {
            Log.e(TAG, "pullDB called with a null context");
            return;
        }

        // First of all, check the Dropbox link status
        final DbxAccountManager dbxAcctMgr = getAccountManager(context);
        if (dbxAcctMgr == null || !dbxAcctMgr.hasLinkedAccount()) {
            Log.e(TAG, "Dropbox user not authenticated. Can't pull!");
            return;
        }

        // Do the actual pulling in a worker thread to avoid blocking the UI
        Thread t = new Thread(new PullRunnable(listener, dbxAcctMgr, context));
        t.setName("DropboxPuller");
        t.setPriority(Thread.MIN_PRIORITY);

        t.start();
    }

    /**
     * Pushes the local database to Dropbox (backs it up).
     * The operations aren't performed on a background thread, unlike
     * what {@link #pullDB(android.content.Context,
     * it.imwatch.nfclottery.DropboxHelper.DropboxOperationsListener)}.
     *
     * @param context The current context
     */
    public static void pushDB(Context context) {
        if (DEBUG) Log.d(TAG, "Beginning a DB pull operation");

        if (context == null) {
            Log.e(TAG, "pushDB called with a null context");
            return;
        }

        // First of all, check the Dropbox link status
        final DbxAccountManager dbxAcctMgr = getAccountManager(context);
        if (dbxAcctMgr == null || !dbxAcctMgr.hasLinkedAccount()) {
            Log.e(TAG, "Dropbox user not authenticated. Can't push!");
            return;
        }

        // Retrieve the local DB file
        File localDBFile = getLocalDbFile(context);
        if (localDBFile == null) {
            Log.e(TAG, "Cannot retrieve local DB file. Can't push.");
            return;
        }

        try {
            if (DEBUG) Log.d(TAG, "Starting DB push...");
            uploadDbToDropbox(dbxAcctMgr, localDBFile);
        }
        catch (DbxException e) {
            Log.w(TAG, "Dropbox exception while pushing database", e);
        }
        catch (IOException e) {
            Log.w(TAG, "I/O exception while pushing database", e);
        }
    }

    /**
     * Uploads the specified database file to Dropbox.
     *
     * @param dbxAcctMgr  The Dropbox Account Manager used to access the Dropbox Filesystem
     * @param localDBFile The local database file to upload
     *
     * @throws IOException Thrown when an I/O error occours in the local cache or the given file
     */
    private static void uploadDbToDropbox(DbxAccountManager dbxAcctMgr, File localDBFile) throws IOException {
        DbxFileSystem dbxFs = DbxFileSystem.forAccount(dbxAcctMgr.getLinkedAccount());
        DbxPath remotePath = new DbxPath(DB_SYNC_FILE);
        DbxFile remoteFile;

        if (dbxFs.exists(remotePath)) {
            remoteFile = dbxFs.open(remotePath);
        }
        else {
            remoteFile = dbxFs.create(remotePath);
        }

        // Write to the Dropbox remote file
        remoteFile.writeFromExistingFile(localDBFile, false);
        remoteFile.close();
        Log.i(TAG, "Database pushed to Dropbox");
    }

    /**
     * Retrieves the local DB File, if it exists.
     *
     * @param context The current context
     *
     * @return Returns the local DB File instance, or null if
     * it doesn't exist or it can't be found
     */
    private static File getLocalDbFile(Context context) {
        final File filesDir = context.getFilesDir();
        if (filesDir == null) {
            Log.w(TAG, "Can't retrieve the app's files directory");
            return null;
        }

        File localDBFile = new File(filesDir.getPath(), LOCAL_DB_FILE);
        if (!localDBFile.exists()) {
            Log.w(TAG, "Local DB not yet created.");
            return null;
        }

        return localDBFile;
    }

    /**
     * Replaces the local database file with the specified file from Dropbox.
     *
     * @param context    The current context
     * @param remoteFile The Dropbox copy of the database file. Will be closed when the
     *                   replace operation is completed!
     * @param listener   The Dropbox operations listener for this operation
     *
     * @return Returns true if the operation has been successful, false otherwise
     */
    private static boolean replaceDbFile(Context context, DbxFile remoteFile, DropboxOperationsListener listener) {
        if (DEBUG) Log.d(TAG, "Replacing the DB file with Dropbox file: " + remoteFile.getPath());

        if (context == null) {
            Log.e(TAG, "replaceDbFile called with a null context");
            return false;
        }

        if (listener != null) {
            listener.onStartRestoreOperation();
        }

        boolean replaceSuccess = false;
        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            final File filesDir = context.getFilesDir();
            if (filesDir == null) {
                Log.e(TAG, "Can't retrieve the app's files directory");
                return false;
            }

            String db_path = context.getFilesDir().getPath();
            if (!db_path.endsWith("/")) db_path += "/";

            File localFile = new File(db_path + LOCAL_DB_FILE);
            inputStream = remoteFile.getReadStream();
            outputStream = new FileOutputStream(localFile);

            int read;
            byte[] bytes = new byte[1024];

            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
            replaceSuccess = true;
        }
        catch (IOException e) {
            Log.e(TAG, "Unable to replace DB file", e);
        }
        finally {
            remoteFile.close();

            try {
                inputStream.close();
            }
            catch (Throwable ignored) { }
            try {
                outputStream.close();
            }
            catch (Throwable ignored) { }
        }

        return replaceSuccess;
    }

    /**
     * Gets the instance of the Dropbox Account Manager associated with this
     * application's context.
     * <p/>
     * <b>Note:</b> always check if there is a linked account by calling the
     * {@link com.dropbox.sync.android.DbxAccountManager#hasLinkedAccount()}
     * method, before actually using Dropbox APIs.</code>
     *
     * @param context The current context
     *
     * @return Returns the correct Dropbox Account Manager instance
     */
    public static DbxAccountManager getAccountManager(Context context) {
        return DbxAccountManager.getInstance(context.getApplicationContext(),
                                             ApiKeys.DROPBOX_APP_KEY,
                                             ApiKeys.DROPBOX_APP_SECRET);
    }

    /**
     * Simple listener for Dropbox operations callbacks.
     */
    public interface DropboxOperationsListener {

        /**
         * Called when a restore operation from Dropbox begins.
         */
        public void onStartRestoreOperation();

        /**
         * Called when a restore operation from Dropbox is completed.
         *
         * @param success True if the operation was successfully completed,
         *                false otherwise.
         */
        public void onRemoteDBRestored(boolean success);
    }

    /**
     * A Runnable that pulls the database from Dropbox.
     */
    private static class PullRunnable implements Runnable {

        private DropboxOperationsListener mListener;
        private DbxAccountManager mAccountMgr;
        private Context mContext;

        public PullRunnable(DropboxOperationsListener listener, DbxAccountManager accountMgr,
                            Context context) {

            mListener = listener;
            mAccountMgr = accountMgr;
            mContext = context;
        }

        @Override
        public void run() {
            Log.d(TAG, "Starting DB pull thread" + (mListener != null ? " with a listener" : ""));

            try {
                DbxFileSystem dbxFs = DbxFileSystem.forAccount(mAccountMgr.getLinkedAccount());
                DbxPath remotePath = new DbxPath(DB_SYNC_FILE);

                if (dbxFs.exists(remotePath)) {
                    updateDropboxRemoteFile(dbxFs, remotePath);
                }
                else {
                    Log.i(TAG, "DB file not yet uploaded to Dropbox. Nothing to pull.");
                    if (mListener != null) mListener.onRemoteDBRestored(false);
                }
            }
            catch (DbxException e) {
                Log.e(TAG, "Error while pulling the DB from Dropbox", e);
                if (mListener != null) mListener.onRemoteDBRestored(false);
            }
        }

        /**
         * Updates the local (cached) version of a remote file in Dropbox.
         *
         * @param dbxFs      The Dropbox filesystem to use
         * @param remotePath The path of the remote file
         *
         * @throws DbxException Thrown in case of problems during the operation
         */
        private void updateDropboxRemoteFile(DbxFileSystem dbxFs, DbxPath remotePath) throws DbxException {
            final DbxFile remoteFile = dbxFs.open(remotePath);

            if (!remoteFile.getSyncStatus().isLatest) {
                if (DEBUG) Log.d(TAG, "Cached DB file not at the lastest version. Updating it..");

                remoteFile.getNewerStatus();
                remoteFile.addListener(new DbxFile.Listener() {
                    @Override
                    public void onFileChange(DbxFile dbxFile) {
                        try {
                            if (DEBUG) Log.d(TAG, "File change event received on DbxFile.Listener()");

                            if (dbxFile.getSyncStatus().isLatest) {
                                if (DEBUG) Log.d(TAG, "Cached DB at the lastest version");
                                updateDBFile(dbxFile);
                                dbxFile.removeListener(this);
                            }
                            else {
                                Log.e(TAG, "Cached DB file hasn't been updated to the latest version");
                                if (mListener != null) mListener.onRemoteDBRestored(false);
                            }
                        }
                        catch (DbxException e) {
                            Log.e(TAG, "Error in the Dropbox pull listener", e);
                            if (mListener != null) mListener.onRemoteDBRestored(false);
                        }
                    }
                });
            }
            else {
                // No need to update the file cache, it's already the latest version
                updateDBFile(remoteFile);
            }
        }

        /**
         * Updates the app's DB using a remote file in Dropbox.
         *
         * @param dbxFile The Dropbox file with the database
         *
         * @throws DbxException Thrown in case of problems during the operation
         */
        private void updateDBFile(DbxFile dbxFile) throws DbxException {
            dbxFile.update();
            final boolean success = replaceDbFile(mContext, dbxFile, mListener);
            if (mListener != null) mListener.onRemoteDBRestored(success);
        }
    }
}
