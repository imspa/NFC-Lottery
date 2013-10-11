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
import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.format.DateFormat;
import android.util.Log;
import au.com.bytecode.opencsv.CSVWriter;
import it.imwatch.nfclottery.data.provider.NFCMLContent;

import java.io.File;
import java.io.FileWriter;

import static it.imwatch.nfclottery.Const.DEBUG;

/**
 * An utility class that exports the contents of the database file to a CSV file.
 */
public class DBExporter {

    private static final String TAG = DBExporter.class.getSimpleName();

    private static final String FILENAME_EXTENSION = ".csv";
    private static final String FILENAME_PREFIX = "DB_backup_";
    private static final String FOLDER_NAME = "NFCLotteryExport";

    private static final String TIMESTAMP_FORMAT = "yyMMdd_kkmmss";

    /**
     * Exports the DB contents in CSV format to external storage.
     *
     * @param context The current context
     *
     * @return Returns the output file path, or <code>null</code> if something
     * went wrong during the export process.
     */
    public static String exportDB(Context context) {
        if (DEBUG) Log.d(TAG, "Exporting DB contents to CSV file");

        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            Log.e(TAG, "Unable to export the DB. External storage is not available.");
            return null;
        }

        // Ensure the export directory exists
        File exportDir = new File(Environment.getExternalStorageDirectory(), FOLDER_NAME);
        if (!exportDir.exists()) {
            if (!exportDir.mkdirs()) {
                Log.w(TAG, "Impossible to create the dirs path: " + exportDir.getAbsolutePath());
                // We'll try anyway to export the DB, you never know.
            }
        }

        final String fileName = String.format("%s%s%s", FILENAME_PREFIX,
                                              DateFormat.format(TIMESTAMP_FORMAT, System.currentTimeMillis()),
                                              FILENAME_EXTENSION);
        File file = new File(exportDir, fileName);
        if (DEBUG) Log.v(TAG, "CSV file DB dump target path: " + file.getAbsolutePath());

        // Read ALL the rows!
        ContentResolver cr = context.getContentResolver();
        Cursor curCSV = cr.query(NFCMLContent.Geeks.CONTENT_URI, null, null, null, null);

        CSVWriter csvWrite = null;
        try {
            // Create the new file, then begin writing the contents to it.
            file.createNewFile();

            csvWrite = new CSVWriter(new FileWriter(file));
            csvWrite.writeNext(curCSV.getColumnNames());        // Writes the column headers

            while (curCSV.moveToNext()) {
                // All the columns to export
                String arrStr[] = {
                    curCSV.getString(NFCMLContent.Geeks.Columns.ID.getIndex()),
                    curCSV.getString(NFCMLContent.Geeks.Columns.EMAIL.getIndex()),
                    curCSV.getString(NFCMLContent.Geeks.Columns.NAME.getIndex()),
                    curCSV.getString(NFCMLContent.Geeks.Columns.ORGANIZATION.getIndex()),
                    curCSV.getString(NFCMLContent.Geeks.Columns.TITLE.getIndex()),
                    curCSV.getString(NFCMLContent.Geeks.Columns.TIMEWINNER.getIndex())};

                csvWrite.writeNext(arrStr);
            }

            Log.i(TAG, "DB succesfully exported to: " + file.getPath());
            return file.getAbsolutePath();
        }
        catch (Exception e) {
            Log.e(TAG, "Error while writing the DB contents to the CSV file", e);
        }
        finally {
            // Cleanup (we don't care for any Exception thrown here)
            try {
                csvWrite.close();
            }
            catch (Throwable ignored) { }
            try {
                curCSV.close();
            }
            catch (Throwable ignored) { }
        }

        return null;
    }

}
