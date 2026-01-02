/*
 * Copyright © 2026 Trevin Beattie
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
 */
package com.xmission.trevin.android.todo.provider;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoMetadata;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.data.repeat.AbstractAdjustableRepeat;
import com.xmission.trevin.android.todo.data.repeat.AbstractDateRepeat;
import com.xmission.trevin.android.todo.data.repeat.AbstractRepeat;
import com.xmission.trevin.android.todo.data.repeat.RepeatMonthlyOnDay;
import com.xmission.trevin.android.todo.data.repeat.RepeatSemiMonthlyOnDates;
import com.xmission.trevin.android.todo.data.repeat.RepeatSemiMonthlyOnDays;
import com.xmission.trevin.android.todo.data.repeat.RepeatWeekly;
import com.xmission.trevin.android.todo.data.repeat.RepeatYearlyOnDate;
import com.xmission.trevin.android.todo.data.repeat.RepeatYearlyOnDay;
import com.xmission.trevin.android.todo.data.repeat.WeekDays;
import com.xmission.trevin.android.todo.provider.ToDoSchema.*;

/**
 * Run-time implementation of the To Do repository.
 *
 * @author Trevin Beattie
 */
public class ToDoRepositoryImpl implements ToDoRepository {

    private static final String TAG = "ToDoRepositoryImpl";

    public static final int DATABASE_VERSION = 3;
    static final String CATEGORY_TABLE_NAME = "category";
    static final String METADATA_TABLE_NAME = "misc";
    public static final String TODO_TABLE_NAME = "todo";

    private static final String[] CATEGORY_FIELDS = new String[] {
            ToDoCategoryColumns._ID,
            ToDoCategoryColumns.NAME
    };

    private static final String[] METADATA_FIELDS = new String[] {
            ToDoMetadataColumns._ID,
            ToDoMetadataColumns.NAME,
            ToDoMetadataColumns.VALUE
    };

    private static final String[] ITEM_FIELDS = new String[] {
            ToDoItemColumns._ID,
            ToDoItemColumns.DESCRIPTION,
            ToDoItemColumns.CREATE_TIME,
            ToDoItemColumns.MOD_TIME,
            ToDoItemColumns.DUE_TIME,
            ToDoItemColumns.COMPLETED_TIME,
            ToDoItemColumns.CHECKED,
            ToDoItemColumns.PRIORITY,
            ToDoItemColumns.PRIVATE,
            ToDoItemColumns.CATEGORY_ID,
            ToDoItemColumns.NOTE,
            ToDoItemColumns.ALARM_DAYS_EARLIER,
            ToDoItemColumns.ALARM_TIME,
            ToDoItemColumns.REPEAT_INTERVAL,
            ToDoItemColumns.REPEAT_INCREMENT,
            ToDoItemColumns.REPEAT_WEEK_DAYS,
            ToDoItemColumns.REPEAT_DAY,
            ToDoItemColumns.REPEAT_DAY2,
            ToDoItemColumns.REPEAT_WEEK,
            ToDoItemColumns.REPEAT_WEEK2,
            ToDoItemColumns.REPEAT_MONTH,
            ToDoItemColumns.REPEAT_END,
            ToDoItemColumns.HIDE_DAYS_EARLIER
    };

    /**
     * Projection fields which are available in a To Do item query.
     * This must be used in todo queries to disambiguate columns
     * that are joined with the category table.
     */
    private static final Map<String,String> ITEM_PROJECTION_MAP;

    static {
        Map<String,String> m = new HashMap<>();
        for (String field : ITEM_FIELDS)
            m.put(field, field);
        // Overrides
        m.put(ToDoItemColumns._ID,
                TODO_TABLE_NAME + "." + ToDoItemColumns._ID);
        m.put(ToDoItemColumns.CATEGORY_NAME,
                CATEGORY_TABLE_NAME + "." + ToDoCategoryColumns.NAME
                + " AS " + ToDoItemColumns.CATEGORY_NAME);
        ITEM_PROJECTION_MAP = Collections.unmodifiableMap(m);
    }

    /** Singleton instance of this repository */
    private static ToDoRepository instance = null;

    SQLiteDatabase db = null;

    private String unfiledCategoryName = null;

    private final LinkedHashMap<Context,Integer> openContexts =
            new LinkedHashMap<>();

    /** Observers to call when any To Do data changes */
    private final List<DataSetObserver> registeredObservers =
            new ArrayList<>();

    /** Instantiate the To Do repository.  This should be a singleton. */
    private ToDoRepositoryImpl() {}

    /**
     * Set the instance of ToDoRepository to use.
     * This is intended for use by test code which replaces the
     * normal implementation with a mock repository.
     * Must not be called more than once!
     */
    public static void setInstance(ToDoRepository replacement) {
        if ((instance != null) && (instance != replacement))
            throw new IllegalArgumentException(String.format(
                    "Repository instance has already been set to %s;"
                    + " cannot change it to %s",
                    instance.getClass().getName(),
                    replacement.getClass().getName()));
        instance = replacement;
    }

    /** @return the singleton instance of the To Do repository */
    public static ToDoRepository getInstance() {
        if (instance == null) {
            instance = new ToDoRepositoryImpl();
        }
        return instance;
    }

    /**
     * Check that the database connection is open.  If not,
     * re-establish the connection.
     *
     * @return the database
     *
     * @throws SQLException if we fail to connect to the database
     */
    private synchronized SQLiteDatabase getDb() throws SQLException {
        if ((db != null) && !db.isOpen())
            db = null;
        if (db == null) {
            if (openContexts.isEmpty())
                throw new SQLException("Attempted to use the repository"
                        + " without opening it from a context");
            Context lastContext = null;
            for (Context context : openContexts.keySet())
                lastContext = context;
            ToDoDatabaseHelper openHelper =
                    new ToDoDatabaseHelper(lastContext);
            try {
                db = openHelper.getWritableDatabase();
            } catch (SQLException e) {
                Log.e(TAG, "Failed to connect to the database", e);
                throw e;
            }
        }
        return db;
    }

    /**
     * Call the registered observers when any To Do data changes.
     * The calls <b>must</b> be done on the main UI thread.
     */
    private Runnable observerNotificationRunner = new Runnable() {
        @Override
        public void run() {
            for (DataSetObserver observer : registeredObservers) try {
                observer.onChanged();
            } catch (Exception e) {
                Log.w(TAG, "Caught exception when notifying observer "
                        + observer.getClass().getCanonicalName(), e);
            }
        }
    };

    private  void notifyObservers() {
        // Shortcut out if there are no observers
        if (registeredObservers.isEmpty())
            return;

        // Use the context's UI thread if we have any
        for (Context context : openContexts.keySet()) {
            if (context instanceof Activity) {
                ((Activity) context).runOnUiThread(
                        observerNotificationRunner);
                return;
            }
        }

        // Otherwise fall back to the main looper
        new Handler(Looper.getMainLooper()).post(
                observerNotificationRunner);
    }

    @Override
    public void open(@NonNull Context context) throws SQLException {
        Log.d(TAG, ".open");
        if (db == null) {
            Log.d(TAG, "Connecting to the database");
            ToDoDatabaseHelper mOpenHelper = new ToDoDatabaseHelper(context);
            try {
                db = mOpenHelper.getWritableDatabase();
            } catch (SQLException e) {
                Log.e(TAG, "Failed to connect to the database", e);
                throw e;
            }
        }
        if (unfiledCategoryName == null)
            unfiledCategoryName = context.getString(R.string.Category_Unfiled);
        if (openContexts.containsKey(context)) {
            openContexts.put(context, openContexts.get(context) + 1);
            Log.d(TAG, String.format(
                    "Context has opened the repository %d times",
                    openContexts.get(context)));
        } else {
            openContexts.put(context, 1);
        }
    }

    @Override
    public void release(@NonNull Context context) {
        if (!openContexts.containsKey(context)) {
            Log.e(TAG, ".release called from context"
                    + " which did not open the repository!");
            return;
        }
        Log.d(TAG, ".release");
        int openCount = openContexts.get(context) - 1;
        if (openCount > 0) {
            openContexts.put(context, openCount);
            Log.d(TAG, String.format(
                    "Context has %d remaining connections to the repository",
                    openCount));
        } else {
            openContexts.remove(context);
            if (openContexts.isEmpty() && (db != null)) {
                Log.d(TAG, "The last context has released the repository;"
                        + " closing the database");
                db.close();
                db = null;
            }
        }
    }

    /**
     * Get the index of a column in Cursor results.
     * This also checks that the column exists, which is expected.
     *
     * @param cursor the Cursor from which to get the column
     * @param columnName the name of the column
     *
     * @return the index of the column
     *
     * @throws SQLException if the column does not exist in the Cursor
     */
    private int getColumnIndex(Cursor cursor, String columnName)
        throws SQLException {
        int index = cursor.getColumnIndex(columnName);
        if (index < 0)
            throw new SQLException(String.format(
                    "Missing %s column in the query results", columnName));
        return index;
    }

    @Override
    public int countCategories() {
        Log.d(TAG, ".countCategories");
        Cursor c = getDb().rawQuery("SELECT COUNT(1) FROM "
                + CATEGORY_TABLE_NAME, null);
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            Log.e(TAG, "Nothing returned from count query!");
            return 1;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to count the number of categories!", e);
            return 1;
        } finally {
            c.close();
        }
    }

    @Override
    public List<ToDoCategory> getCategories() {
        Log.d(TAG, ".getCategories");
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(CATEGORY_TABLE_NAME);
        Cursor c = qb.query(getDb(), CATEGORY_FIELDS, null, null,
                null, null, ToDoCategoryColumns.DEFAULT_SORT_ORDER);
        try {
            List<ToDoCategory> categoryList = new ArrayList<>(c.getCount());
            int idColumn = getColumnIndex(c, ToDoCategoryColumns._ID);
            int nameColumn = getColumnIndex(c, ToDoCategoryColumns.NAME);
            while (c.moveToNext()) {
                ToDoCategory tCat = new ToDoCategory();
                tCat.setId(c.getLong(idColumn));
                tCat.setName(c.getString(nameColumn));
                categoryList.add(tCat);
            }
            return categoryList;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to read the category table!", e);
            ToDoCategory unfiled = new ToDoCategory();
            unfiled.setId(ToDoSchema.ToDoCategoryColumns.UNFILED);
            unfiled.setName(unfiledCategoryName);
            return Collections.singletonList(unfiled);
        } finally {
            c.close();
        }
    }

    @Override
    public ToDoCategory getCategoryById(long categoryId) {
        Log.d(TAG, String.format(".getCategoryById(%d)", categoryId));
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(CATEGORY_TABLE_NAME);
        Cursor c = qb.query(getDb(), CATEGORY_FIELDS,
        ToDoCategoryColumns._ID + " = ?",
                new String[] {String.valueOf(categoryId)},
                null, null, null, "1");
        try {
            int nameColumn = getColumnIndex(c, ToDoCategoryColumns.NAME);
            if (c.moveToFirst()) {
                ToDoCategory cat = new ToDoCategory();
                cat.setId(categoryId);
                cat.setName(c.getString(nameColumn));
                return cat;
            }
            return null;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to read category #" + categoryId, e);
            return null;
        } finally {
            c.close();
        }
    }

    @Override
    public ToDoCategory insertCategory(@NonNull String categoryName)
            throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".insertCategory(\"%s\")", categoryName));
        if (TextUtils.isEmpty(categoryName))
            throw new IllegalArgumentException("Category name cannot by empty");
        ContentValues values = new ContentValues();
        values.put(ToDoCategoryColumns.NAME, categoryName);
        long id = getDb().insert(CATEGORY_TABLE_NAME, null, values);
        try {
            long rowId = getDb().insertOrThrow(
                    CATEGORY_TABLE_NAME, null, values);
            if (rowId < 0) {
                Log.e(TAG, String.format(
                        "Failed to add the category \"%s\"; reason unknown",
                        categoryName));
                throw new SQLException("Failed to insert category name");
            }
            if (!getDb().inTransaction())
                notifyObservers();
            ToDoCategory newCat = new ToDoCategory();
            newCat.setId(rowId);
            newCat.setName(categoryName);
            return newCat;
        } catch (SQLException e) {
            Log.e(TAG, String.format("Failed to add the category \"%s\"",
                    categoryName), e);
            throw e;
        }
    }

    @Override
    public ToDoCategory insertCategory(@NonNull ToDoCategory category)
            throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".insertCategory(%s)", category));
        if (TextUtils.isEmpty(category.getName()))
            throw new IllegalArgumentException("Category name cannot by empty");
        if (category.getId() == null)
            return insertCategory(category.getName());
        ContentValues values = new ContentValues();
        values.put(ToDoCategoryColumns._ID, category.getId());
        values.put(ToDoCategoryColumns.NAME, category.getName());
        try {
            long rowId = getDb().insertOrThrow(
                    CATEGORY_TABLE_NAME, null, values);
            if (rowId < 0) {
                Log.e(TAG, String.format(
                        "Failed to add %s; reason unknown", category));
                throw new SQLException("Failed to insert category (with ID)");
            }
            if (!getDb().inTransaction())
                notifyObservers();
            if (rowId != category.getId()) {
                Log.w(TAG, String.format("Category \"%s\" ID was changed from %d to %d",
                        category.getName(), category.getId(), rowId));
                category.setId(rowId);
            }
            return category;
        } catch (SQLException e) {
            Log.e(TAG, String.format("Failed to add %s", category), e);
            throw e;
        }
    }

    @Override
    public ToDoCategory updateCategory(long categoryId, @NonNull String newName)
            throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".updateCategory(%d, \"%s\")",
                categoryId, newName));
        if (TextUtils.isEmpty(newName))
            throw new IllegalArgumentException("Category name cannot be empty");
        if ((categoryId == ToDoCategory.UNFILED) &&
                !newName.equals(unfiledCategoryName)) {
            Log.w(TAG, String.format("Unfiled category cannot be changed;"
                    + " using \"%s\" instead", unfiledCategoryName));
            newName = unfiledCategoryName;
        }
        ContentValues values = new ContentValues();
        values.put(ToDoCategoryColumns._ID, categoryId);
        values.put(ToDoCategoryColumns.NAME, newName);
        try {
            int count = getDb().update(CATEGORY_TABLE_NAME, values,
                    ToDoCategoryColumns._ID + " = ?",
                    new String[] { Long.toString(categoryId) });
            if (count > 0) {
                if (!getDb().inTransaction())
                    notifyObservers();
                ToDoCategory cat = new ToDoCategory();
                cat.setId(categoryId);
                cat.setName(newName);
                return cat;
            } else {
                throw new SQLException("No rows matched category " + categoryId);
            }
        } catch (SQLException e) {
            Log.e(TAG, String.format("Failed to change category %d to \"%s\"",
                    categoryId, newName));
            throw e;
        }
    }

    @Override
    public boolean deleteCategory(long categoryId)
            throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".deleteCategory(%d)", categoryId));
        if (categoryId == ToDoCategory.UNFILED)
            throw new IllegalArgumentException("Will not delete the Unfiled category");
        String[] whereArgs = new String[] { Long.toString(categoryId) };
        SQLiteDatabase db = getDb();
        boolean inTransaction = db.inTransaction();
        try {
            db.beginTransaction();
            int count = db.delete(CATEGORY_TABLE_NAME,
                    ToDoCategoryColumns._ID + " = ?", whereArgs);
            if (count <= 0)
                return false;
            ContentValues update = new ContentValues();
            update.put(ToDoItemColumns.CATEGORY_ID, ToDoCategory.UNFILED);
            db.update(TODO_TABLE_NAME, update,
                    ToDoItemColumns.CATEGORY_ID + " = ?", whereArgs);
            db.setTransactionSuccessful();
            if (!inTransaction)
                notifyObservers();
            return true;
        } catch (SQLException e) {
            Log.e(TAG, String.format("Failed to delete category %d"
                    + " or update notes", categoryId), e);
            throw e;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public boolean deleteAllCategories() throws SQLException {
        Log.d(TAG, ".deleteAllCategories");
        String[] whereArgs = new String[] {
                Long.toString(ToDoCategory.UNFILED)
        };
        SQLiteDatabase db = getDb();
        boolean inTransaction = db.inTransaction();
        try {
            db.beginTransaction();
            int count = db.delete(CATEGORY_TABLE_NAME,
            ToDoCategoryColumns._ID + " != ?", whereArgs);
            if (count <= 0)
                return false;
            ContentValues update = new ContentValues();
            update.put(ToDoItemColumns.CATEGORY_ID, ToDoCategory.UNFILED);
            db.update(TODO_TABLE_NAME, update,
                    ToDoItemColumns.CATEGORY_ID + " != ?", whereArgs);
            db.setTransactionSuccessful();
            if (!inTransaction)
                notifyObservers();
            return true;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to delete all categories or update To Do items", e);
            throw e;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public int countMetadata() {
        Log.d(TAG, ".countMetadata");
        Cursor c = getDb().rawQuery("SELECT COUNT(1) FROM "
                + METADATA_TABLE_NAME, null);
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            Log.e(TAG, "Nothing returned from count query!");
            return 1;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to count the number of metadata!", e);
            return 1;
        } finally {
            c.close();
        }
    }

    @Override
    public List<ToDoMetadata> getMetadata() {
        Log.d(TAG, ".getMetadata");
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(METADATA_TABLE_NAME);
        Cursor c = qb.query(getDb(), METADATA_FIELDS, null, null,
                null, null, ToDoMetadataColumns.NAME);
        try {
            List<ToDoMetadata> metadataList = new ArrayList<>(c.getCount());
            int idColumn = getColumnIndex(c, ToDoMetadataColumns._ID);
            int nameColumn = getColumnIndex(c, ToDoMetadataColumns.NAME);
            int valueColumn = getColumnIndex(c, ToDoMetadataColumns.VALUE);
            while (c.moveToNext()) {
                ToDoMetadata nMeta = new ToDoMetadata();
                nMeta.setId(c.getLong(idColumn));
                nMeta.setName(c.getString(nameColumn));
                nMeta.setValue(c.getBlob(valueColumn));
                metadataList.add(nMeta);
            }
            return metadataList;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to read the metadata table!", e);
            return Collections.emptyList();
        } finally {
            c.close();
        }
    }

    @Override
    public ToDoMetadata getMetadataByName(@NonNull String key) {
        Log.d(TAG, String.format(".getMetadataByName(\"%s\")", key));
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(METADATA_TABLE_NAME);
        Cursor c = qb.query(getDb(), METADATA_FIELDS,
                ToDoMetadataColumns.NAME + " = ?",
                new String[] { key }, null, null, null, "1");
        try {
            int idColumn = getColumnIndex(c, ToDoMetadataColumns._ID);
            int nameColumn = getColumnIndex(c, ToDoMetadataColumns.NAME);
            int valueColumn = getColumnIndex(c, ToDoMetadataColumns.VALUE);
            if (c.moveToFirst()) {
                ToDoMetadata metadata = new ToDoMetadata();
                metadata.setId(c.getLong(idColumn));
                metadata.setName(c.getString(nameColumn));
                metadata.setValue(c.getBlob(valueColumn));
                return metadata;
            }
            return null;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to look up metadata " + key, e);
            return null;
        } finally {
            c.close();
        }
    }

    @Override
    public ToDoMetadata getMetadataById(long id) {
        Log.d(TAG, String.format(".getMetadataById(%d)", id));
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(METADATA_TABLE_NAME);
        Cursor c = qb.query(getDb(), METADATA_FIELDS,
                ToDoMetadataColumns._ID + " = ?",
                new String[] { Long.toString(id) },
                null, null, null, "1");
        try {
            int idColumn = getColumnIndex(c, ToDoMetadataColumns._ID);
            int nameColumn = getColumnIndex(c, ToDoMetadataColumns.NAME);
            int valueColumn = getColumnIndex(c, ToDoMetadataColumns.VALUE);
            if (c.moveToFirst()) {
                ToDoMetadata metadata = new ToDoMetadata();
                metadata.setId(c.getLong(idColumn));
                metadata.setName(c.getString(nameColumn));
                metadata.setValue(c.getBlob(valueColumn));
                return metadata;
            }
            return null;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to look up metadata #" + id, e);
            return null;
        } finally {
            c.close();
        }
    }

    @Override
    public ToDoMetadata upsertMetadata(
            @NonNull String name, @NonNull byte[] value)
            throws IllegalArgumentException, SQLException {
    Log.d(TAG, String.format(".upsertMetadata(\"%s\", (%d bytes))",
            name, value.length));
    if (TextUtils.isEmpty(name))
        throw new IllegalArgumentException("Metadata name cannot be empty");
    ContentValues upsertValues = new ContentValues();
    upsertValues.put(ToDoMetadataColumns.NAME, name);
    upsertValues.put(ToDoMetadataColumns.VALUE, value);
    SQLiteDatabase db = getDb();
    boolean inTransaction = db.inTransaction();
    db.beginTransaction();
    try {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(METADATA_TABLE_NAME);
        Cursor c = qb.query(getDb(), METADATA_FIELDS,
                ToDoMetadataColumns.NAME + " = ?",
                new String[] { name }, null, null, null, "1");
        int idColumn = getColumnIndex(c, ToDoMetadataColumns._ID);
        long rowId;
        if (c.moveToFirst()) {
            rowId = c.getLong(idColumn);
            upsertValues.put(ToDoMetadataColumns._ID, rowId);
            int count =  db.update(METADATA_TABLE_NAME, upsertValues,
                    ToDoMetadataColumns._ID + " = ?",
                    new String[] { Long.toString(rowId) });
            if (count < 1)
                throw new SQLException(
                        "Existing metadata was not updated");
        } else {
            rowId = getDb().insert(METADATA_TABLE_NAME,
                    null, upsertValues);
        }
        if (!inTransaction)
            notifyObservers();
        ToDoMetadata metadata = new ToDoMetadata();
        metadata.setName(name);
        metadata.setValue(value);
        metadata.setId(rowId);
        db.setTransactionSuccessful();
        return metadata;
    } catch (SQLException e) {
        Log.e(TAG, String.format("Failed to set metadata \"%s\"",
                name), e);
        throw e;
    } finally {
        db.endTransaction();
    }
    }

    @Override
    public boolean deleteMetadata(@NonNull String name)
            throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".deleteMetadata(\"%s\")", name));
        try {
            int count = getDb().delete(METADATA_TABLE_NAME,
                    ToDoMetadataColumns.NAME + " = ?",
                    new String[] { name });
            if ((count > 0) && !getDb().inTransaction())
                notifyObservers();
            return (count > 0);
        } catch (SQLException e) {
            Log.e(TAG, String.format("Failed to delete metadata \"%s\"",
                    name), e);
            throw e;
        }
    }

    @Override
    public boolean deleteMetadataById(long id)
            throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".deleteMetadataById(%d)", id));
        try {
            SQLiteDatabase db = getDb();
            boolean inTransaction = db.inTransaction();
            int count = db.delete(METADATA_TABLE_NAME,
                    ToDoMetadataColumns._ID + " = ?",
                    new String[] { Long.toString(id) });
            if ((count > 0) && !inTransaction)
                notifyObservers();
            return (count > 0);
        } catch (SQLException e) {
            Log.e(TAG, String.format("Failed to delete metadata #%d",
                    id), e);
            throw e;
        }
    }

    @Override
    public boolean deleteAllMetadata() throws SQLException {
        Log.d(TAG, ".deleteAllMetadata");
        try {
            SQLiteDatabase db = getDb();
            boolean inTransaction = db.inTransaction();
            int count = db.delete(METADATA_TABLE_NAME, null, null);
            if ((count > 0) && !inTransaction)
                notifyObservers();
            return (count > 0);
        } catch (SQLException e) {
            Log.e(TAG, "Failed to delete all metadata", e);
            throw e;
        }
    }

    @Override
    public int countItems() {
        Log.d(TAG, ".countItems");
        Cursor c = getDb().rawQuery("SELECT COUNT(1) FROM "
                + TODO_TABLE_NAME, null);
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            Log.e(TAG, "Nothing returned from count query!");
            return 0;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to count the number of To Do items!", e);
            return 0;
        } finally {
            c.close();
        }
    }

    @Override
    public int countItemsInCategory(long categoryId) {
        Log.d(TAG, String.format(".countItemsInCategory(%d)", categoryId));
        Cursor c = getDb().rawQuery("SELECT COUNT(1) FROM " + TODO_TABLE_NAME
                + " WHERE " + ToDoItemColumns.CATEGORY_ID + " = ?",
                new String[] { Long.toString(categoryId) });
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            Log.e(TAG, "Nothing returned from count query!");
            return 0;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to count the number of To Do items!", e);
            return 0;
        } finally {
            c.close();
        }
    }

    @Override
    public int countPrivateItems() {
        Log.d(TAG, ".countPrivateItems()");
        Cursor c = getDb().rawQuery("SELECT COUNT(1) FROM " + TODO_TABLE_NAME
                + " WHERE " + ToDoItemColumns.PRIVATE + " >= ?",
                new String[] { "1" });
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            Log.e(TAG, "Nothing returned from count query!");
            return 0;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to count the number of private To Do items!", e);
            return 0;
        } finally {
            c.close();
        }
    }

    @Override
    public int countEncryptedItems() {
        Log.d(TAG, ".countEncryptedItems()");
        Cursor c = getDb().rawQuery("SELECT COUNT(1) FROM " + TODO_TABLE_NAME
                + " WHERE " + ToDoItemColumns.PRIVATE + " > ?",
                new String[] { "1" });
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            Log.e(TAG, "Nothing returned from count query!");
            return 0;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to count the number of encrypted To Do items!", e);
            return 0;
        } finally {
            c.close();
        }
    }

    @Override
    public ToDoCursor getItems(long categoryId,
                               boolean includePrivate,
                               boolean includeEncrypted,
                               String sortOrder) {
        Log.d(TAG, String.format(".getItems(%d,%s,%s,\"%s\")",
                categoryId, includePrivate, includeEncrypted, sortOrder));
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TODO_TABLE_NAME + " JOIN " + CATEGORY_TABLE_NAME
                + " ON (" + TODO_TABLE_NAME + "." + ToDoItemColumns.CATEGORY_ID
                + " = " + CATEGORY_TABLE_NAME + "." + ToDoCategoryColumns._ID + ")");
        qb.setProjectionMap(ITEM_PROJECTION_MAP);
        List<String> selectors = new ArrayList<>();
        List<String> selectorArgs = new ArrayList<>();
        if (categoryId > ToDoPreferences.ALL_CATEGORIES) {
            selectors.add(ToDoItemColumns.CATEGORY_ID + " = ?");
            selectorArgs.add(Long.toString(categoryId));
        }
        if (!includePrivate) {
            selectors.add(ToDoItemColumns.PRIVATE + " <= ?");
            selectorArgs.add("0");
        } else if (!includeEncrypted) {
            selectors.add(ToDoItemColumns.PRIVATE + " <= ?");
            selectorArgs.add("1");
        }
        String selection = null;
        if (!selectors.isEmpty())
            selection = TextUtils.join(" AND ", selectors);
        String[] selectionArgs = selectorArgs.isEmpty() ? null :
                selectorArgs.toArray(new String[selectorArgs.size()]);
        Cursor c = qb.query(getDb(), ITEM_FIELDS, selection, selectionArgs,
                null, null, sortOrder);
        return new ToDoCursorImpl(c);
    }

    @Override
    public long[] getPrivateItemIds() {
        Log.d(TAG, ".getPrivateItemIds()");
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TODO_TABLE_NAME);
        String selection = ToDoItemColumns.PRIVATE + " >= ?";
        String[] selectionArgs = new String[] { "1" };
        Cursor c = qb.query(getDb(), new String[] { ToDoItemColumns._ID },
                selection, selectionArgs, null, null,
                ToDoItemColumns._ID);
        try {
            long[] ids = new long[c.getCount()];
            for (int i = 0; i < ids.length; i++) {
                c.moveToNext();
                ids[i] = c.getLong(0);
            }
            return ids;
        }
        finally {
            c.close();
        }
    }

    @Override
    public ToDoItem getItemById(long itemId) {
        Log.d(TAG, String.format(".getItemById(%d)", itemId));
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TODO_TABLE_NAME + " JOIN " + CATEGORY_TABLE_NAME
                + " ON (" + TODO_TABLE_NAME + "." + ToDoItemColumns.CATEGORY_ID
                + " = " + CATEGORY_TABLE_NAME + "." + ToDoCategoryColumns._ID + ")");
        qb.setProjectionMap(ITEM_PROJECTION_MAP);
        Cursor c = qb.query(getDb(), ITEM_FIELDS,
                TODO_TABLE_NAME + "." + ToDoItemColumns._ID + " = ?",
                new String[] { Long.toString(itemId) },
                null, null, null, "1");
        try {
            ToDoCursor tc = new ToDoCursorImpl(c);
            if (tc.moveToFirst()) {
                return tc.getItem();
            }
            return null;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to read To Do item #" + itemId, e);
            return null;
        } finally {
            c.close();
        }
    }

    /**
     * Convert a {@link LocalDate} field to an integer in
     * milliseconds since the epoch (legacy Date.getTime() value).
     *
     * @param date the date to convert (may be {@code null}
     *
     * @return the number of milliseconds from the Epoch to that date
     * (or {@code null} if {@code date} was {@code null}).
     */
    private static Long dateToMillis(LocalDate date) {
        if (date == null)
            return null;
        return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * Convert a {@link LocalTime} field to an integer in
     * milliseconds since midnight.
     *
     * @param time the time to convert (may be {@code null}
     *
     * @return the number of milliseconds since midnight to that time
     * (or {@code null} if {@code time} was {@code null}).
     */
    private static Long timeToMillis(LocalTime time) {
        if (time == null)
            return null;
        return time.toNanoOfDay() / 1000000L;
    }

    /**
     * Set up ContentValues based on the fields of a ToDoItem.
     * This is used when inserting or updating an item.
     *
     * @param item the ToDoItem to be inserted or updated
     *
     * @return the ContentValues
     *
     * @throws IllegalArgumentException if the {@code private} field
     * is not; if the {@code description} field is empty
     * (if {@code private} &le; 1) or {@code encryptedDescription}
     * is empty (if {@code private} > 1); if a note is present and only
     * encrypted (if {@code private} &le; 1) or only unencrypted
     * (if {@code private} > 1).
     */
    private ContentValues todoToContentValues(ToDoItem item) {
        if (item.getPrivate() <= 1) {
            if (TextUtils.isEmpty(item.getDescription()))
                throw new IllegalArgumentException("Description cannot be empty");
            if ((item.getEncryptedNote() != null) &&
                    (item.getEncryptedNote().length > 0) &&
                    TextUtils.isEmpty(item.getNote()))
                throw new IllegalArgumentException("Note cannot be encrypted");
        } else {
            if ((item.getEncryptedDescription() == null) ||
                    (item.getEncryptedDescription().length == 0))
                throw new IllegalArgumentException("Description must be encrypted");
            if (!TextUtils.isEmpty(item.getNote()) &&
                    ((item.getEncryptedNote() == null) ||
                            (item.getEncryptedNote().length == 0)))
                throw new IllegalArgumentException("Note must be encrypted");
        }
        if (item.getCategoryId() == null)
            item.setCategoryId(ToDoCategory.UNFILED);
        if (item.getCreateTime() == null)
            item.setCreateTimeNow();
        if (item.getModTime() == null)
            item.setModTimeNow();
        ContentValues values = new ContentValues();
        values.put(ToDoItemColumns.CREATE_TIME, item.getCreateTime());
        values.put(ToDoItemColumns.MOD_TIME, item.getModTime());
        values.put(ToDoItemColumns.PRIVATE, item.getPrivate());
        values.put(ToDoItemColumns.CATEGORY_ID, item.getCategoryId());
        values.put(ToDoItemColumns.DUE_TIME, item.getDue());
        values.put(ToDoItemColumns.COMPLETED_TIME, item.getCompleted());
        // SQLite has no boolean type, so this is stored as 0 or 1.
        values.put(ToDoItemColumns.CHECKED, item.isChecked() ? 1 : 0);
        values.put(ToDoItemColumns.PRIORITY, item.getPriority());
        if (item.getAlarm() == null) {
            values.putNull(ToDoItemColumns.ALARM_TIME);
            values.putNull(ToDoItemColumns.ALARM_DAYS_EARLIER);
            values.putNull(ToDoItemColumns.NOTIFICATION_TIME);
        } else {
            values.put(ToDoItemColumns.ALARM_TIME,
                    timeToMillis(item.getAlarm().getTime()));
            values.put(ToDoItemColumns.ALARM_DAYS_EARLIER,
                    item.getAlarm().getAlarmDaysEarlier());
            if (item.getAlarm().getNotificationTime() == null)
                values.putNull(ToDoItemColumns.NOTIFICATION_TIME);
            else
                values.put(ToDoItemColumns.NOTIFICATION_TIME,
                        item.getAlarm().getNotificationTime().toEpochMilli());
        }
        // All repeat settings are dependent on the repeat type;
        // initialize them to null up front, then fill in whatever is there.
        values.putNull(ToDoItemColumns.REPEAT_INTERVAL);
        values.putNull(ToDoItemColumns.REPEAT_INCREMENT);
        values.putNull(ToDoItemColumns.REPEAT_WEEK_DAYS);
        values.putNull(ToDoItemColumns.REPEAT_DAY);
        values.putNull(ToDoItemColumns.REPEAT_DAY2);
        values.putNull(ToDoItemColumns.REPEAT_WEEK);
        values.putNull(ToDoItemColumns.REPEAT_WEEK2);
        values.putNull(ToDoItemColumns.REPEAT_MONTH);
        values.putNull(ToDoItemColumns.REPEAT_END);
        if (item.getRepeatInterval() != null) {
            values.put(ToDoItemColumns.REPEAT_INTERVAL,
                    item.getRepeatInterval().getId());
            if (item.getRepeatInterval() instanceof AbstractRepeat) {
                AbstractRepeat repeat = (AbstractRepeat) item.getRepeatInterval();
                values.put(ToDoItemColumns.REPEAT_INCREMENT,
                        repeat.getIncrement());
                values.put(ToDoItemColumns.REPEAT_END,
                        dateToMillis(repeat.getEnd()));
            }
            if (item.getRepeatInterval() instanceof AbstractAdjustableRepeat) {
                AbstractAdjustableRepeat repeat =
                        (AbstractAdjustableRepeat) item.getRepeatInterval();
                values.put(ToDoItemColumns.REPEAT_WEEK_DAYS,
                        WeekDays.toBitMap(repeat.getAllowedWeekDays()) |
                                repeat.getDirection().getValue());
            }
            if (item.getRepeatInterval() instanceof AbstractDateRepeat) {
                AbstractDateRepeat repeat =
                        (AbstractDateRepeat) item.getRepeatInterval();
                values.put(ToDoItemColumns.REPEAT_DAY, repeat.getDate());
            }
            if (item.getRepeatInterval() instanceof RepeatMonthlyOnDay) {
                RepeatMonthlyOnDay repeat =
                        (RepeatMonthlyOnDay) item.getRepeatInterval();
                values.put(ToDoItemColumns.REPEAT_DAY,
                        repeat.getDay().getValue());
                values.put(ToDoItemColumns.REPEAT_WEEK, repeat.getWeek());
            }
            if (item.getRepeatInterval() instanceof RepeatSemiMonthlyOnDates) {
                RepeatSemiMonthlyOnDates repeat =
                        (RepeatSemiMonthlyOnDates) item.getRepeatInterval();
                values.put(ToDoItemColumns.REPEAT_DAY2, repeat.getDate2());
            }
            if (item.getRepeatInterval() instanceof RepeatSemiMonthlyOnDays) {
                RepeatSemiMonthlyOnDays repeat =
                        (RepeatSemiMonthlyOnDays) item.getRepeatInterval();
                values.put(ToDoItemColumns.REPEAT_DAY2,
                        repeat.getDay2().getValue());
                values.put(ToDoItemColumns.REPEAT_WEEK2, repeat.getWeek2());
            }
            if (item.getRepeatInterval() instanceof RepeatWeekly) {
                RepeatWeekly repeat = (RepeatWeekly) item.getRepeatInterval();
                values.put(ToDoItemColumns.REPEAT_WEEK_DAYS,
                        WeekDays.toBitMap(repeat.getWeekDays()));
            }
            if (item.getRepeatInterval() instanceof RepeatYearlyOnDate) {
                RepeatYearlyOnDate repeat =
                        (RepeatYearlyOnDate) item.getRepeatInterval();
                values.put(ToDoItemColumns.REPEAT_MONTH,
                        repeat.getMonth().getValue());
            }
            if (item.getRepeatInterval() instanceof RepeatYearlyOnDay) {
                RepeatYearlyOnDay repeat =
                        (RepeatYearlyOnDay) item.getRepeatInterval();
                values.put(ToDoItemColumns.REPEAT_MONTH,
                        repeat.getMonth().getValue());
            }
        }
        values.put(ToDoItemColumns.HIDE_DAYS_EARLIER, item.getHideDaysEarlier());
        if (item.getPrivate() <= 1) {
            values.put(ToDoItemColumns.DESCRIPTION, item.getDescription());
            if (TextUtils.isEmpty(item.getNote()))
                values.putNull(ToDoItemColumns.NOTE);
            else
                values.put(ToDoItemColumns.NOTE, item.getNote());
        } else {
            values.put(ToDoItemColumns.DESCRIPTION,
                    item.getEncryptedDescription());
            if ((item.getEncryptedNote() == null) ||
                    (item.getEncryptedNote().length == 0))
                values.putNull(ToDoItemColumns.NOTE);
            else
                values.put(ToDoItemColumns.NOTE, item.getEncryptedNote());
        }
        return values;
    }

    @Override
    public ToDoItem insertItem(@NonNull ToDoItem item)
            throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".insertItem(%s)", item));
        ContentValues values = todoToContentValues(item);
        // Allow setting the ID for inserts, used when importing data.
        if (item.getId() != null)
            values.put(ToDoItemColumns._ID, item.getId());
        try {
            SQLiteDatabase db = getDb();
            boolean inTransaction = db.inTransaction();
            long rowId = getDb().insertOrThrow(TODO_TABLE_NAME, null, values);
            if (rowId < 0) {
                Log.e(TAG, String.format(
                        "Failed to insert %s; reason unknown", item));
                throw new SQLException("Failed to insert To Do item");
            }
            if (!inTransaction)
                notifyObservers();
            item.setId(rowId);
            return item;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to insert " + item, e);
            throw e;
        }
    }

    @Override
    public ToDoItem updateItem(@NonNull ToDoItem item)
            throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".updateItem(%s)", item));
        if (item.getId() == null)
            throw new IllegalArgumentException("Missing item ID");
        ContentValues values = todoToContentValues(item);
        try {
            SQLiteDatabase db = getDb();
            boolean inTransaction = db.inTransaction();
            int count = db.update(TODO_TABLE_NAME, values,
                    ToDoItemColumns._ID + " = ?",
                    new String[] { Long.toString(item.getId()) });
            if (count <= 0)
                throw new SQLException("Now rows matched item " + item.getId());
            if (!inTransaction)
                notifyObservers();
            return item;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to update " + item, e);
            throw e;
        }
    }

    @Override
    public boolean deleteItem(long itemId) throws SQLException {
        Log.d(TAG, String.format(".deleteItem(%d)", itemId));
        String[] whereArgs = new String[] { Long.toString(itemId) };
        try {
            SQLiteDatabase db = getDb();
            boolean inTransaction = db.inTransaction();
            int count = db.delete(TODO_TABLE_NAME,
                    ToDoItemColumns._ID + " = ?", whereArgs);
            if ((count > 0) && !inTransaction)
                notifyObservers();
            return (count > 0);
        } catch (SQLException e) {
            Log.e(TAG, String.format("Failed to delete item %d", itemId), e);
            throw e;
        }
    }

    @Override
    public boolean deleteAllItems() throws SQLException {
        Log.d(TAG, ".deleteAllItems");
        try {
            SQLiteDatabase db = getDb();
            boolean inTransaction = db.inTransaction();
            int count = db.delete(TODO_TABLE_NAME, null, null);
            if ((count > 0) && !inTransaction)
                notifyObservers();
            return (count > 0);
        } catch (SQLException e) {
            Log.e(TAG, "Failed to delete all items", e);
            throw e;
        }
    }

    @Override
    public synchronized void runInTransaction(@NonNull Runnable callback) {
        Log.d(TAG, String.format(".runInTransaction(%s)",
                callback.getClass().getName()));
        SQLiteDatabase db = getDb();
        boolean nestedTransaction = db.inTransaction();
        db.beginTransaction();
        try {
            callback.run();
            db.setTransactionSuccessful();
            if (!nestedTransaction)
                notifyObservers();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void registerDataSetObserver(@NonNull DataSetObserver observer) {
        registeredObservers.add(observer);
    }

    @Override
    public void unregisterDataSetObserver(@NonNull DataSetObserver observer) {
        registeredObservers.remove(observer);
    }
}
