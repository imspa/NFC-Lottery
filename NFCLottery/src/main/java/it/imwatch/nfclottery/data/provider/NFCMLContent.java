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

package it.imwatch.nfclottery.data.provider;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import it.imwatch.nfclottery.data.provider.util.ColumnMetadata;

/**
 * This class was generated by the ContentProviderCodeGenerator project made by Foxykeep
 * <p/>
 * (More information available https://github.com/foxykeep/ContentProviderCodeGenerator)
 */
public abstract class NFCMLContent {

    public static final Uri CONTENT_URI = Uri.parse("content://" + NFCMLProvider.AUTHORITY);

    private NFCMLContent() {
    }

    /**
     * Created in version 1
     */
    public static final class Geeks extends NFCMLContent {

        private static final String LOG_TAG = Geeks.class.getSimpleName();

        public static final String TABLE_NAME = "geeks";
        public static final String TYPE_ELEM_TYPE = "vnd.android.cursor.item/nfcml-geeks";
        public static final String TYPE_DIR_TYPE = "vnd.android.cursor.dir/nfcml-geeks";

        public static final Uri CONTENT_URI = Uri.parse(NFCMLContent.CONTENT_URI + "/" + TABLE_NAME);

        public static enum Columns implements ColumnMetadata {
            ID(BaseColumns._ID, "integer"),
            EMAIL("email", "text"),
            NAME("name", "text"),
            ORGANIZATION("organization", "text"),
            TITLE("title", "integer"),
            TIMEWINNER("timewinner", "integer");

            private final String mName;
            private final String mType;

            private Columns(String name, String type) {
                mName = name;
                mType = type;
            }

            @Override
            public int getIndex() {
                return ordinal();
            }

            @Override
            public String getName() {
                return mName;
            }

            @Override
            public String getType() {
                return mType;
            }
        }

        public static final String[] PROJECTION = new String[] {
            Columns.ID.getName(),
            Columns.EMAIL.getName(),
            Columns.NAME.getName(),
            Columns.ORGANIZATION.getName(),
            Columns.TITLE.getName(),
            Columns.TIMEWINNER.getName()
        };

        private Geeks() {
            // No private constructor
        }

        public static void createTable(SQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE " + TABLE_NAME + " (" + Columns.ID.getName() + " " + Columns.ID.getType() + ", " +
                "" + Columns.EMAIL.getName() + " " + Columns.EMAIL.getType() + ", " + Columns.NAME.getName() + " " +
                Columns.NAME.getType() + ", " + Columns.ORGANIZATION.getName() + " " + Columns.ORGANIZATION.getType()
                + ", " + Columns.TITLE.getName() + " " + Columns.TITLE.getType() + ", " +
                "" + Columns.TIMEWINNER.getName() + " " + Columns.TIMEWINNER.getType() + ", " +
                "PRIMARY KEY (" + Columns.ID.getName() + ")" + ");");

            db.execSQL("CREATE INDEX geeks_email on " + TABLE_NAME + "(" + Columns.EMAIL.getName() + ");");
            db.execSQL("CREATE INDEX geeks_name on " + TABLE_NAME + "(" + Columns.NAME.getName() + ");");
            db.execSQL(
                "CREATE INDEX geeks_organization on " + TABLE_NAME + "(" + Columns.ORGANIZATION.getName() + ");");
        }

        // Version 1 : Creation of the table
        public static void upgradeTable(SQLiteDatabase db, int oldVersion, int newVersion) {

            if (oldVersion < 2) {
                Log.i(LOG_TAG, "Upgrading from version " + oldVersion + " to " + newVersion
                               + ", data will be lost!");

                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME + ";");
                createTable(db);
                return;
            }


            if (oldVersion != newVersion) {
                throw new IllegalStateException("Error upgrading the database to version "
                                                + newVersion);
            }
        }

        static String getBulkInsertString() {
            return new StringBuilder("INSERT INTO ").append(TABLE_NAME).append(" ( ").append(
                Columns.ID.getName()).append(", ").append(Columns.EMAIL.getName()).append(", ").append(
                Columns.NAME.getName()).append(", ").append(Columns.ORGANIZATION.getName()).append(", ").append(
                Columns.TITLE.getName()).append(", ").append(Columns.TIMEWINNER.getName()).append(
                " ) VALUES (?, ?, ?, ?, ?, ?)").toString();
        }

        static void bindValuesInBulkInsert(SQLiteStatement stmt, ContentValues values) {
            int i = 1;
            String value;
            stmt.bindLong(i++, values.getAsLong(Columns.ID.getName()));
            value = values.getAsString(Columns.EMAIL.getName());
            stmt.bindString(i++, value != null ? value : "");
            value = values.getAsString(Columns.NAME.getName());
            stmt.bindString(i++, value != null ? value : "");
            value = values.getAsString(Columns.ORGANIZATION.getName());
            stmt.bindString(i++, value != null ? value : "");
            stmt.bindLong(i++, values.getAsLong(Columns.TITLE.getName()));
            stmt.bindLong(i++, values.getAsLong(Columns.TIMEWINNER.getName()));
        }
    }
}
