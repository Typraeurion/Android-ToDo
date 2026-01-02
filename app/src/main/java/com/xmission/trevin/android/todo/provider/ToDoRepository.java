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

import android.content.Context;
import android.database.DataSetObserver;
import android.database.SQLException;
import androidx.annotation.NonNull;

import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.ToDoMetadata;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.service.PasswordChangeService;

import java.util.List;

/**
 * Higher-level interface to the SQLite database for the To Do app.
 * All methods in this class should <i>not</i> be called from the
 * UI thread of the application.
 *
 * @author Trevin Beattie
 */
public interface ToDoRepository {

    /**
     * Open the database for a given context.  The database will be held open
     * until all contexts using it call the {@link #release(Context)} method.
     *
     * @param context the context in which to connect to the database
     *
     * @throws SQLException if we fail to connect to the database
     */
    void open(@NonNull Context context) throws SQLException;

    /**
     * Release the database for a given context.  If this is the last
     * context holding the database, the database connection is closed.
     *
     * @param context the context which no longer needs this repository
     */
    void release(@NonNull Context context);

    /**
     * Count the number of categories in the category table.
     * This will include the pre-populated &ldquo;Unfiled&rdquo;
     * category, but not the &ldquo;All&rdquo; pseudo-category.
     *
     * @return the number of categories in the database
     */
    int countCategories();

    /**
     * Get all of the categories available from the database.
     * This will include the pre-populated &ldquo;Unfiled&rdquo;
     * category.  The categories will be sorted alphabetically.
     *
     * @return a List of categories
     */
    List<ToDoCategory> getCategories();

    /**
     * Get a single category by its ID.
     *
     * @param categoryId the ID of the category to return
     *
     * @return the category if found, or {@code null}
     * if there is no category with that ID.
     */
    ToDoCategory getCategoryById(long categoryId);

    /**
     * Add a new category.
     *
     * @param categoryName the name of the category to add
     *
     * @return the newly added category
     *
     * @throws IllegalArgumentException if {@code categoryName} is empty
     * @throws SQLException if we failed to insert the category
     */
    ToDoCategory insertCategory(@NonNull String categoryName)
            throws IllegalArgumentException, SQLException;

    /**
     * Add a new category, including its pre-determined ID.
     * This is meant for use by the importer service where
     * category ID's should be preserved.
     *
     * @param category the category to add
     *
     * @return the newly added category
     *
     * @throws IllegalArgumentException if {@code categoryName} is empty
     * @throws SQLException if we failed to insert the category
     */
    ToDoCategory insertCategory(@NonNull ToDoCategory category)
            throws IllegalArgumentException, SQLException;

    /**
     * Rename a category.  If the &ldquo;Unfiled&rdquo; category ID
     * ({@value ToDoCategory#UNFILED}) is given, the {@code newName}
     * parameter is ignored; the name will be taken from the string resources.
     *
     * @param categoryId the ID of the category to change
     * @param newName the new name of the category
     *
     * @return the modified category
     *
     * @throws IllegalArgumentException if {@code newName} is empty
     * @throws SQLException if we failed to update the category
     */
    ToDoCategory updateCategory(long categoryId, @NonNull String newName)
        throws IllegalArgumentException, SQLException;

    /**
     * Delete a category.  This will refuse to delete the
     * &ldquo;Unfiled&rdquo; category ({@value ToDoCategory#UNFILED}).
     * Any To Do items which are using the given category will be reassigned
     * to &ldquo;Unfiled&rdquo;.
     *
     * @param categoryId the ID of the category to delete
     *
     * @return {@code true} if the category was deleted,
     * {@code false} if the category did not exist.
     *
     * @throws IllegalArgumentException if {@code categoryId} is
     * ({@value ToDoCategory#UNFILED})
     * @throws SQLException if we failed to delete the category
     * or update any of its To Do items
     */
    boolean deleteCategory(long categoryId)
            throws IllegalArgumentException, SQLException;

    /**
     * Delete <i>all</i> categories except &ldquo;Unfiled&rdquo;.
     * All To Do items will be reassigned to the &ldquo;Unfiled&rdquo;
     * category.
     *
     * @return {@code true} if any categories were deleted, {@code false}
     * if there were no categories other than &ldquo;Unfiled&rdquo;.
     *
     * @throws SQLException if we failed to delete the categories
     * or update the To Do items
     */
    boolean deleteAllCategories() throws SQLException;

    /**
     * Count the number of metadata in the metadata table.
     *
     * @return the number of metadata in the database
     */
    int countMetadata();

    /**
     * Get all of the metadata from the database.
     * The data will be ordered by name.
     *
     * @return the To Do app metadata
     */
    List<ToDoMetadata> getMetadata();

    /**
     * Get a single metadatum by name.
     *
     * @param key the name of the metadata to return
     *
     * @return the metadata if found, or {@code null} if there is no
     * metadata with the given {@code key}.
     */
    ToDoMetadata getMetadataByName(@NonNull String key);

    /**
     * Get a single metadatum by ID.  There shouldn&rsquo;t be a use case
     * for this, but it is provided for completeness.
     *
     * @param id the ID of the metadata to return
     *
     * @return the metadata if found, or {@code null} if there is no
     * metadata with the given {@code id}.
     */
    ToDoMetadata getMetadataById(long id);

    /**
     * Add or modify metadata.
     *
     * @param name the name of the metadata to set
     * @param value the value to set for the metadata
     *
     * @return the metadata that was added or changed
     *
     * @throws IllegalArgumentException if {@code name} is empty
     * @throws SQLException if we failed to insert or update the metadata
     */
    ToDoMetadata upsertMetadata(@NonNull String name, @NonNull byte[] value)
            throws IllegalArgumentException, SQLException;

    /**
     * Delete metadata.
     *
     * @param name the name of the metadata to remove
     *
     * @return {@code true} if the metadata was deleted,
     * {@code false} if the metedata did not exist.
     *
     * @throws IllegalArgumentException if {@code name} is empty
     * @throws SQLException if we failed to delete the metadata
     */
    boolean deleteMetadata(@NonNull String name)
        throws IllegalArgumentException, SQLException;

    /**
     * Delete metadata by its ID.  There is no use case for this,
     * but it is provided for completeness.
     *
     * @param id the database ID of the metadata
     *
     * @return {@code true} if the metadata was deleted,
     * {@code false} if the metedata did not exist.
     *
     * @throws IllegalArgumentException if {@code name} is empty
     * @throws SQLException if we failed to delete the metadata
     */
    boolean deleteMetadataById(long id)
            throws IllegalArgumentException, SQLException;

    /**
     * Delete <i>all</i> metadata.
     *
     * @return {@code true} if any metadata was deleted, {@code false}
     * if there was no metadata.
     *
     * @throws SQLException if we failed to delete the metadata
     */
    boolean deleteAllMetadata() throws SQLException;

    /**
     * Count the number of To Do items in the database.
     *
     * @return the number of To Do items in the database
     */
    int countItems();

    /**
     * Count the number of To Do items in the database for a given category.
     *
     * @param categoryId the ID of the category whose items to count,
     * or {@link ToDoPreferences#ALL_CATEGORIES}) to caunt all items.
     *
     * @return the number of items in the category
     */
    int countItemsInCategory(long categoryId);

    /**
     * Count the number of private To Do items in the databes.
     *
     * @return the number of private items in the database
     */
    int countPrivateItems();

    /**
     * Count the number of encrypted To Do items in the database.
     *
     * @return the number of encrypted items in the database
     */
    int countEncryptedItems();

    /**
     * Get a cursor over To Do items matching the given selection criteria.
     *
     * @param categoryId the ID of the category whose items to include,
     * or {@link ToDoPreferences#ALL_CATEGORIES}) to include all items.
     * @param includePrivate whether to include private items.
     * @param includeEncrypted whether to include encrypted items.
     * If {@code true}, {@code includePrivate} must also be {@code true}.
     * The provider will <i>not</i> decrypt the items; decryption must be
     * done by the UI.
     * @param sortOrder the order in which to return matching items,
     *                  expressed as one or more field names optionally
     *                  followed by &ldquo;desc&rdquo; suitable for use
     *                  as the object of an {@code ORDER BY} clause.
     *
     * @return a cursor for retrieving the To Do items
     */
    ToDoCursor getItems(long categoryId, boolean includePrivate,
                        boolean includeEncrypted, String sortOrder);

    /**
     * Get a list of ID&rsquo;s of private To Do items.  This is exclusively
     * meant for use by the {@link PasswordChangeService} to select items
     * whose encryption needs changing.
     *
     * @return an array of item ID&rsquo;s.
     */
    long[] getPrivateItemIds();

    /**
     * Get a single To Do item by its ID.
     *
     * @param itemId the ID of the item to return
     *
     * @return the item if found, or {@code null} if there is no item
     * with that ID.
     */
    ToDoItem getItemById(long itemId);

    /**
     * Add a new To Do item.  The item&rsquo;s category will be set by its
     * {@code categoryId}, <i>not</i> its {@code categoryName}.  If the item
     * is encrypted, its {@code encryptedDescription} and (if applicable)
     * {@code encryptedNote} fields <i>must</i> already be set to the
     * encrypted values of {@code description} and {@code note} respectively;
     * any text in {@code description} and {@code note} will be ignored.
     *
     * @param item the To Do item to add
     *
     * @return the newly added item.  This will be the same as the
     * {@code item} parameter that was passed in but will have its
     * {@code id} field set.
     *
     * @throws java.lang.IllegalArgumentException if the
     * {@code description} is empty or &hellip;
     * (FIXME: add other validation constraints)
     * @throws SQLException if we failed to insert the To Do item
     */
    ToDoItem insertItem(@NonNull ToDoItem item)
        throws IllegalArgumentException, SQLException;

    /**
     * Modify an existing To Do item.  The item&rsquo;s category will be set
     * by its {@code categoryId}, <i>not</i> its {@code categoryName}.
     * If the item is encrypted, its {@code encryptedDescription} and
     * (if applicable) {@code encryptedNote} fields <i>must</i> already be
     * set to the encrypted values of {@code description} and {@code note}
     * respectively; any text in {@code description} and {@code note} will
     * be ignored.
     *
     * @param item the To Do item to change
     *
     * @return the updated item.  This will be the same as the
     * {@code item} parameter that was passed in
     *
     * @throws java.lang.IllegalArgumentException if the
     * {@code description} is empty or &hellip;
     * (FIXME: add other validation constraints)
     * @throws SQLException if we failed to update the To Do item
     */
    ToDoItem updateItem(@NonNull ToDoItem item)
        throws IllegalArgumentException, SQLException;

    /**
     * Delete a To Do item.
     *
     * @param itemId the ID of the item to delete
     *
     * @return {@code true} if the item was deleted,
     * {@code false} if there was no item with that ID.
     *
     * @throws SQLException if we failed to delete the item
     */
    boolean deleteItem(long itemId) throws SQLException;

    /**
     * Purge <i>all</i> To Do items.
     *
     * @return {@code true} if any items were deleted, {@code false}
     * if there were no items.
     *
     * @throws SQLException if we failed to delete any items
     */
    boolean deleteAllItems() throws SQLException;

    /**
     * Run an operation within a database transaction.
     * The repository ensures that no other database operations
     * will be called while the transaction is in progress.
     * The transaction will be committed if the operation returns
     * normally; if it throws an (uncaught) exception, the transaction
     * will be rolled back.
     *
     * @param callback the operation to do.
     */
    void runInTransaction(@NonNull Runnable callback);

    /**
     * Register an observer that is called when To Do data changes.
     * Due to the nature of this app, whether the data is included in
     * this particular cursor is not taken into account; <i>any</i>
     * data change will result in a callback.
     *
     * @param observer the object that gets notified when To Do
     * data changes.
     */
    void registerDataSetObserver(@NonNull DataSetObserver observer);

    /**
     * Unregister an observer that has previously been registered
     * with this cursor via {@link #registerDataSetObserver}.
     *
     * @param observer the object to unregister
     */
    void unregisterDataSetObserver(@NonNull DataSetObserver observer);

}
