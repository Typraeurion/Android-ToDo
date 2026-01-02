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
package com.xmission.trevin.android.todo.data;

import org.apache.commons.lang3.RandomStringUtils;
import static org.junit.Assert.*;

import com.xmission.trevin.android.todo.data.repeat.RepeatInterval;
import com.xmission.trevin.android.todo.data.repeat.RepeatType;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Random;

/**
 * Tests the {@link ToDoItem} data class.  This is mostly just an
 * object for storing fields, but because of the large number of
 * fields we want to ensure its basic {@code toString},
 * {@code hashCode}, and {@code equals} methods cover all known fields.
 *
 * @author Trevin Beattie
 */
public class ToDoItemTests {

    /** Random number generator for some tests */
    static final Random RAND = new Random();
    /** Random string generator for use in the tests */
    static final RandomStringUtils STRING_GEN = RandomStringUtils.insecure();

    /**
     * The defaults for the created and modified timestamps
     * are the current time, which can mess up tests that expect
     * new objects to all have the same data.  This method
     * creates a new ToDoItem with these timestamps explicitly
     * set to the Epoch (0).
     */
    private static ToDoItem newEmptyItem() {
        ToDoItem item = new ToDoItem();
        item.setCreateTime(0);
        item.setModTime(0);
        return item;
    }

    /**
     * Most tests compare a completely empty item (fields either
     * {@code null} or set to defaults) with an item that has the
     * field in question set.  This is the empty item.
     * <b>Must not be modified by any test case!</b>
     */
    private final static ToDoItem EMPTY_ITEM = newEmptyItem();

    /**
     * Some tests will use a To Do item with everything in it
     * (no {@code null} values).  This generates an item with all its
     * fields (that we know of) set to random values.
     * <p>
     *     Note that this invalidates the dichotomy of the encrypted
     *     vs. unencrypted fields in some cases; normally only one
     *     of each pair will be set, but this method sets both.
     * </p>
     */
    private static ToDoItem randomItem() {
        ToDoItem item = newEmptyItem();
        item.setId(RAND.nextLong(1000000000L) + 1L);
        item.setDescription(STRING_GEN.nextAlphanumeric(
                RAND.nextInt(16) + 8));
        item.setEncryptedDescription(new byte[RAND.nextInt(64) + 32]);
        RAND.nextBytes(item.getEncryptedDescription());
        item.setCreateTime(RAND.nextLong());
        item.setModTime(RAND.nextLong());
        item.setDue(RAND.nextLong());
        item.setCompleted(RAND.nextLong());
        item.setChecked(!EMPTY_ITEM.isChecked()); // We can only toggle a boolean
        item.setPriority(RAND.nextInt(10) + 2); // Default is 1
        item.setPrivate(RAND.nextInt(5) + 1); // Default is 0
        item.setCategoryId(RAND.nextInt(100) + 1); // Default is 0 (Unfiled)
        item.setCategoryName(STRING_GEN.nextAlphabetic(
                RAND.nextInt(10) + 5));
        item.setNote(STRING_GEN.nextAlphanumeric(
                RAND.nextInt(128) + 16));
        item.setEncryptedNote(new byte[RAND.nextInt(128) + 32]);
        RAND.nextBytes(item.getEncryptedNote());

        ToDoAlarm alarm = new ToDoAlarm();
        item.setAlarm(alarm);

        // No single repeat interval uses all repeat fields, but
        // for the purpose of this test we don't need to go that deep.
        // We do rely on repeat type ID's numbering consecutively from 0.
        RepeatInterval repeat = RepeatType.newInstance(
                RAND.nextInt(RepeatType.values().length), LocalDate.now());
        item.setRepeatInterval(repeat);

        item.setHideDaysEarlier(RAND.nextInt(10) + 1);
        return item;
    }

    @Test
    public void testEmptyToString() {
        // For this test, just make sure we're not using
        // Java's default toString implementation.
        String representation = EMPTY_ITEM.toString();
        assertFalse("Custom toString() method not used; got: "
                + representation, representation.matches(
                ToDoItem.class.getSimpleName() + "@[0-9a-f]+"));
    }

    @Test
    public void testEmptyHashCode() {
        // Since we don't know what the hash code of an empty
        // item should be, just verify it doesn't crash.
        EMPTY_ITEM.hashCode();
    }

    @Test
    public void testEmptyEquals() {
        assertTrue("Default item does not equal itself",
                EMPTY_ITEM.equals(EMPTY_ITEM));
    }

    @Test
    public void testEmptyClone() {
        ToDoItem clone = EMPTY_ITEM.clone();
        assertEquals("Clone of empty item", EMPTY_ITEM, clone);
    }

    @Test
    public void testIdToString() {
        ToDoItem itemWithId = newEmptyItem();
        itemWithId.setId(RAND.nextLong(1000000000L) + 1L);
        assertNotEquals("Items with or without ID",
                EMPTY_ITEM.toString(), itemWithId.toString());
    }

    @Test
    public void testIdHashCode() {
        ToDoItem itemWithId = newEmptyItem();
        itemWithId.setId(RAND.nextLong(1000000000L) + 1L);
        assertNotEquals("Item hash codes with or without ID",
                EMPTY_ITEM.hashCode(), itemWithId.hashCode());
        ToDoItem item2 = newEmptyItem();
        do {
            item2.setId(RAND.nextLong(1000000000) + 1L);
        } while (item2.getId() == itemWithId.getId());
        assertNotEquals("Item hash codes with different ID's",
                itemWithId.hashCode(), item2.hashCode());
    }

    @Test
    public void testIdEquals() {
        ToDoItem itemWithId = newEmptyItem();
        itemWithId.setId(RAND.nextLong(1000000000L) + 1L);
        assertTrue("Items with an ID do not compare equal",
                itemWithId.equals(itemWithId));
        assertFalse("Items with or without ID compare equal",
                EMPTY_ITEM.equals(itemWithId));
        ToDoItem item2 = newEmptyItem();
        item2.setId(itemWithId.getId());
        assertTrue("Items with the same ID do not compare equal",
                itemWithId.equals(item2));
        do {
            item2.setId(RAND.nextLong(1000000000L) + 1L);
        } while (item2.getId() == itemWithId.getId());
        assertFalse("Items with different ID's compare equal",
                itemWithId.equals(item2));
    }

    @Test
    public void testIdClone() {
        ToDoItem itemWithId = newEmptyItem();
        itemWithId.setId(RAND.nextLong(1000000000L) + 1L);
        ToDoItem clone = itemWithId.clone();
        assertEquals("Clone of item ID",
                itemWithId.getId(), clone.getId());
    }

    @Test
    public void testDescriptionToString() {
        ToDoItem itemWithDescription = newEmptyItem();
        itemWithDescription.setDescription(STRING_GEN.nextAlphanumeric(
                RAND.nextInt(20) + 10));
        assertNotEquals("Items with or without Description",
                EMPTY_ITEM.toString(), itemWithDescription.toString());
    }

    @Test
    public void testDescriptionHashCode() {
        ToDoItem itemWithDescription = newEmptyItem();
        itemWithDescription.setDescription(STRING_GEN.nextAlphanumeric(
                        RAND.nextInt(20) + 10));
        assertNotEquals("Item hash codes with or without Description",
                EMPTY_ITEM.hashCode(), itemWithDescription.hashCode());
        ToDoItem item2 = newEmptyItem();
        item2.setDescription(itemWithDescription.getDescription());
        assertEquals("Item hash codes with the same description",
                itemWithDescription.hashCode(), item2.hashCode());
        do {
            item2.setDescription(STRING_GEN.nextAlphanumeric(
                    RAND.nextInt(20) + 10));
        } while (StringUtils.equals(item2.getDescription(),
                itemWithDescription.getDescription()));
        assertNotEquals("Item hash codes with different descriptions",
                itemWithDescription.hashCode(), item2.hashCode());
    }

    @Test
    public void testDescriptionEquals() {
        ToDoItem itemWithDescription = newEmptyItem();
        itemWithDescription.setDescription(STRING_GEN.nextAlphanumeric(
                        RAND.nextInt(20) + 10));
        assertFalse("Items with or without description compare equal",
                itemWithDescription.equals(EMPTY_ITEM));
        ToDoItem item2 = newEmptyItem();
        item2.setDescription(itemWithDescription.getDescription());
        assertTrue("Items with same description do not compare equal",
                itemWithDescription.equals(item2));
        do {
                item2.setDescription(STRING_GEN.nextAlphanumeric(
                        RAND.nextInt(20) + 10));
        } while (StringUtils.equals(item2.getDescription(),
                itemWithDescription.getDescription()));
        assertFalse("Items with different descriptions compare equal",
                itemWithDescription.equals(item2));
    }

    @Test
    public void testDescriptionClone() {
        ToDoItem itemWithDescription = newEmptyItem();
        itemWithDescription.setDescription(STRING_GEN.nextAlphanumeric(
                        RAND.nextInt(20) + 10));
        ToDoItem clone = itemWithDescription.clone();
        assertEquals("Clone of item description",
                itemWithDescription.getDescription(), clone.getDescription());
    }

    @Test
    public void testEncryptedDescriptionToString() {
        ToDoItem itemWithEncryptedDescription = newEmptyItem();
        itemWithEncryptedDescription.setEncryptedDescription(
                new byte[RAND.nextInt(32) + 8]);
        RAND.nextBytes(itemWithEncryptedDescription.getEncryptedDescription());
        assertNotEquals("Items with or without Encrypted Description",
                EMPTY_ITEM.toString(), itemWithEncryptedDescription.toString());
    }

    @Test
    public void testEncryptedDescriptionHashCode() {
        ToDoItem itemWithEncryptedDescription = newEmptyItem();
        itemWithEncryptedDescription.setEncryptedDescription(
                new byte[RAND.nextInt(32) + 8]);
        RAND.nextBytes(itemWithEncryptedDescription.getEncryptedDescription());
        assertNotEquals("Item hash codes with or without encrypted description",
                EMPTY_ITEM.hashCode(), itemWithEncryptedDescription.hashCode());
        ToDoItem item2 = newEmptyItem();
        item2.setEncryptedDescription(
                itemWithEncryptedDescription.getEncryptedDescription());
        assertEquals("Item hash codes with the same encrypted description",
                itemWithEncryptedDescription.hashCode(), item2.hashCode());
        item2.setEncryptedDescription(new byte[RAND.nextInt(32) + 8]);
        do {
            RAND.nextBytes(item2.getEncryptedDescription());
        } while (Arrays.equals(item2.getEncryptedDescription(),
                itemWithEncryptedDescription.getEncryptedDescription()));
        assertNotEquals("Item hash codes with different encrypted descriptions",
                itemWithEncryptedDescription.hashCode(), item2.hashCode());
    }

    @Test
    public void testEncryptedDescriptionEquals() {
        ToDoItem itemWithEncryptedDescription = newEmptyItem();
        itemWithEncryptedDescription.setEncryptedDescription(
                new byte[RAND.nextInt(32) + 8]);
        assertFalse("Items with or without encrypted description compare equal",
                itemWithEncryptedDescription.equals(EMPTY_ITEM));
        ToDoItem item2 = newEmptyItem();
        item2.setEncryptedDescription(
                itemWithEncryptedDescription.getEncryptedDescription());
        assertTrue("Items with same encrypted description do not compare equal",
                itemWithEncryptedDescription.equals(item2));
        item2.setEncryptedDescription(
                new byte[RAND.nextInt(32) + 8]);
        do {
            RAND.nextBytes(item2.getEncryptedDescription());
        } while (Arrays.equals(item2.getEncryptedDescription(),
                itemWithEncryptedDescription.getEncryptedDescription()));
        assertFalse("Items with different encrypted descriptions compare equal",
                itemWithEncryptedDescription.equals(item2));
    }

    @Test
    public void testEncryptedDescriptionClone() {
        ToDoItem itemWithEncryptedDescription = newEmptyItem();
        itemWithEncryptedDescription.setEncryptedDescription(
                new byte[RAND.nextInt(32) + 8]);
        RAND.nextBytes(itemWithEncryptedDescription.getEncryptedDescription());
        ToDoItem clone = itemWithEncryptedDescription.clone();
        assertArrayEquals("Clone of item encrypted description",
                itemWithEncryptedDescription.getEncryptedDescription(),
                clone.getEncryptedDescription());
    }

    @Test
    public void testCreateTimeToString() {
        ToDoItem itemWithCreateTime = newEmptyItem();
        itemWithCreateTime.setCreateTime(RAND.nextLong());
        assertNotEquals("Items with or without create time",
                EMPTY_ITEM.toString(), itemWithCreateTime.toString());
    }

    @Test
    public void testCreateTimeHashCode() {
        ToDoItem itemWithCreateTime = newEmptyItem();
        itemWithCreateTime.setCreateTime(RAND.nextLong());
        assertNotEquals("Item hash codes with or without create time",
                EMPTY_ITEM.hashCode(), itemWithCreateTime.hashCode());
        ToDoItem item2 = newEmptyItem();
        do {
            item2.setCreateTime(RAND.nextLong());
        } while (item2.getCreateTime() == itemWithCreateTime.getCreateTime());
        assertNotEquals("Item hash codes with different create times",
                itemWithCreateTime.hashCode(), item2.hashCode());
    }

    @Test
    public void testCreateTimeEquals() {
        ToDoItem itemWithCreateTime = newEmptyItem();
        itemWithCreateTime.setCreateTime(RAND.nextLong());
        assertTrue("Items with a create time do not compare equal",
                itemWithCreateTime.equals(itemWithCreateTime));
        assertFalse("Items with or without a create time compare equal",
                EMPTY_ITEM.equals(itemWithCreateTime));
        ToDoItem item2 = newEmptyItem();
        item2.setCreateTime(itemWithCreateTime.getCreateTime());
        assertTrue("Items with same create time do not compare equal",
                itemWithCreateTime.equals(item2));
        do {
            item2.setCreateTime(RAND.nextLong());
        } while (item2.getCreateTime() == itemWithCreateTime.getCreateTime());
        assertFalse("Items with different create times compare equal",
                itemWithCreateTime.equals(item2));
    }

    @Test
    public void testCreateTimeClone() {
        ToDoItem itemWithCreateTime = newEmptyItem();
        itemWithCreateTime.setCreateTime(RAND.nextLong());
        ToDoItem clone = itemWithCreateTime.clone();
        assertEquals("Clone of item create time",
                itemWithCreateTime.getCreateTime(), clone.getCreateTime());
    }

    @Test
    public void testModTimeToString() {
        ToDoItem itemWithModTime = newEmptyItem();
        itemWithModTime.setModTime(RAND.nextLong());
        assertNotEquals("Items with or without modification time",
                EMPTY_ITEM.toString(), itemWithModTime.toString());
    }

    @Test
    public void testModTimeHashCode() {
        ToDoItem itemWithModTime = newEmptyItem();
        itemWithModTime.setModTime(RAND.nextLong());
        assertNotEquals("Item hash codes with or without modification time",
                EMPTY_ITEM.hashCode(), itemWithModTime.hashCode());
        ToDoItem item2 = newEmptyItem();
        do {
            item2.setModTime(RAND.nextLong());
        } while (item2.getModTime() == itemWithModTime.getModTime());
        assertNotEquals("Item hash codes with different modification times",
                itemWithModTime.hashCode(), item2.hashCode());
    }

    @Test
    public void testModTimeEquals() {
        ToDoItem itemWithModTime = newEmptyItem();
        itemWithModTime.setModTime(RAND.nextLong());
        assertTrue("Items with a modification time do not compare equal",
                itemWithModTime.equals(itemWithModTime));
        assertFalse("Items with or without a modification time compare equal",
                EMPTY_ITEM.equals(itemWithModTime));
        ToDoItem item2 = newEmptyItem();
        item2.setModTime(itemWithModTime.getModTime());
        assertTrue("Items with same modification time do not compare equal",
                itemWithModTime.equals(item2));
        do {
            item2.setModTime(RAND.nextLong());
        } while (item2.getModTime() == itemWithModTime.getModTime());
        assertFalse("Items with different modification times compare equal",
                itemWithModTime.equals(item2));
    }

    @Test
    public void testModTimeClone() {
        ToDoItem itemWithModTime = newEmptyItem();
        itemWithModTime.setModTime(RAND.nextLong());
        ToDoItem clone = itemWithModTime.clone();
        assertEquals("Clone of item modification time",
                itemWithModTime.getModTime(), clone.getModTime());
    }

    @Test
    public void testDueToString() {
        ToDoItem itemWithDue = newEmptyItem();
        itemWithDue.setDue(RAND.nextLong());
        assertNotEquals("Items with or without due time",
                EMPTY_ITEM.toString(), itemWithDue.toString());
    }

    @Test
    public void testDueHashCode() {
        ToDoItem itemWithDue = newEmptyItem();
        itemWithDue.setDue(RAND.nextLong());
        assertNotEquals("Item hash codes with or without due time",
                EMPTY_ITEM.hashCode(), itemWithDue.hashCode());
        ToDoItem item2 = newEmptyItem();
        do {
            item2.setDue(RAND.nextLong());
        } while (item2.getDue() == itemWithDue.getDue());
        assertNotEquals("Item hash codes with different due times",
                itemWithDue.hashCode(), item2.hashCode());
    }

    @Test
    public void testDueEquals() {
        ToDoItem itemWithDue = newEmptyItem();
        itemWithDue.setDue(RAND.nextLong());
        assertTrue("Items with a due time do not compare equal",
                itemWithDue.equals(itemWithDue));
        assertFalse("Items with or without due time compare equal",
                EMPTY_ITEM.equals(itemWithDue));
        ToDoItem item2 = newEmptyItem();
        item2.setDue(itemWithDue.getDue());
        assertTrue("Items with the same due time do not compare equal",
                itemWithDue.equals(item2));
        do {
            item2.setDue(RAND.nextLong());
        } while (item2.getDue() == itemWithDue.getDue());
        assertFalse("Items with different due times compare equal",
                itemWithDue.equals(item2));
    }

    @Test
    public void testDueClone() {
        ToDoItem itemWithDue = newEmptyItem();
        itemWithDue.setDue(RAND.nextLong());
        ToDoItem clone = itemWithDue.clone();
        assertEquals("Clone of item due time",
                itemWithDue.getDue(), clone.getDue());
    }

    @Test
    public void testCompletedToString() {
        ToDoItem itemWithCompleted = newEmptyItem();
        itemWithCompleted.setCompleted(RAND.nextLong());
        assertNotEquals("Items with or without completed time",
                EMPTY_ITEM.toString(), itemWithCompleted.toString());
    }

    @Test
    public void testCompletedHashCode() {
        ToDoItem itemWithCompleted = newEmptyItem();
        itemWithCompleted.setCompleted(RAND.nextLong());
        assertNotEquals("Item hash codes with or without completed time",
                EMPTY_ITEM.hashCode(), itemWithCompleted.hashCode());
        ToDoItem item2 = newEmptyItem();
        do {
            item2.setCompleted(RAND.nextLong());
        } while (item2.getCompleted() == itemWithCompleted.getCompleted());
        assertNotEquals("Item hash codes with different completed times",
                itemWithCompleted.hashCode(), item2.hashCode());
    }

    @Test
    public void testCompletedEquals() {
        ToDoItem itemWithCompleted = newEmptyItem();
        itemWithCompleted.setCompleted(RAND.nextLong());
        assertTrue("Items with a completed time do not compare equal",
                itemWithCompleted.equals(itemWithCompleted));
        assertFalse("Item with or without completed time compare equal",
                EMPTY_ITEM.equals(itemWithCompleted));
        ToDoItem item2 = newEmptyItem();
        item2.setCompleted(itemWithCompleted.getCompleted());
        assertTrue("Items with same completed time do not compare equal",
                itemWithCompleted.equals(item2));
        do {
            item2.setCompleted(RAND.nextLong());
        } while (item2.getCompleted() == itemWithCompleted.getCompleted());
        assertFalse("Items with different completed times compare equal",
                itemWithCompleted.equals(item2));
    }

    @Test
    public void testCompletedClone() {
        ToDoItem itemWithCompleted = newEmptyItem();
        itemWithCompleted.setCompleted(RAND.nextLong());
        ToDoItem clone = itemWithCompleted.clone();
        assertEquals("Clone of item completed time",
                itemWithCompleted.getCompleted(), clone.getCompleted());
    }

    @Test
    public void testCheckedToString() {
        ToDoItem itemChecked = newEmptyItem();
        itemChecked.setChecked(!EMPTY_ITEM.isChecked()); // Boolean value can only be toggled
        assertNotEquals("Items with different checked states",
                EMPTY_ITEM.toString(), itemChecked.toString());
    }

    @Test
    public void testCheckedHashCode() {
        ToDoItem itemChecked = newEmptyItem();
        itemChecked.setChecked(!EMPTY_ITEM.isChecked());
        assertNotEquals("Item hash codes with different checked states",
                EMPTY_ITEM.hashCode(), itemChecked.hashCode());
    }

    @Test
    public void testCheckedEquals() {
        ToDoItem itemChecked = newEmptyItem();
        itemChecked.setChecked(!EMPTY_ITEM.isChecked());
        assertTrue("Items both checked do not compare equal",
                itemChecked.equals(itemChecked));
        assertFalse("Items with different checked states compare equal",
                EMPTY_ITEM.equals(itemChecked));
    }

    @Test
    public void testCheckedClone() {
        ToDoItem itemChecked = newEmptyItem();
        itemChecked.setChecked(!EMPTY_ITEM.isChecked());
        ToDoItem clone = itemChecked.clone();
        assertEquals("Clone of item checked state",
                itemChecked.isChecked(), clone.isChecked());
    }

    @Test
    public void testPriorityToString() {
        ToDoItem itemOtherPriority = newEmptyItem();
        itemOtherPriority.setPriority(RAND.nextInt(10) + 2);
        assertNotEquals("Items with different priority",
                EMPTY_ITEM.toString(), itemOtherPriority.toString());
    }

    @Test
    public void testPriorityHashCode() {
        ToDoItem itemOtherPriority = newEmptyItem();
        itemOtherPriority.setPriority(RAND.nextInt(10) + 2);
        assertNotEquals("Item hash codes with different priority",
                EMPTY_ITEM.hashCode(), itemOtherPriority.hashCode());
    }

    @Test
    public void testPriorityEquals() {
        ToDoItem itemOtherPriority = newEmptyItem();
        itemOtherPriority.setPriority(RAND.nextInt(10) + 2);
        assertFalse("Item equals item with different priority",
                EMPTY_ITEM.equals(itemOtherPriority));
    }

    @Test
    public void testPriorityClone() {
        ToDoItem itemOtherPriority = newEmptyItem();
        itemOtherPriority.setPrivate(RAND.nextInt(10) + 2);
        ToDoItem clone = itemOtherPriority.clone();
        assertEquals("Clone of item priority",
                itemOtherPriority.getPriority(), clone.getPriority());
    }

    @Test
    public void testPrivateToString() {
        ToDoItem privateItem = newEmptyItem();
        privateItem.setPrivate(RAND.nextInt(5) + 1);
        assertNotEquals("Items with different privacy",
                EMPTY_ITEM.toString(), privateItem.toString());
    }

    @Test
    public void testPrivateHashCode() {
        ToDoItem privateItem = newEmptyItem();
        privateItem.setPrivate(RAND.nextInt(5) + 1);
        assertNotEquals("Item hash codes with different privacy",
                EMPTY_ITEM.hashCode(), privateItem.hashCode());
    }

    @Test
    public void testPrivateEquals() {
        ToDoItem privateItem = newEmptyItem();
        privateItem.setPrivate(RAND.nextInt(5) + 1);
        assertFalse("Item equals item with different privacy",
                EMPTY_ITEM.equals(privateItem));
    }

    @Test
    public void testPrivateClone() {
        ToDoItem privateItem = newEmptyItem();
        privateItem.setPrivate(RAND.nextInt(5) + 1);
        ToDoItem clone = privateItem.clone();
        assertEquals("Clone of item privacy",
                privateItem.getPrivate(), clone.getPrivate());
    }

    @Test
    public void testCategoryIdToString() {
        ToDoItem itemOtherCategory = newEmptyItem();
        itemOtherCategory.setCategoryId(RAND.nextInt(100) + 1);
        assertNotEquals("Items with different Category ID",
                EMPTY_ITEM.toString(), itemOtherCategory.toString());
    }

    @Test
    public void testCategoryIdHashCode() {
        ToDoItem itemOtherCategory = newEmptyItem();
        itemOtherCategory.setCategoryId(RAND.nextInt(100) + 1);
        assertNotEquals("Item hash codes with different Category ID",
                EMPTY_ITEM.hashCode(), itemOtherCategory.hashCode());
    }

    @Test
    public void testCategoryIdEquals() {
        ToDoItem itemOtherCategory = newEmptyItem();
        itemOtherCategory.setCategoryId(RAND.nextInt(100) + 1);
        assertFalse("Items with different category ID's compare equal",
                EMPTY_ITEM.equals(itemOtherCategory));
    }

    @Test
    public void testCategoryIdClone() {
        ToDoItem itemOtherCategory = newEmptyItem();
        itemOtherCategory.setCategoryId(RAND.nextInt(100) + 1);
        ToDoItem clone = itemOtherCategory.clone();
        assertEquals("Clone of item category ID",
                itemOtherCategory.getCategoryId(), clone.getCategoryId());
    }

    @Test
    public void testCategoryNameToString() {
        ToDoItem itemWithCategoryName = newEmptyItem();
        itemWithCategoryName.setCategoryName(STRING_GEN.nextAlphabetic(
                RAND.nextInt(10) + 5));
        assertNotEquals("Items with or without category name",
                EMPTY_ITEM.toString(), itemWithCategoryName.toString());
    }

    @Test
    public void testCategoryNameHashCode() {
        ToDoItem itemWithCategoryName = newEmptyItem();
        itemWithCategoryName.setCategoryName(STRING_GEN.nextAlphabetic(
                RAND.nextInt(10) + 5));
        assertNotEquals("Item hash codes with or without category name",
                EMPTY_ITEM.hashCode(), itemWithCategoryName.hashCode());
        ToDoItem item2 = newEmptyItem();
        do {
            item2.setCategoryName(STRING_GEN.nextAlphabetic(
                    RAND.nextInt(10) + 5));
        } while (StringUtils.equals(item2.getCategoryName(),
                itemWithCategoryName.getCategoryName()));
        assertNotEquals("Item hash codes with different category names",
                itemWithCategoryName.hashCode(), item2.hashCode());
    }

    @Test
    public void testCategoryNameEquals() {
        ToDoItem itemWithCategoryName = newEmptyItem();
        itemWithCategoryName.setCategoryName(STRING_GEN.nextAlphabetic(
                        RAND.nextInt(10) + 5));
        assertTrue("Items with a category name do not compare equal",
                itemWithCategoryName.equals(itemWithCategoryName));
        assertFalse("Items with or without a category name compare equal",
                EMPTY_ITEM.equals(itemWithCategoryName));
        ToDoItem item2 = newEmptyItem();
        item2.setCategoryName(itemWithCategoryName.getCategoryName());
        assertTrue("Items with the same category name do not compare equal",
                itemWithCategoryName.equals(item2));
        do {
            item2.setCategoryName(STRING_GEN.nextAlphabetic(
                    RAND.nextInt(10) + 5));
        } while (StringUtils.equals(item2.getCategoryName(),
                itemWithCategoryName.getCategoryName()));
        assertFalse("Items with different category names compare equal",
                itemWithCategoryName.equals(item2));
    }

    @Test
    public void testCategoryNameClone() {
        ToDoItem itemWithCategoryName = newEmptyItem();
        itemWithCategoryName.setCategoryName(STRING_GEN.nextAlphabetic(
                        RAND.nextInt(10) + 5));
        ToDoItem clone = itemWithCategoryName.clone();
        assertEquals("Clone of item category name",
                itemWithCategoryName.getCategoryName(), clone.getCategoryName());
    }

    @Test
    public void testNoteToString() {
        ToDoItem itemWithNote = newEmptyItem();
        itemWithNote.setNote(STRING_GEN.nextAlphanumeric(
                RAND.nextInt(200) + 10));
        assertNotEquals("Items with or without a note",
                EMPTY_ITEM.toString(), itemWithNote.toString());
    }

    @Test
    public void testNoteHashCode() {
        ToDoItem itemWithNote = newEmptyItem();
        itemWithNote.setNote(STRING_GEN.nextAlphanumeric(
                        RAND.nextInt(200) + 10));
        assertNotEquals("Item hash codes with or without a note",
                EMPTY_ITEM.hashCode(), itemWithNote.hashCode());
        ToDoItem item2 = newEmptyItem();
        do {
            item2.setNote(STRING_GEN.nextAlphanumeric(
                    RAND.nextInt(200) + 10));
        } while (StringUtils.equals(item2.getNote(), itemWithNote.getNote()));
        assertNotEquals("Item hash codes with different notes",
                itemWithNote.hashCode(), item2.hashCode());
    }

    @Test
    public void testNoteEquals() {
        ToDoItem itemWithNote = newEmptyItem();
        itemWithNote.setNote(STRING_GEN.nextAlphanumeric(
                        RAND.nextInt(200) + 10));
        assertTrue("Items with a note do not compare equal",
                itemWithNote.equals(itemWithNote));
        assertFalse("Items with or without a note compare equal",
                EMPTY_ITEM.equals(itemWithNote));
        ToDoItem item2 = newEmptyItem();
        item2.setNote(itemWithNote.getNote());
        assertTrue("Items with the same note do not compare equal",
                itemWithNote.equals(item2));
        do {
            item2.setNote(STRING_GEN.nextAlphanumeric(
                    RAND.nextInt(200) + 10));
        } while (StringUtils.equals(item2.getNote(), itemWithNote.getNote()));
        assertFalse("Items with different notes compare equal",
                itemWithNote.equals(item2));
    }

    @Test
    public void testNoteClone() {
        ToDoItem itemWithNote = newEmptyItem();
        itemWithNote.setNote(STRING_GEN.nextAlphanumeric(
                        RAND.nextInt(200) + 10));
        ToDoItem clone = itemWithNote.clone();
        assertEquals("Clone of item note", itemWithNote.getNote(),
                clone.getNote());
    }

    @Test
    public void testEncryptedNoteToString() {
        ToDoItem itemWithEncryptedNote = newEmptyItem();
        itemWithEncryptedNote.setEncryptedNote(
                new byte[RAND.nextInt(128) + 16]);
        RAND.nextBytes(itemWithEncryptedNote.getEncryptedNote());
        assertNotEquals("Items with or without Encrypted Note",
                EMPTY_ITEM.toString(), itemWithEncryptedNote.toString());
    }

    @Test
    public void testEncryptedNoteHashCode() {
        ToDoItem itemWithEncryptedNote = newEmptyItem();
        itemWithEncryptedNote.setEncryptedNote(
                new byte[RAND.nextInt(128) + 16]);
        RAND.nextBytes(itemWithEncryptedNote.getEncryptedNote());
        assertNotEquals("Item hash codes with or without Encrypted Note",
                EMPTY_ITEM.hashCode(), itemWithEncryptedNote.hashCode());
        ToDoItem item2 = newEmptyItem();
        item2.setEncryptedNote(new byte[RAND.nextInt(128) + 16]);
        do {
            RAND.nextBytes(item2.getEncryptedNote());
        } while (Arrays.equals(item2.getEncryptedNote(),
                itemWithEncryptedNote.getEncryptedNote()));
        assertNotEquals("Item hash codes with different encrypted notes",
                itemWithEncryptedNote.hashCode(), item2.hashCode());
    }

    @Test
    public void testEncryptedNoteEquals() {
        ToDoItem itemWithEncryptedNote = newEmptyItem();
        itemWithEncryptedNote.setEncryptedNote(
                new byte[RAND.nextInt(128) + 16]);
        RAND.nextBytes(itemWithEncryptedNote.getEncryptedNote());
        assertTrue("Items with an encrypted note do not compare equal",
                itemWithEncryptedNote.equals(itemWithEncryptedNote));
        assertFalse("Items with or without encrypted note compare equal",
                EMPTY_ITEM.equals(itemWithEncryptedNote));
        ToDoItem item2 = newEmptyItem();
        item2.setEncryptedNote(itemWithEncryptedNote.getEncryptedNote());
        assertTrue("Items with same encrypted note do not compare equal",
                itemWithEncryptedNote.equals(item2));
        item2.setEncryptedNote(new byte[RAND.nextInt(128) + 16]);
        do {
            RAND.nextBytes(itemWithEncryptedNote.getEncryptedNote());
        } while (Arrays.equals(item2.getEncryptedNote(),
                itemWithEncryptedNote.getEncryptedNote()));
        assertFalse("Items with different encrypted notes compare equal",
                itemWithEncryptedNote.equals(item2));
    }

    @Test
    public void testEncryptedNoteClone() {
        ToDoItem itemWithEncryptedNote = newEmptyItem();
        itemWithEncryptedNote.setEncryptedNote(
                new byte[RAND.nextInt(128) + 16]);
        RAND.nextBytes(itemWithEncryptedNote.getEncryptedNote());
        ToDoItem clone = itemWithEncryptedNote.clone();
        assertArrayEquals("Clone of item encrypted note",
                itemWithEncryptedNote.getEncryptedNote(),
                clone.getEncryptedNote());
    }

    @Test
    public void testAlarmToString() {
        ToDoItem itemWithAlarm = newEmptyItem();
        ToDoAlarm alarm = new ToDoAlarm(LocalTime.ofSecondOfDay(
                RAND.nextInt(86400)), RAND.nextInt(7));
        itemWithAlarm.setAlarm(alarm);
        assertNotEquals("Items with or without Alarm",
                EMPTY_ITEM.toString(), itemWithAlarm.toString());
    }

    @Test
    public void testAlarmHashCode() {
        ToDoItem itemWithAlarm = newEmptyItem();
        ToDoAlarm alarm = new ToDoAlarm(LocalTime.ofSecondOfDay(
                RAND.nextInt(86400)), RAND.nextInt(7));
        itemWithAlarm.setAlarm(alarm);
        assertNotEquals("Item hash codes with or without an alarm",
                EMPTY_ITEM.hashCode(), itemWithAlarm.hashCode());
        ToDoItem item2 = newEmptyItem();
        ToDoAlarm alarm2 = new ToDoAlarm(LocalTime.ofSecondOfDay(
                RAND.nextInt(86400)), RAND.nextInt(7));
        alarm2.setNotificationTime(Instant.ofEpochSecond(RAND.nextInt(86400)));
        item2.setAlarm(alarm2);
        assertNotEquals("Item hash codes with different alarms",
                itemWithAlarm.hashCode(), item2.hashCode());
    }

    @Test
    public void testAlarmEquals() {
        ToDoItem itemWithAlarm = newEmptyItem();
        ToDoAlarm alarm = new ToDoAlarm(LocalTime.ofSecondOfDay(
                RAND.nextInt(86400)), RAND.nextInt(7));
        itemWithAlarm.setAlarm(alarm);
        assertTrue("Items with an alarm do not compare equal",
                itemWithAlarm.equals(itemWithAlarm));
        assertFalse("Items with or without an alarm compare equal",
                EMPTY_ITEM.equals(itemWithAlarm));
        ToDoItem item2 = newEmptyItem();
        item2.setAlarm(alarm);
        assertTrue("Items with the same alarm do not compare equal",
                itemWithAlarm.equals(item2));
        ToDoAlarm alarm2 = new ToDoAlarm(LocalTime.ofSecondOfDay(
                RAND.nextInt(86400)), RAND.nextInt(7));
        alarm2.setNotificationTime(Instant.ofEpochSecond(RAND.nextInt(86400)));
        item2.setAlarm(alarm2);
        assertFalse("Items with different alarms compare equal",
                itemWithAlarm.equals(item2));
    }

    @Test
    public void testRepeatIntervalToString() {
        ToDoItem itemWithRepeat = newEmptyItem();
        itemWithRepeat.setRepeatInterval(RepeatType.newInstance(
                RAND.nextInt(RepeatType.values().length),
                LocalDate.ofEpochDay(RAND.nextInt(100000))));
        assertNotEquals("Items with or without a repeat",
                EMPTY_ITEM.toString(), itemWithRepeat.toString());
    }

    @Test
    public void testRepeatIntervalHashCode() {
        ToDoItem itemWithRepeat = newEmptyItem();
        itemWithRepeat.setRepeatInterval(RepeatType.newInstance(
                RAND.nextInt(RepeatType.values().length),
                LocalDate.ofEpochDay(RAND.nextInt(100000))));
        assertNotEquals("Item hash codes with or without a repeat",
                EMPTY_ITEM.hashCode(), itemWithRepeat.hashCode());
        ToDoItem item2 = newEmptyItem();
        do {
            item2.setRepeatInterval(RepeatType.newInstance(
                    RAND.nextInt(RepeatType.values().length),
                    LocalDate.ofEpochDay(RAND.nextInt(100000))));
        } while (item2.getRepeatInterval().equals(
                itemWithRepeat.getRepeatInterval()));
        assertNotEquals("Item hash codes with different repeat intervals",
                itemWithRepeat.hashCode(), item2.hashCode());
    }

    @Test
    public void testRepeatIntervalEquals() {
        ToDoItem itemWithRepeat = newEmptyItem();
        itemWithRepeat.setRepeatInterval(RepeatType.newInstance(
                RAND.nextInt(RepeatType.values().length),
                LocalDate.ofEpochDay(RAND.nextInt(100000))));
        assertTrue("Items with a repeat do not compare equal",
                itemWithRepeat.equals(itemWithRepeat));
        assertFalse("Items with or without repeat interval compare equal",
                EMPTY_ITEM.equals(itemWithRepeat));
        ToDoItem item2 = newEmptyItem();
        item2.setRepeatInterval(itemWithRepeat.getRepeatInterval());
        assertTrue("Items with the same repeat do not compare equal",
                itemWithRepeat.equals(item2));
        do {
            item2.setRepeatInterval(RepeatType.newInstance(
                    RAND.nextInt(RepeatType.values().length),
                    LocalDate.ofEpochDay(RAND.nextInt(100000))));
        } while (item2.getRepeatInterval().equals(
                itemWithRepeat.getRepeatInterval()));
        assertNotEquals("Items with different repeats compare equal",
                itemWithRepeat.equals(item2));
    }

    @Test
    public void testRepeatIntervalClone() {
        ToDoItem itemWithRepeat = newEmptyItem();
        itemWithRepeat.setRepeatInterval(RepeatType.newInstance(
                RAND.nextInt(RepeatType.values().length),
                LocalDate.ofEpochDay(RAND.nextInt(100000))));
        ToDoItem clone = itemWithRepeat.clone();
        assertEquals("Clone of item repeat interval",
                itemWithRepeat.getRepeatInterval(), clone.getRepeatInterval());
    }

    @Test
    public void testHideDaysEarlierToString() {
        ToDoItem itemWithHide = newEmptyItem();
        itemWithHide.setHideDaysEarlier(RAND.nextInt(7));
        assertNotEquals("Items with or without hide days earlier",
                EMPTY_ITEM.toString(), itemWithHide.toString());
    }

    @Test
    public void testHideDaysEarlierHashCode() {
        ToDoItem itemWithHide = newEmptyItem();
        itemWithHide.setHideDaysEarlier(RAND.nextInt(7) + 1);
        assertNotEquals("Item hash codes with or without hide days earlier",
                EMPTY_ITEM.hashCode(), itemWithHide.hashCode());
        ToDoItem item2 = newEmptyItem();
        do {
            item2.setHideDaysEarlier(RAND.nextInt(7) + 1);
        } while (item2.getHideDaysEarlier() == itemWithHide.getHideDaysEarlier());
        assertNotEquals("Item hash codes with different hide days earlier",
                itemWithHide.hashCode(), item2.hashCode());
    }

    @Test
    public void testHideDaysEarlierEquals() {
        ToDoItem itemWithHide = newEmptyItem();
        itemWithHide.setHideDaysEarlier(RAND.nextInt(7));
        assertTrue("Items with hide days earlier do not compare equal",
                itemWithHide.equals(itemWithHide));
        assertFalse("Items with or without hide days earlier compare equal",
                EMPTY_ITEM.equals(itemWithHide));
        ToDoItem item2 = newEmptyItem();
        item2.setHideDaysEarlier(itemWithHide.getHideDaysEarlier());
        assertTrue("Items with same hide days earlier do not compare equal",
                itemWithHide.equals(item2));
        do {
            item2.setHideDaysEarlier(RAND.nextInt(7));
        } while (item2.getHideDaysEarlier() == itemWithHide.getHideDaysEarlier());
        assertFalse("Items with different hide days earlier compare equal",
                itemWithHide.equals(item2));
    }

    @Test
    public void testHideDaysEarlierClone() {
        ToDoItem itemWithHide = newEmptyItem();
        itemWithHide.setHideDaysEarlier(RAND.nextInt(7));
        ToDoItem clone = itemWithHide.clone();
        assertEquals("Clone of hide days earlier",
                itemWithHide.getHideDaysEarlier(), clone.getHideDaysEarlier());
    }

    @Test
    public void testFullClone() {
        ToDoItem original = randomItem();
        ToDoItem clone = original.clone();
        assertEquals("Clone of full item", original, clone);
    }

}
