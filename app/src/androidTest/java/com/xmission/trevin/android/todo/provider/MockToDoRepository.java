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
import android.content.Context;
import android.database.DataSetObserver;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;

import com.xmission.trevin.android.todo.R;
import com.xmission.trevin.android.todo.data.*;

import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * In-memory implementation of the To Do repository for use in UI tests.
 *
 * @author Trevin Beattie
 */
public class MockToDoRepository implements ToDoRepository {

    private static final String TAG = "MockToDoRepository";

    /** Singleton instance of this repository */
    private static MockToDoRepository instance;

    private String unfiledCategoryName = null;

    private final LinkedHashMap<Context,Integer> openContexts =
            new LinkedHashMap<>();

    /**
     * Category table.  This will be initialized the first time a context
     * opens the repository, since we need the &ldquo;Unfiled&rdquo;
     * category name from the context resources.
     */
    private final SortedMap<Long,String> categories = new TreeMap<>();

    private long nextCategoryId = 1;

    /**
     * Metadata table.  This is keyed by name which is the most common
     * use case.
     */
    private final SortedMap<String,ToDoMetadata> metadata = new TreeMap<>();

    private long nextMetadataId = 1;

    /** To Do item table.  This is indexed by the item ID. */
    private final SortedMap<Long,ToDoItem> itemTable = new TreeMap<>();

    private long nextItemId = 1;

    private int transactionLevel = 0;

    /** Observers to call when any To Do data changes */
    private final ArrayList<DataSetObserver> registeredObservers =
            new ArrayList<>();

    /** Instantiate the To Do repository.  This should be a singleton. */
    private MockToDoRepository() {}

    /** @return the singleton instance of the To Do repository */
    public static MockToDoRepository getInstance() {
        if (instance == null) {
            instance = new MockToDoRepository();
        }
        return instance;
    }

    /**
     * Call the registered observers when any mock To Do data changes.
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

    /**
     * Call the registered observers when any To Do data changes.
     */
    private void notifyObservers() {
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
        if (unfiledCategoryName == null) {
            unfiledCategoryName = context.getString(R.string.Category_Unfiled);
            categories.put((long) ToDoCategory.UNFILED, unfiledCategoryName);
        }
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
            if (openContexts.isEmpty()) {
                Log.d(TAG, "The last context has released the repository");
            }
        }
    }

    /**
     * Test verification method: determine whether
     * all contexts have closed the repository
     *
     * @return {@code true} if there are no open contexts,
     * {@code false} if there are any still open.
     */
    public boolean isOpenContextsEmpty() {
        return openContexts.isEmpty();
    }

    /**
     * Clear the mock database.  This is intended to be used between tests.
     */
    public synchronized void clear() {
        categories.clear();
        if (unfiledCategoryName != null)
            categories.put((long) ToDoCategory.UNFILED, unfiledCategoryName);
        nextCategoryId = 1;
        metadata.clear();
        nextMetadataId = 1;
        itemTable.clear();
        nextItemId = 1;
        transactionLevel = 0;
    }

    /* **** Comparators used in sorting results by arbitrary columns **** */

    /** Compares categories by name alphabetically (case-sensitive) */
    public static final Comparator<ToDoCategory> CATEGORY_COMPARATOR =
            new Comparator<ToDoCategory>() {
                @Override
                public int compare(ToDoCategory cat1, ToDoCategory cat2) {
                    return cat1.getName().compareTo(cat2.getName());
                }
            };

    /** Compare metadata by name alphabetically (case-sensitive) */
    public static final Comparator<ToDoMetadata> METADATA_COMPARATOR =
            new Comparator<ToDoMetadata>() {
                @Override
                public int compare(ToDoMetadata meta1, ToDoMetadata meta2) {
                    return meta1.getName().compareTo(meta2.getName());
                }
            };

    /** Compare To Do items by their ID&rsquo;s.  Handles {@code null}s. */
    public static final Comparator<ToDoItem> TODO_ID_COMPARATOR =
            new Comparator<ToDoItem>() {
                @Override
                public int compare(ToDoItem item1, ToDoItem item2) {
                    if (item1.getId() == item2.getId())
                        return 0;
                    if (item1.getId() == null)
                        return -1;
                    if (item2.getId() == null)
                        return 1;
                    return (item1.getId() < item2.getId()) ? -1 : 1;
                }
            };

    /**
     * A comparator for To Do item descriptions which may or may not be
     * encrypted.  We don&rsquo;t do any decrypting on the storage side,
     * which means if an item is encrypted it&rsquo;s content is treated
     * as a BLOB.  There is no good alternative that doesn&rsquo;t expose
     * plain-text content.  According to the SQLite documentation for <a
     * href="https://www.sqlite.org/datatype3.html#sort_order">Sort Order</a>,
     * &ldquo;A TEXT value is less than a BLOB value.&rdquo;  Although
     * descriptions should never be empty, this comparator handles nulls
     * by ordering them first (as if an empty string).
     * <p>
     * This comparator can be set up as case-sensitive or case-insensitive;
     * that distinction only applies when comparing non-encrypted items.
     * </p>
     */
    private static class ToDoDescriptionComparator
            implements Comparator<ToDoItem> {
        private final boolean isCaseInsensitive;
        /**
         * @param caseInsensitive Whether this comparator should compare
         * (public) descriptions ignoring case
         */
        ToDoDescriptionComparator(boolean caseInsensitive) {
            isCaseInsensitive = caseInsensitive;
        }
        @Override
        public int compare(ToDoItem item1, ToDoItem item2) {
            if (item1.getPrivate() <= 1) {
                if (item2.getPrivate() <= 1) {
                    // Both items are plain; we can compare the fields directly
                    if (item1.getDescription() == null) {
                        if (item2.getDescription() == null)
                            return 0;
                        return -1;
                    }
                    if (item2.getDescription() == null)
                        return 1;
                    return isCaseInsensitive
                            ? item1.getDescription().compareToIgnoreCase(
                                    item2.getDescription())
                            : item1.getDescription().compareTo(
                                    item2.getDescription());
                }
                // First item is plain, second is encrypted.
                // Check for nulls.
                if (item2.getEncryptedDescription() == null)
                    return (item1.getDescription() == null) ? 0 : 1;
                return -1;
            }
            if (item2.getPrivate() <= 1) {
                // First item is encrypted, second item is plain.
                // Check for nulls.
                if (item2.getDescription() == null)
                    return (item1.getEncryptedDescription() == null) ? 0 : 1;
                return 1;
            }
            byte[] ba1 = item1.getEncryptedDescription();
            byte[] ba2 = item2.getEncryptedDescription();
            if (ba1 == null)
                ba1 = new byte[0];
            if (ba2 == null)
                ba2 = new byte[0];
            // Both items are encrypted.  The byte arrays should
            // both be non-null; do an unsigned byte-by-byte comparison.
            int i = 0;
            while ((i < ba1.length) || (i < ba2.length)) {
                if (i > ba1.length)
                    return -1;
                if (i > ba2.length)
                    return 1;
                if (ba1[i] != ba2[i])
                    return ((ba1[i] & 0xff) < (ba2[i] & 0xff)) ? -1 : 1;
                i++;
            }
            return 0;
        }
    }

    /** Compare To Do items by their descriptions (case-sensitive) */
    public static final Comparator<ToDoItem> TODO_DESCRIPTION_COMPARATOR =
            new ToDoDescriptionComparator(false);


    /** Compare To Do items by their descriptions (case-insensitive) */
    public static final Comparator<ToDoItem>
            TODO_DESCRIPTION_COMPARATOR_IGNORE_CASE =
            new ToDoDescriptionComparator(true);

    /** Compare To Do items by their creation time.  Handles null fields. */
    public static final Comparator<ToDoItem> TODO_CREATE_TIME_COMPARATOR =
            new Comparator<ToDoItem>() {
                @Override
                public int compare(ToDoItem item1, ToDoItem item2) {
                    if ((item1.getCreateTime() == null) &&
                            (item2.getCreateTime() == null))
                        return 0;
                    if (item1.getCreateTime() == null)
                        return -1;
                    if (item2.getCreateTime() == null)
                        return 1;
                    return item1.getCreateTime()
                            .compareTo(item2.getCreateTime());
                }
            };

    /** Compare To Do items by their modification time.  Handles null fields. */
    public static final Comparator<ToDoItem> TODO_MOD_TIME_COMPARATOR =
            new Comparator<ToDoItem>() {
                @Override
                public int compare(ToDoItem item1, ToDoItem item2) {
                    if ((item1.getModTime() == null) &&
                            (item2.getModTime() == null))
                        return 0;
                    if (item1.getModTime() == null)
                        return -1;
                    if (item2.getModTime() == null)
                        return 1;
                    return item1.getModTime()
                            .compareTo(item2.getModTime());
                }
            };

    /** Compare To Do items by their due dates.  Handles nulls. */
    public static final Comparator<ToDoItem> TODO_DUE_COMPARATOR =
            new Comparator<ToDoItem>() {
                @Override
                public int compare(ToDoItem item1, ToDoItem item2) {
                    if ((item1.getDue() == null) &&
                            (item2.getDue() == null))
                        return 0;
                    if (item1.getDue() == null)
                        return -1;
                    if (item2.getDue() == null)
                        return 1;
                    return item1.getDue().compareTo(item2.getDue());
                }
            };

    /** Compare To Do items by their completion time.  Handles nulls. */
    public static final Comparator<ToDoItem> TODO_COMPLETED_COMPARATOR =
            new Comparator<ToDoItem>() {
                @Override
                public int compare(ToDoItem item1, ToDoItem item2) {
                    if ((item1.getCompleted() == null) &&
                            (item2.getCompleted() == null))
                        return 0;
                    if (item1.getCompleted() == null)
                        return -1;
                    if (item2.getCompleted() == null)
                        return 1;
                    return item1.getCompleted()
                            .compareTo(item2.getCompleted());
                }
            };

    /** Compare To Do items by whether they are done (checked). */
    public static final Comparator<ToDoItem> TODO_CHECKED_COMPARATOR =
            new Comparator<ToDoItem>() {
                @Override
                public int compare(ToDoItem item1, ToDoItem item2) {
                    return Boolean.compare(item1.isChecked(),
                            item2.isChecked());
                }
            };

    /** Compare To Do items by their priority. */
    public static final Comparator<ToDoItem> TODO_PRIORITY_COMPARATOR =
            new Comparator<ToDoItem>() {
                @Override
                public int compare(ToDoItem item1, ToDoItem item2) {
                    return Integer.compare(item1.getPriority(),
                            item2.getPriority());
                }
            };

    /**
     * A comparator for a To Do item&rsquo;s privacy level.
     * At this point we&rsquo;re only defining these comparators for
     * completeness; the mock should support whatever fields a user
     * may throw at it.
     */
    public static final Comparator<ToDoItem> TODO_PRIVATE_COMPARATOR =
            new Comparator<ToDoItem>() {
                @Override
                public int compare(ToDoItem item1, ToDoItem item2) {
                    return Integer.compare(item1.getPrivate(),
                            item2.getPrivate());
                }
            };

    /** Compare To Do items by their category ID&rsquo;s. */
    public static final Comparator<ToDoItem> TODO_CATEGORY_ID_COMPARATOR =
            new Comparator<ToDoItem>() {
                @Override
                public int compare(ToDoItem item1, ToDoItem item2) {
                    return Long.compare(item1.getCategoryId(),
                            item2.getCategoryId());
                }
            };

    /**
     * A comparator for a To Do item&rsquo;s category name.
     * If the category names are null, compares the category ID instead.
     * If one note has a category name but the other only has an ID,
     * order ID&rsquo;s before names.
     * <p>
     * This comparator can be set up as case-sensitive or case-insensitive.
     * </p>
     */
    private static class ToDoCategoryNameComparator
            implements Comparator<ToDoItem> {
        private final boolean isCaseInsensitive;
        ToDoCategoryNameComparator(boolean caseInsensitive) {
            isCaseInsensitive = caseInsensitive;
        }
        @Override
        public int compare(ToDoItem item1, ToDoItem item2) {
            if (item1.getCategoryName() == null) {
                if (item2.getCategoryName() == null)
                    // Fall back to the category ID's
                    return Long.compare(item1.getCategoryId(),
                            item2.getCategoryId());
                return -1;
            }
            if (item2.getCategoryName() == null)
                return 1;
            return isCaseInsensitive
                    ? item1.getCategoryName().compareToIgnoreCase(
                            item2.getCategoryName())
                    : item1.getCategoryName().compareTo(
                            item2.getCategoryName());
        }
    }

    /** Compare To Do items by their category name (case-sensitive) */
    public static final Comparator<ToDoItem> TODO_CATEGORY_COMPARATOR =
            new ToDoCategoryNameComparator(false);


    /** Compare To Do items by their category name (case-insensitive) */
    public static final Comparator<ToDoItem>
            TODO_CATEGORY_COMPARATOR_IGNORE_CASE =
            new ToDoCategoryNameComparator(true);

    /**
     * A comparator for To Do item notes which may or may not be
     * encrypted.  We don&rsquo;t do any decrypting on the storage side,
     * which means if an item is encrypted it&rsquo;s content is treated
     * as a BLOB.  There is no good alternative that doesn&rsquo;t expose
     * plain-text content.  According to the SQLite documentation for <a
     * href="https://www.sqlite.org/datatype3.html#sort_order">Sort Order</a>,
     * &ldquo;A TEXT value is less than a BLOB value.&rdquo;  This
     * comparator handles nulls by ordering them first (as if an empty string).
     * <p>
     * This comparator can be set up as case-sensitive or case-insensitive;
     * that distinction only applies when comparing non-encrypted items.
     * </p>
     */
    private static class ToDoNoteComparator
            implements Comparator<ToDoItem> {
        private final boolean isCaseInsensitive;
        /**
         * @param caseInsensitive Whether this comparator should compare
         * (public) descriptions ignoring case
         */
        ToDoNoteComparator(boolean caseInsensitive) {
            isCaseInsensitive = caseInsensitive;
        }
        @Override
        public int compare(ToDoItem item1, ToDoItem item2) {
            if (item1.getPrivate() <= 1) {
                if (item2.getPrivate() <= 1) {
                    // Both items are plain; we can compare the fields directly
                    if (item1.getNote() == null) {
                        if (item2.getNote() == null)
                            return 0;
                        return -1;
                    }
                    if (item2.getNote() == null)
                        return 1;
                    return isCaseInsensitive
                            ? item1.getNote().compareToIgnoreCase(
                                    item2.getNote())
                            : item1.getNote().compareTo(
                                    item2.getNote());
                }
                // First item is plain, second is encrypted.
                // Check for nulls.
                if (item2.getEncryptedNote() == null)
                    return (item1.getNote() == null) ? 0 : 1;
                return -1;
            }
            if (item2.getPrivate() <= 1) {
                // First item is encrypted, second item is plain.
                // Check for nulls.
                if (item2.getNote() == null)
                    return (item1.getEncryptedNote() == null) ? 0 : 1;
                return 1;
            }
            byte[] ba1 = item1.getEncryptedNote();
            byte[] ba2 = item2.getEncryptedNote();
            if (ba1 == null)
                ba1 = new byte[0];
            if (ba2 == null)
                ba2 = new byte[0];
            // Both items are encrypted.  The byte arrays should
            // both be non-null; do an unsigned byte-by-byte comparison.
            int i = 0;
            while ((i < ba1.length) || (i < ba2.length)) {
                if (i > ba1.length)
                    return -1;
                if (i > ba2.length)
                    return 1;
                if (ba1[i] != ba2[i])
                    return ((ba1[i] & 0xff) < (ba2[i] & 0xff)) ? -1 : 1;
                i++;
            }
            return 0;
        }
    }

    /** Compare To Do items by their descriptions (case-sensitive) */
    public static final Comparator<ToDoItem> TODO_NOTE_COMPARATOR =
            new ToDoNoteComparator(false);


    /** Compare To Do items by their descriptions (case-insensitive) */
    public static final Comparator<ToDoItem> TODO_NOTECOMPARATOR_IGNORE_CASE =
            new ToDoNoteComparator(true);

    /** Compare To Do items by their alarm dates.  Handles nulls. */
    public static final Comparator<ToDoItem> TODO_ALARM_COMPARATOR =
            new Comparator<ToDoItem>() {
                @Override
                public int compare(ToDoItem item1, ToDoItem item2) {
                    if ((item1.getAlarm() == null) &&
                            (item2.getAlarm() == null))
                        return 0;
                    if (item1.getAlarm() == null)
                        return -1;
                    if (item2.getAlarm() == null)
                        return 1;
                    return item1.getAlarm().getTime().compareTo(
                            item2.getAlarm().getTime());
                }
            };

    /**
     * Compare To Do items by their alarm notification times.
     * Handles nulls.
     */
    public static final Comparator<ToDoItem> TODO_NOTIFICATION_COMPARATOR =
            new Comparator<ToDoItem>() {
                @Override
                public int compare(ToDoItem item1, ToDoItem item2) {
                    Instant time1 = (item1.getAlarm() == null) ? null
                            : item1.getAlarm().getNotificationTime();
                    Instant time2 = (item2.getAlarm() == null) ? null
                            : item2.getAlarm().getNotificationTime();
                    if ((time1 == null) && (time2 == null))
                        return 0;
                    if (time1 == null)
                        return -1;
                    if (time2 == null)
                        return 1;
                    return time1.compareTo(time2);
                }
            };

    // To Do: Implement comparators for the rest of the ToDoItem fields...
    // if it turns out there's a legitimate use for sorting by them.

    /* **** Begin the mock DB operations implementation section **** */

    @Override
    public int countCategories() {
        Log.d(TAG, ".countCategories");
        return categories.size();
    }

    @Override
    public long getMaxCategoryId() {
        if (categories.isEmpty())
            return nextCategoryId;
        return categories.lastKey();
    }

    @Override
    public List<ToDoCategory> getCategories() {
        Log.d(TAG, ".getCategories");
        List<ToDoCategory> list = new ArrayList<>(categories.size());
        for (Map.Entry<Long,String> categoryEntry : categories.entrySet()) {
            ToDoCategory category = new ToDoCategory();
            category.setId(categoryEntry.getKey());
            category.setName(categoryEntry.getValue());
            list.add(category);
        }
        Collections.sort(list, CATEGORY_COMPARATOR);
        return list;
    }

    @Override
    public ToDoCategory getCategoryById(long categoryId) {
        Log.d(TAG, String.format(".getCategoryById(%d)", categoryId));
        if (categories.containsKey(categoryId)) {
            ToDoCategory category = new ToDoCategory();
            category.setId(categoryId);
            category.setName(categories.get(categoryId));
            return category;
        }
        return null;
    }

    @Override
    public synchronized ToDoCategory insertCategory(@NonNull String categoryName)
        throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".insertCategory(\"%s\")", categoryName));
        if (StringUtils.isEmpty(categoryName))
            throw new IllegalArgumentException("Category name cannot by empty");
        if (categories.containsValue(categoryName))
            throw new SQLException("Category name already exists");
        ToDoCategory newCategory = new ToDoCategory();
        newCategory.setId(nextCategoryId++);
        newCategory.setName(categoryName);
        categories.put(newCategory.getId(), categoryName);
        if (transactionLevel <= 0)
            notifyObservers();
        return newCategory;
    }

    @Override
    public synchronized ToDoCategory insertCategory(@NonNull ToDoCategory newCategory)
        throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".insertCategory(%s)", newCategory));
        if (newCategory.getId() == null)
            return insertCategory(newCategory.getName());
        if (StringUtils.isEmpty(newCategory.getName()))
            throw new IllegalArgumentException("Category name cannot by empty");
        if (categories.containsKey(newCategory.getId()))
            throw new SQLException("Category ID already exists");
        if (categories.containsValue(newCategory.getName()))
            throw new SQLException("Category name already exists");
        categories.put(newCategory.getId(), newCategory.getName());
        if (newCategory.getId() >= nextCategoryId)
            nextCategoryId = newCategory.getId() + 1;
        if (transactionLevel <= 0)
            notifyObservers();
        return newCategory;
    }

    @Override
    public synchronized ToDoCategory updateCategory(
            long categoryId, @NonNull String newName)
        throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".updateCategory(%d, \"%s\")",
                categoryId, newName));
        if (StringUtils.isEmpty(newName))
            throw new IllegalArgumentException("Category name cannot be empty");
        if ((categoryId == ToDoCategory.UNFILED) &&
                !newName.equals(unfiledCategoryName)) {
            Log.w(TAG, String.format("Unfiled category cannot be changed;"
                    + " using \"%s\" instead", unfiledCategoryName));
            newName = unfiledCategoryName;
        }
        if (!categories.containsKey(categoryId))
            throw new SQLException("No rows matched category " + categoryId);
        if (categories.containsValue(newName)) {
            if (!categories.get(categoryId).equals(newName))
                throw new SQLException("Category name already exists");
        }
        categories.put(categoryId, newName);
        if (transactionLevel <= 0)
            notifyObservers();
        ToDoCategory retCat = new ToDoCategory();
        retCat.setId(categoryId);
        retCat.setName(newName);
        return retCat;
    }

    @Override
    public synchronized boolean deleteCategory(long categoryId)
            throws IllegalArgumentException {
        Log.d(TAG, String.format(".deleteCategory(%d)", categoryId));
        if (categoryId == ToDoCategory.UNFILED)
            throw new IllegalArgumentException(
                    "Will not delete the Unfiled category");
        if (!categories.containsKey(categoryId))
            return false;
        for (ToDoItem item : itemTable.values()) {
            if (item.getCategoryId() == categoryId)
                item.setCategoryId(ToDoCategory.UNFILED);
        }
        categories.remove(categoryId);
        if (transactionLevel <= 0)
            notifyObservers();
        return true;
    }

    @Override
    public synchronized boolean deleteAllCategories() throws SQLException {
        Log.d(TAG, ".deleteAllCategories");
        if (categories.isEmpty())
            return false;
        if (categories.size() == 1) {
            if (categories.containsKey((long) ToDoCategory.UNFILED))
                return false;
        }
        categories.clear();
        if (unfiledCategoryName != null)
            categories.put((long) ToDoCategory.UNFILED, unfiledCategoryName);
        for (ToDoItem item : itemTable.values())
            item.setCategoryId(ToDoCategory.UNFILED);
        if (transactionLevel <= 0)
            notifyObservers();
        return true;
    }

    @Override
    public int countMetadata() {
        Log.d(TAG, ".countMetadata");
        return metadata.size();
    }

    @Override
    public List<ToDoMetadata> getMetadata() {
        Log.d(TAG, ".getMetadata");
        List<ToDoMetadata> list = new ArrayList<>(metadata.size());
        for (ToDoMetadata datum : metadata.values()) {
            list.add(datum.clone());
        }
        Collections.sort(list, METADATA_COMPARATOR);
        return list;
    }

    @Override
    public ToDoMetadata getMetadataByName(@NonNull String key) {
        Log.d(TAG, String.format(".getMetadataByName(\"%s\")", key));
        return metadata.get(key);
    }

    @Override
    public ToDoMetadata getMetadataById(long id) {
        Log.d(TAG, String.format(".getMetadataById(%d)", id));
        for (ToDoMetadata datum : metadata.values()) {
            if (datum.getId() == id)
                return datum.clone();
        }
        return null;
    }

    @Override
    public synchronized ToDoMetadata upsertMetadata(
            @NonNull String name, @NonNull byte[] value)
            throws IllegalArgumentException {
        Log.d(TAG, String.format(".upsertMetadata(\"%s\", (%d bytes))",
                name, value.length));
        if (StringUtils.isEmpty(name))
            throw new IllegalArgumentException("Metadata name cannot be empty");
        ToDoMetadata newMeta = new ToDoMetadata();
        newMeta.setName(name);
        newMeta.setValue(new byte[value.length]);
        System.arraycopy(value, 0, newMeta.getValue(), 0, value.length);
        ToDoMetadata oldMeta = metadata.get(name);
        if (oldMeta != null) {
            newMeta.setId(oldMeta.getId());
            oldMeta.setValue(newMeta.getValue());
        } else {
            newMeta.setId(nextMetadataId++);
            metadata.put(name, newMeta.clone());
        }
        if (transactionLevel <= 0)
            notifyObservers();
        return newMeta;
    }

    @Override
    public synchronized boolean deleteMetadata(@NonNull String name)
            throws IllegalArgumentException {
        Log.d(TAG, String.format(".deleteMetadata(\"%s\")", name));
        if (!metadata.containsKey(name))
            return false;
        metadata.remove(name);
        if (transactionLevel <= 0)
            notifyObservers();
        return true;
    }

    @Override
    public synchronized boolean deleteMetadataById(long id)
            throws IllegalArgumentException {
        Log.d(TAG, String.format(".deleteMetadataById(%d)", id));
        Iterator<Map.Entry<String,ToDoMetadata>> iter =
                metadata.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String,ToDoMetadata> metaEntry = iter.next();
            if (metaEntry.getValue().getId() == id) {
                iter.remove();
                if (transactionLevel <= 0)
                    notifyObservers();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean deleteAllMetadata() {
        Log.d(TAG, ".deleteAllMetadata");
        if (metadata.isEmpty())
            return false;
        metadata.clear();
        if (transactionLevel <= 0)
            notifyObservers();
        return true;
    }

    @Override
    public int countItems() {
        Log.d(TAG, ".countItems");
        return itemTable.size();
    }

    @Override
    public int countItemsInCategory(long categoryId) {
        Log.d(TAG, String.format(".countItemsInCategory(%d)", categoryId));
        int counter = 0;
        for (ToDoItem item : itemTable.values()) {
            if (item.getCategoryId() == categoryId)
                counter++;
        }
        return counter;
    }

    @Override
    public int countPrivateItems() {
        Log.d(TAG, ".countPrivateItems");
        int counter = 0;
        for (ToDoItem item : itemTable.values()) {
            if (item.isPrivate())
                counter++;
        }
        return counter;
    }

    @Override
    public int countEncryptedItems() {
        Log.d(TAG, ".countEncryptedItems");
        int counter = 0;
        for (ToDoItem item : itemTable.values()) {
            if (item.isEncrypted())
                counter++;
        }
        return counter;
    }

    @Override
    public long getMaxItemId() {
        if (itemTable.isEmpty())
            return nextItemId;
        return itemTable.lastKey();
    }

    @Override
    public ToDoCursor getItems(long categoryId,
                               boolean includePrivate,
                               boolean includeEncrypted,
                               @NonNull String sortOrder) {
        Log.d(TAG, String.format(".getItems(%d,%s,%s,%s)",
                categoryId, includePrivate, includeEncrypted, sortOrder));
        List<ToDoItem> foundItems = new ArrayList<>();
        for (ToDoItem item : itemTable.values()) {
            if (categoryId != ToDoPreferences.ALL_CATEGORIES) {
                if (item.getCategoryId() != categoryId)
                    continue;
            }
            if (!includePrivate && item.isPrivate())
                continue;
            if (!includeEncrypted && item.isEncrypted())
                continue;
            // Add the category name to a copy of the item
            ToDoItem copy = item.clone();
            copy.setCategoryName(categories.get(item.getCategoryId()));
            foundItems.add(copy);
        }
        Comparator<ToDoItem> comparator = null;
        for (String sortItem : sortOrder.split(",")) {
            sortItem = sortItem.trim();
            String[] sortParts = sortItem.split(" +");
            Comparator<ToDoItem> nextComparator;
            if (sortParts[0].equalsIgnoreCase(
                    ToDoRepositoryImpl.TODO_TABLE_NAME
                            + "." + ToDoSchema.ToDoItemColumns._ID))
                nextComparator = TODO_ID_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.DESCRIPTION))
                nextComparator = TODO_DESCRIPTION_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase("lower("
                    + ToDoSchema.ToDoItemColumns.DESCRIPTION + ")"))
                nextComparator = TODO_DESCRIPTION_COMPARATOR_IGNORE_CASE;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.CREATE_TIME))
                nextComparator = TODO_CREATE_TIME_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.MOD_TIME))
                nextComparator = TODO_MOD_TIME_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.DUE_TIME))
                nextComparator = TODO_DUE_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.COMPLETED_TIME))
                nextComparator = TODO_COMPLETED_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.CHECKED))
                nextComparator = TODO_CHECKED_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.PRIORITY))
                nextComparator = TODO_PRIORITY_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.PRIVATE))
                nextComparator = TODO_PRIVATE_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.CATEGORY_ID))
                nextComparator = TODO_CATEGORY_ID_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.CATEGORY_NAME))
                nextComparator = TODO_CATEGORY_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase("lower("
                    + ToDoSchema.ToDoItemColumns.CATEGORY_NAME + ")"))
                nextComparator = TODO_CATEGORY_COMPARATOR_IGNORE_CASE;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.NOTE))
                nextComparator = TODO_NOTE_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase("lower("
                    + ToDoSchema.ToDoItemColumns.NOTE + ")"))
                nextComparator = TODO_NOTECOMPARATOR_IGNORE_CASE;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.ALARM_TIME))
                nextComparator = TODO_ALARM_COMPARATOR;
            else if (sortParts[0].equalsIgnoreCase(ToDoSchema
                    .ToDoItemColumns.NOTIFICATION_TIME))
                nextComparator = TODO_NOTIFICATION_COMPARATOR;
            // To Do: Add cases for additional comparators as they are implemented
            else
                throw new SQLException(
                        "Unsupported sort field: " + sortParts[0]);
            if (sortParts.length > 1) {
                if (sortParts[1].equalsIgnoreCase("desc"))
                    nextComparator = Collections.reverseOrder(nextComparator);
                else if (!sortParts[1].equalsIgnoreCase("asc"))
                    throw new SQLException(
                            "Unrecognized sort direction: " + sortParts[1]);
                if (sortParts.length > 2)
                    throw new SQLException(
                            "Invalid ORDER BY clause: " + sortItem);
            }
            if (comparator == null)
                comparator = nextComparator;
            else
                comparator = comparator.thenComparing(nextComparator);
        }
        if (comparator != null)
            Collections.sort(foundItems, comparator);
        return new MockToDoCursor(foundItems);
    }

    @Override
    public SortedSet<AlarmInfo> getPendingAlarms(ZoneId timeZone) {
        Log.d(TAG, ".getPendingAlarms()");
        SortedSet<AlarmInfo> alarms = new TreeSet<>();
        for (ToDoItem item : itemTable.values()) {
            AlarmInfo alarm = new AlarmInfo(item);
            alarm.setTimeZone(timeZone);
            alarms.add(alarm);
        }
        return alarms;
    }

    @Override
    public long[] getPrivateItemIds() {
        Log.d(TAG, ".getPrivateItemIds");
        long[] ids = new long[countPrivateItems()];
        int i = 0;
        for (ToDoItem item : itemTable.values()) {
            if (item.isPrivate()) {
                if (i >= ids.length) {
                    // Something must have changed the notes
                    // while we were iterating!  Resize the array.
                    Log.w(TAG, "Private item count mismatch");
                    ids = Arrays.copyOf(ids, i+1);
                }
                ids[i++] = item.getId();
            }
        }
        if (i < ids.length) {
            Log.w(TAG, "Private item count mismatch");
            ids = Arrays.copyOf(ids, i);
        }
        return ids;
    }

    @Override
    public ToDoItem getItemById(long itemId) {
        Log.d(TAG, String.format(".getItemById(%d)", itemId));
        ToDoItem foundItem = itemTable.get(itemId);
        if (foundItem == null)
            return null;
        // Add the category name;
        foundItem = foundItem.clone();
        foundItem.setCategoryName(categories.get(foundItem.getCategoryId()));
        return foundItem;
    }

    /**
     * Verify the fields of a To Do item are set properly
     * when inserting or updating.
     *
     * @param item the To Do item to check
     *
     * @throws IllegalArgumentException if any fields are invalid
     * @throws SQLException if the item&rsquo;s category ID is not
     * found in the categories table
     */
    private void checkToDoFields(ToDoItem item)
            throws IllegalArgumentException, SQLException {
        if (item.isEncrypted()) {
            if ((item.getEncryptedDescription() == null) ||
                    (item.getEncryptedDescription().length == 0))
                throw new IllegalArgumentException(
                        "Description must be encrypted");
        } else {
            if (StringUtils.isEmpty(item.getDescription()))
                throw new IllegalArgumentException(
                        "Description cannot be empty");
        }
        if (!categories.containsKey(item.getCategoryId()))
            throw new SQLiteConstraintException(String.format(
                    "Category ID %d does not exist", item.getCategoryId()));
        if (item.getCreateTime() == null)
            item.setCreateTimeNow();
        if (item.getModTime() == null)
            item.setModTimeNow();
    }

    /**
     * Clone a To Do item for storage in the items table.
     * This ensures that fields which aren&rsquo;t
     * stored in the real database are empty.
     *
     * @param item the original To Do item passed in
     *
     * @return a clean clone of the item
     */
    private ToDoItem cloneForStorage(ToDoItem item) {
        ToDoItem itemClone = item.clone();
        itemClone.setCategoryName(null);
        if (itemClone.isEncrypted()) {
            itemClone.setDescription(null);
            itemClone.setNote(null);
        } else {
            itemClone.setEncryptedDescription(null);
            itemClone.setEncryptedNote(null);
        }
        return itemClone;
    }

    @Override
    public synchronized ToDoItem insertItem(@NonNull ToDoItem item)
            throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".insertItem(%s)", item));
        checkToDoFields(item);
        // Allow setting the ID for inserts, used when importing data.
        if (item.getId() != null) {
            if (itemTable.containsKey(item.getId()))
                throw new IllegalArgumentException(String.format(
                        "To Do item ID %d already exists", item.getId()));
            if (item.getId() >= nextItemId)
                nextItemId = item.getId() + 1;
        } else {
            item.setId(nextItemId++);
        }
        ToDoItem itemClone = cloneForStorage(item);
        itemTable.put(item.getId(), itemClone);
        if (transactionLevel <= 0)
            notifyObservers();
        // Ensure the category name is set
        item.setCategoryName(categories.get(item.getCategoryId()));
        return item;
    }

    @Override
    public synchronized ToDoItem updateItem(@NonNull ToDoItem item)
        throws IllegalArgumentException, SQLException {
        Log.d(TAG, String.format(".updateItem(%s)", item));
        if (item.getId() == null)
            throw new IllegalArgumentException("Missing item ID");
        if (!itemTable.containsKey(item.getId()))
            throw new SQLException("No rows matched item " + item.getId());
        checkToDoFields(item);
        // We replace the item in-place since it's
        // easier than updating the fields.
        itemTable.put(item.getId(), cloneForStorage(item));
        if (transactionLevel <= 0)
            notifyObservers();
        return item;
    }

    @Override
    public synchronized void updateAlarmNotificationTime(
            long itemId, @NonNull Instant notificationTime) {
        Log.d(TAG, String.format(".updateAlarmNotificationTime(%d, %s)",
                itemId, notificationTime));
        if (notificationTime == null)
            throw new IllegalArgumentException(
                    "Notification time cannot be null");
        if (!itemTable.containsKey(itemId))
            return;
        ToDoAlarm alarm = itemTable.get(itemId).getAlarm();
        if (alarm == null) {
            /*
             * It's possible the user disabled the alarm after it went off.
             * Normally the database should ignore notification time
             * updates after the alarm is cleared, but that should be
             * invisible since we don't return the notification time
             * without an alarm and the next time the alarm is set
             * should clear the notification time anyway.
             */
            return;
        }
        alarm.setNotificationTime(notificationTime);
    }

    @Override
    public synchronized boolean deleteItem(long itemId) {
        Log.d(TAG, String.format(".deleteItem(%d)", itemId));
        if (!itemTable.containsKey(itemId))
            return false;
        itemTable.remove(itemId);
        if (transactionLevel <= 0)
            notifyObservers();
        return true;
    }

    @Override
    public synchronized boolean deleteAllItems() {
        Log.d(TAG, ".deleteAllItems");
        if (itemTable.isEmpty())
            return false;
        itemTable.clear();
        if (transactionLevel <= 0)
            notifyObservers();
        return true;
    }

    @Override
    public synchronized void runInTransaction(@NonNull Runnable callback) {
        Log.d(TAG, String.format(".runInTransaction(%s)",
                callback.getClass().getCanonicalName()));

        // In order to support a rollback, copy our current data
        Map<Long,String> originalCategories = new HashMap<>(categories);
        long originalNextCategoryId = nextCategoryId;
        Map<String,ToDoMetadata> originalMetadata = new HashMap<>();
        for (ToDoMetadata metadatum : metadata.values())
            originalMetadata.put(metadatum.getName(), metadatum.clone());
        long originalNextMetadataId = nextMetadataId;
        Map<Long,ToDoItem> originalItems = new HashMap<>(itemTable.size());
        for (ToDoItem item : itemTable.values())
            originalItems.put(item.getId(), item.clone());
        long originalNextItemId = nextItemId;

        boolean nestedTransaction = (transactionLevel > 0);
        try {
            transactionLevel++;
            callback.run();
            if (!nestedTransaction)
                notifyObservers();
        } catch (RuntimeException e) {
            Log.e(TAG, "Exception thrown from transaction operation;"
                    + " rolling back the mock repository.", e);
            categories.clear();
            categories.putAll(originalCategories);
            nextCategoryId = originalNextCategoryId;
            metadata.clear();
            metadata.putAll(originalMetadata);
            nextMetadataId = originalNextMetadataId;
            itemTable.clear();
            itemTable.putAll(originalItems);
            nextItemId = originalNextItemId;
            throw e;
        } finally {
            --transactionLevel;
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
