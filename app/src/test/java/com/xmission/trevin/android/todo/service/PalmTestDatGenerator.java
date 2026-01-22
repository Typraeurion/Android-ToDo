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
package com.xmission.trevin.android.todo.service;

import static com.xmission.trevin.android.todo.service.PalmImportWorker.*;
import static org.junit.Assert.*;

import androidx.annotation.Nullable;

import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.service.PalmImportWorker.CategoryEntry;
import com.xmission.trevin.android.todo.service.PalmImportWorker.ToDoEntry;

import java.io.*;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.Test;

/**
 * Helper class for generating test data to use in
 * {@code PalmImportWorkerTests}.
 */
public class PalmTestDatGenerator {

    /** The character encoding used in Palm Desktop data files */
    public static Charset DAT_ENCODING = Charset.forName("Cp1252");

    /**
     * Timestamp to use for date fields where the date should be unset.
     * <b>Warning:</b> This is only a few years away from the current
     * date of this implementation, due to the limitation of 32-bit time; see:
     * &ldquo;<a href"https://en.wikipedia.org/wiki/Year_2038_problem">Year
     * 2038 Problem</a>&rdquo;.
     */
    public static final long UNSET_DATE = ToDoEntry.MAX_DATE + 25199;

    /**
     * Common method for writing the &lqduo;.dat&rdquo; files.
     *
     * @param out the output file to write
     * @param usePalmSG whether the Palm data should be wrapped
     * in a Palm Desktop Structured data file header.
     * @param useTD20 whether to use the version 2 {@code true}
     * or version 1 {@code false} header format.
     * @param saveFile the name of the save file to include
     * in the header data
     * @param categories a {@link List} of the {@code CategoryEntry}
     * records to write
     * @param todos a {@link List} of the {@code ToDoEntry}
     * records to write
     *
     * @throws IOException if there was an error writing the file
     */
    public static void writeDatFile(File out,
                                    boolean usePalmSG,
                                    boolean useTD20,
                                    String saveFile,
                                    List<CategoryEntry> categories,
                                    List<ToDoEntry> todos)
            throws IOException {

        try (DataOutputStream outStream = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(out)))) {

            if (usePalmSG) {
                writeInteger(outStream, PalmImportWorker.MAGIC);
                writeString(outStream, "PalmSG Database");
                outStream.writeBytes("00RV");
            }

            if (useTD20) {
                writeInteger(outStream, PalmImportWorker.TD20_MAGIC);
                writeZeroes(outStream, 12);
                writeInteger(outStream, 0x1165);
                writeZeroes(outStream, 8);
            } else {
                writeInteger(outStream, PalmImportWorker.TD10_MAGIC);
            }

            writeString(outStream, saveFile);

            if (useTD20) {
                writeZeroes(outStream, 43); // Lots of unknowns
            } else {
                writeString(outStream, "");   // "custom show header" (?)
            }

            int nextCategoryId = 1;
            for (CategoryEntry category : categories) {
                if (category.ID >= nextCategoryId)
                    nextCategoryId = category.ID + 1;
            }
            writeInteger(outStream, nextCategoryId);

            writeCategories(outStream, categories);
            writeSchemaMetadata(outStream, useTD20);
            writeRecords(outStream, todos, useTD20);
        }
    }

    /**
     * Write out category data to the output file
     *
     * @param outStream the output file to write
     * @param categories a {@link List} of the {@code CategoryEntry}
     * records to write out
     *
     * @throws IOException if there was an error writing to the file
     */
    private static void writeCategories(DataOutputStream outStream,
                                        List<CategoryEntry> categories)
            throws IOException {
        writeInteger(outStream, categories.size());
        for (CategoryEntry category : categories) {
            writeInteger(outStream, category.index);
            writeInteger(outStream, category.ID);
            writeInteger(outStream, category.dirty);
            writeString(outStream, category.longName);
            writeString(outStream, category.shortName);
        }
    }

    /**
     * Determine the field index of a To Do record field.
     *
     * @param fields the array of known fields for a given
     * To Do data file version
     * @param targetField the ID of the field to look for
     *
     * @return the index of the field, or -1 if not found
     */
    private static int indexOfField(int[] fields, int targetField) {
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] == targetField)
                return i;
        }
        return -1;
    }

    /**
     * Write out the database schema to the output file.
     *
     * @param outStream the output file to write
     * @param useTD20 whether we are writing a version 2 data file
     *
     * @throws IOException if there was an error writing to the file
     */
    private static void writeSchemaMetadata(DataOutputStream outStream,
                                            boolean useTD20)
            throws IOException {
        writeInteger(outStream, 44); // To Do: find an official source describing this value
        writeInteger(outStream, useTD20 ? ToDoEntry.expectedFieldTypesV2.length
                : ToDoEntry.expectedFieldTypesV1.length);
        int[] fields = useTD20 ? ToDoEntry.fieldOrderV2
                : ToDoEntry.fieldOrderV1;
        writeInteger(outStream, indexOfField(fields, ToDoEntry.FIELD_ID));
        writeInteger(outStream, indexOfField(fields, ToDoEntry.FIELD_STATUS));
        writeInteger(outStream, indexOfField(fields, ToDoEntry.FIELD_POSITION));
        writeShort(outStream, useTD20 ? ToDoEntry.expectedFieldTypesV2.length
                : ToDoEntry.expectedFieldTypesV1.length);
        if (useTD20) {
            for (int type : ToDoEntry.expectedFieldTypesV2)
                writeShort(outStream, type);
        } else {
            for (int type : ToDoEntry.expectedFieldTypesV1)
                writeShort(outStream, type);
        }
    }

    /**
     * Write out To Do records to the output file
     *
     * @param outStream the output file to write
     * @param todos a {@link List} of the {@code ToDoEntry}
     * records to write out
     *
     * @throws IOException if there was an error writing to the file
     */
    private static void writeRecords(DataOutputStream outStream,
                                     List<ToDoEntry> todos,
                                     boolean useTD20)
            throws IOException {
        int index = 0;
        int[] types = useTD20 ? ToDoEntry.expectedFieldTypesV2
                : ToDoEntry.expectedFieldTypesV1;
        int[] fields = useTD20 ? ToDoEntry.fieldOrderV2
                : ToDoEntry.fieldOrderV1;
        writeInteger(outStream, todos.size() * fields.length);
        for (ToDoEntry todo : todos) {
            for (int i = 0; i < fields.length; i++) {
                writeInteger(outStream, types[i]);
                switch (fields[i]) {
                    case ToDoEntry.FIELD_ID:
                        writeInteger(outStream, todo.ID);
                        break;
                    case ToDoEntry.FIELD_PLUSK_INDEX:
                        writeInteger(outStream, 0x1000 + index);
                        break;
                    case ToDoEntry.FIELD_STATUS:
                        writeInteger(outStream, (todo.status == null)
                                ? 0 : todo.status);
                        break;
                    case ToDoEntry.FIELD_OFFSET:
                        writeInteger(outStream, index * fields.length);
                        break;
                    case ToDoEntry.FIELD_POSITION:
                        writeInteger(outStream, useTD20 ? 1 : Integer.MAX_VALUE);
                        break;
                    case ToDoEntry.FIELD_CATEGORY:
                        writeInteger(outStream, (todo.categoryIndex == null)
                                ? UNFILED.index : todo.categoryIndex);
                        break;
                    case ToDoEntry.FIELD_PRIVATE:
                        writeInteger(outStream, Boolean.TRUE.equals(
                                todo.isPrivate) ? 1 : 0);
                        break;
                    case ToDoEntry.FIELD_DESCRIPTION:
                        writeZeroes(outStream, 4);
                        writeString(outStream, todo.description);
                        break;
                    case ToDoEntry.FIELD_DUE_DATE:
                        writeInteger(outStream, (int)
                                ((todo.dueDate == null) ? UNSET_DATE
                                        : todo.dueDate.intValue()));
                        break;
                    case ToDoEntry.FIELD_COMPLETED:
                        writeInteger(outStream, Boolean.TRUE.equals(
                                todo.completed) ? 1 : 0);
                        break;
                    case ToDoEntry.FIELD_PRIORITY:
                        writeInteger(outStream, todo.priority);
                        break;
                    case ToDoEntry.FIELD_NOTE:
                        writeZeroes(outStream, 4);
                        writeString(outStream, (todo.note == null)
                                ? "" : todo.note);
                        break;
                    case ToDoEntry.FIELD_REPEAT_AFTER_COMPLETE:
                        writeInteger(outStream, Boolean.TRUE.equals(
                                todo.repeatAfterCompleted) ? 1 : 0);
                        break;
                    case ToDoEntry.FIELD_COMPLETION_DATE:
                        writeInteger(outStream, ((int)
                                ((todo.completionDate == null) ? UNSET_DATE
                                        : todo.completionDate.intValue())));
                        break;
                    case ToDoEntry.FIELD_HAS_ALARM:
                        writeInteger(outStream, Boolean.TRUE.equals(
                                todo.hasAlarm) ? 1 : 0);
                        break;
                    case ToDoEntry.FIELD_ALARM_TIME:
                        writeInteger(outStream, (int)
                                ((todo.alarmTime == null)
                                        ? -1 : todo.alarmTime.intValue()));
                        break;
                    case ToDoEntry.FIELD_ALARM_DAYS_IN_ADVANCE:
                        writeInteger(outStream,
                                (todo.alarmDaysInAdvance == null)
                                        ? -1 : todo.alarmDaysInAdvance);
                        break;
                    case ToDoEntry.FIELD_REPEAT:
                        writeRepeatEvent(outStream, todo.repeat, useTD20);
                        break;

                    case ToDoEntry.FIELD_UNKNOWN:
                    default:
                        switch (types[i]) {
                            case TYPE_INTEGER:
                            case TYPE_UNKNOWN40:
                            case TYPE_UNKNOWN41:
                            case TYPE_UNKNOWN42:
                            case TYPE_UNKNOWN43:
                                writeInteger(outStream, 0);
                                break;
                            case TYPE_REPEAT:
                                writeRepeatEvent(outStream,
                                        todo.repeat, useTD20);
                                break;
                            case TYPE_BOOLEAN:
                                writeInteger(outStream, 0);
                                break;
                            case TYPE_DATE:
                                writeInteger(outStream, (int) UNSET_DATE);
                                break;
                            case TYPE_CSTRING:
                                writeZeroes(outStream, 4);
                                writeString(outStream, "");
                                break;
                        }
                }
            }
        }
    }

    /**
     * Write out a repeat event structure to the output file.
     * This is a complex data type.
     *
     * @param outStream the output file to write
     * @param repeat the {@link RepeatEvent} containing repeat data
     * (may be {@code null})
     * @param useTD20 whether this is a version 2 output file.
     * Version 2 data includes repeat type names.
     *
     * @throws IOException if there was an error writing to the file
     */
    private static void writeRepeatEvent(
            DataOutputStream outStream,
            @Nullable RepeatEvent repeat,
            boolean useTD20)
            throws IOException {
        writeZeroes(outStream, 2);
        if (repeat == null) {
            writeZeroes(outStream, 2);
            return;
        }
        if (useTD20) {
            writeShort(outStream, -1);
            writeShort(outStream, 1);
            writeLongString(outStream, repeat.typeName);
        } else {
            writeShort(outStream, 0);
            fail("Repeats are not supported in Palm To Do database version 1");
        }
        writeInteger(outStream, repeat.type);
        writeInteger(outStream, repeat.interval);
        writeInteger(outStream, (int) repeat.repeatUntil);
        writeZeroes(outStream, 4);
        switch (repeat.type) {
            case RepeatEvent.TYPE_REPEAT_BY_DAY:
                writeInteger(outStream, repeat.dayOfWeek);
                break;
            case RepeatEvent.TYPE_REPEAT_BY_WEEK:
                writeInteger(outStream, 1);
                writeByte(outStream, repeat.dayOfWeekBitmap);
                break;
            case RepeatEvent.TYPE_REPEAT_BY_MONTH_DATE:
                writeInteger(outStream, repeat.dateOfMonth);
                break;
            case RepeatEvent.TYPE_REPEAT_BY_MONTH_DAY:
                writeInteger(outStream, repeat.dayOfWeek);
                writeInteger(outStream, repeat.weekOfMonth);
                break;
            case RepeatEvent.TYPE_REPEAT_BY_YEAR:
                writeInteger(outStream, repeat.dateOfMonth);
                writeInteger(outStream, repeat.monthOfYear);
                break;
            default:
                fail("Unknown repeat type: " + repeat.type);
        }
    }

    /**
     * Write a single byte to the output file.
     *
     * @param outStream the output file to write to
     * @param b the byte to write
     *
     * @throws IOException if there was an error writing to the file
     */
    private static void writeByte(DataOutputStream outStream, byte b)
            throws IOException {
        outStream.write(b);
    }

    /**
     * Write a 2-byte unsigned number to the output file.
     * The number is written in little-endian order.
     *
     * @param outStream the output file to write to
     * @param n the number to write
     *
     * @throws IOException if there was an error writing to the file
     */
    private static void writeShort(DataOutputStream outStream, int n)
        throws IOException {
        byte[] data = new byte[] {
                (byte) (n & 0xff),
                (byte) ((n >> 8) & 0xff)
        };
        outStream.write(data);
    }

    /**
     * Write a 4-byte unsigned number to the output file.
     * The number is written in little-endian order.
     *
     * @param outStream the output file to write to
     * @param n the number to write
     *
     * @throws IOException if there was an error writing to the file
     */
    private static void writeInteger(DataOutputStream outStream, int n)
        throws IOException {
        byte[] data = new byte[] {
                (byte) (n & 0xff),
                (byte) ((n >> 8) & 0xff),
                (byte) ((n >> 16) & 0xff),
                (byte) ((n >> 24) & 0xff)
        };
        outStream.write(data);
    }

    /**
     * Write an arbitrary number of zero bytes to the output file.
     * Used to pad the file with reserved or unknown data.
     *
     * @param outStream the output file to write to
     * @param length the number of 0 bytes to write
     *
     * @throws IOException if there was an error writing to the file
     */
    private static void writeZeroes(DataOutputStream outStream, int length)
        throws IOException {
        byte[] data = new byte[length];
        outStream.write(data);
    }

    /**
     * Write a character sequence to the output file.
     * The first byte contains the length if less than 255.
     * If the first byte is 255, the second two bytes contain the length.
     *
     * @param outStream the output file to write to
     * @param s the string to write
     *
     * @throws IOException if there was an error writing to the file
     */
    private static void writeString(DataOutputStream outStream, String s)
        throws IOException {
        byte[] data = s.getBytes(DAT_ENCODING);
        if (data.length < 0xff)
            outStream.writeByte(data.length);
        else {
            outStream.writeByte(0xff);
            writeShort(outStream, data.length);
        }
        outStream.write(data);
    }

    /**
     * Write a text to the given file, which is assumed to be long.
     * The first two bytes contain the length of the string.
     *
     * @param outStream the output file to write to
     * @param s the string to write
     *
     * @throws IOException if there was an error writing to the file
     */
    private static void writeLongString(DataOutputStream outStream, String s)
        throws IOException {
        byte[] data = s.getBytes(DAT_ENCODING);
        writeShort(outStream, data.length);
        outStream.write(data);
    }

    /**
     * The &ldquo;Unfiled&rdquo; category entry.
     * This is implicit; it is never stored in the data files.
     */
    public static final CategoryEntry UNFILED;
    static {
        UNFILED = new CategoryEntry();
        UNFILED.index = 0;
        UNFILED.ID = ToDoCategory.UNFILED;
        UNFILED.dirty = 0;
        UNFILED.shortName = "Unfiled";
        UNFILED.longName = "Unfiled";
    }

    /**
     * Generate a file with no To Do records.
     * The importer should fail with the message
     * &ldquo;No records were imported&rdquo;.
     */
    //Test
    public void generateEmptyFile() throws IOException {

        File testFile = File.createTempFile("Palm-empty-v1-", ".dat");

        writeDatFile(testFile, false, false, "/dev/null",
                Collections.emptyList(), Collections.emptyList());

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Empty V1 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generate a file with a very simple To Do record.
     * This is used to test file header parsing.
     */
    //Test
    public void generateMinimalV1File() throws IOException {

        File testFile = File.createTempFile("Palm-minimal_v1-", ".dat");

        ToDoEntry todo = new ToDoEntry();
        todo.ID = 1;
        todo.description = "Test task";
        todo.priority = 1;

        writeDatFile(testFile, false, false,
                "C:\\Program Files\\Palm\\User\\todo\\todo.dat",
                Collections.emptyList(), Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Minimal V1 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generate a V2 file with a simple To Do record.
     * This is used to test file header parsing.
     */
    //Test
    public void generateMinimalV2File() throws IOException {

        File testFile = File.createTempFile("Palm-minimal_v2-", ".dat");

        ToDoEntry todo = new ToDoEntry();
        todo.ID = 1;
        todo.description = "Test task";
        todo.priority = 1;

        writeDatFile(testFile, false, true,
                "C:\\Program Files\\Palm\\User\\todo\\todo.dat",
                Collections.emptyList(), Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Minimal V2 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generates a Palm Structured data-wrapped V1 file with
     * a simple To Do record.  This is used to test file header parsing.
     */
    //Test
    public void generateMinimalSGV1File() throws IOException {

        File testFile = File.createTempFile("Palm-minimal_SGv1-", ".dat");

        ToDoEntry todo = new ToDoEntry();
        todo.ID = 1;
        todo.description = "Test task";
        todo.priority = 1;

        writeDatFile(testFile, true, false,
                "C:\\Program Files\\Palm\\User\\todo\\todo.dat",
                Collections.emptyList(), Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Minimal PalmSG-wrapped V1 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generate a Palm Structured data-wrapped V2 file
     * with a simple To Do record.  This is used to test file header parsing.
     */
    //Test
    public void generateMinimalSGV2File() throws IOException {

        File testFile = File.createTempFile("Palm-minimal_SGv2-", ".dat");

        ToDoEntry todo = new ToDoEntry();
        todo.ID = 1;
        todo.description = "Test task";
        todo.priority = 1;

        writeDatFile(testFile, true, true,
                "C:\\Program Files\\Palm\\User\\todo\\todo.dat",
                Collections.emptyList(), Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Minimal PalmSG-wrapped V2 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generate a V1 file with all supported fields in a To Do record.
     * This is used to test {@link PalmImportWorker.ToDoEntry} parsing.
     */
    //Test
    public void generateMaximalV1File() throws IOException {

        File testFile = File.createTempFile("Palm-maximal_v1-", ".dat");

        CategoryEntry category = new CategoryEntry();
        category.index = 1;
        category.ID = 96;
        category.longName = "Animals of the Jungle";
        category.shortName = "Animals";

        ToDoEntry todo = new ToDoEntry();
        todo.ID = Integer.MAX_VALUE - 1;
        todo.status = ToDoEntry.STATUS_ARCHIVE | ToDoEntry.STATUS_PENDING |
                ToDoEntry.STATUS_UPDATE | ToDoEntry.STATUS_ADD;
        todo.description = "Do all the things";
        todo.dueDate = LocalDate.of(2026, 1, 20)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.completed = true;
        todo.priority = 1000;
        todo.isPrivate = true;
        todo.categoryIndex = category.index;
        todo.note = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
                + "  Pellentesque tristique blandit mollis.  Morbi in urna"
                + " eget justo porta elementum vitae ac magna.  Morbi blandit"
                + " sem a eleifend porta.  Aenean vehicula sagittis libero,"
                + " vestibulum viverra erat porttitor eget.  Proin in"
                + " placerat arcu.  Suspendisse euismod quis orci vitae"
                + " tristique.  Phasellus eget mattis dui.  Nam nec volutpat"
                + " lacus.  Aliquam consequat aliquet nunc, nec iaculis"
                + " lectus placerat ut.  Donec nec dictum ligula.  Nulla eu"
                + " luctus justo.\r\n";

        writeDatFile(testFile, false, false,
                "C:\\Program Files\\Palm\\User\\todo\\todo.dat",
                Collections.singletonList(category),
                Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Maximal V1 file written to "
                + testFile.getAbsolutePath());

    };

    /**
     * Generate a V2 file with most of the supported scalar fields
     * in a To Do record.  This does not include the repeat entry.
     * This is used to test {@link PalmImportWorker.ToDoEntry} parsing.
     */
    //Test
    public void testV2FileWithAlarm() throws IOException {

        File testFile = File.createTempFile("Palm-alarm_SGv2-", ".dat");

        CategoryEntry category = new CategoryEntry();
        category.index = 1;
        category.ID = 1985;
        category.longName = "Amazing Stories";
        category.shortName = "Stories";

        ToDoEntry todo = new ToDoEntry();
        todo.ID = Integer.MAX_VALUE - 2;
        todo.status = ToDoEntry.STATUS_ARCHIVE | ToDoEntry.STATUS_PENDING |
                ToDoEntry.STATUS_UPDATE | ToDoEntry.STATUS_ADD;
        todo.categoryIndex = category.index;
        todo.isPrivate = true;
        todo.description = "Do all the things";
        todo.dueDate = LocalDate.of(2026, 1, 20)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.completed = true;
        todo.priority = 1000;
        todo.note = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
                + "  Sed et nisl odio.  Maecenas viverra venenatis orci, at"
                + " maximus orci malesuada sit amet.  Nulla molestie eget"
                + " mauris eget venenatis.  Praesent laoreet vel massa"
                + " vestibulum sodales.  Donec elementum nisi non nulla"
                + " feugiat dignissim.  Proin eros lorem, mattis at neque id,"
                + " rhoncus pharetra tellus.  Proin rhoncus sed velit ut"
                + " sagittis.  Integer massa diam, aliquam vitae ex eget,"
                + " commodo bibendum metus.  Orci varius natoque penatibus et"
                + " magnis dis parturient montes, nascetur ridiculus mus."
                + "  Phasellus eu libero nisl.\r\n";
        todo.completionDate = LocalDate.of(2026, 1, 15)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.hasAlarm = true;
        todo.alarmTime = LocalDate.of(1971, 1, 1)
                .atTime(5, 43, 21).toEpochSecond(ZoneOffset.UTC);
        todo.alarmDaysInAdvance = 6;

        writeDatFile(testFile, true, true,
                "C:\\Program Files\\Palm\\User\\todo\\todo.dat",
                Collections.singletonList(category),
                Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Alarming PalmSG-wrapped V2 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generate a V2 file with a To Do item with a day-of-week repeating
     * interval.  This is used to test {@link PalmImportWorker.RepeatEvent}
     * parsing.
     */
    //Test
    public void testV2FileWithRepeatOnDay() throws IOException {

        File testFile = File.createTempFile(
                "Palm-repeat_on_day_SGv2-", ".dat");

        ToDoEntry todo = new ToDoEntry();
        todo.ID = 11;
        todo.categoryIndex = UNFILED.index;
        todo.description = "Every Eighth Saturday";
        todo.dueDate = LocalDate.of(2026, 1, 24)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.priority = 101;
        todo.repeatAfterCompleted = true;
        todo.completionDate = LocalDate.of(2026, 1, 17)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.repeat = new RepeatEvent();
        todo.repeat.typeName = RepeatEvent.NAME_REPEAT_BY_DAY;
        todo.repeat.type = RepeatEvent.TYPE_REPEAT_BY_DAY;
        todo.repeat.interval = 8;
        todo.repeat.repeatUntil = LocalDate.of(2099, 12, 31)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.repeat.dayOfWeek = 6;

        writeDatFile(testFile, true, true,
                "C:\\Program Files\\Palm\\User\\todo\\todo.dat",
                Collections.emptyList(), Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Repeat on Day PalmSG-wrapped V2 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generate a V2 file with a To Do item with a weekly repeating interval.
     * This is used to test {@link PalmImportWorker.RepeatEvent} parsing.
     */
    //Test
    public void testV2FileWithRepeatByWeek() throws IOException {

        File testFile = File.createTempFile(
                "Palm-repeat_by_week_SGv2-", ".dat");

        ToDoEntry todo = new ToDoEntry();
        todo.ID = 12;
        todo.categoryIndex = UNFILED.index;
        todo.description = "Every Other Day";
        todo.dueDate = LocalDate.of(2026, 1, 20)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.priority = 102;
        todo.repeatAfterCompleted = true;
        todo.completionDate = LocalDate.of(2026, 1, 18)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.repeat = new RepeatEvent();
        todo.repeat.typeName = RepeatEvent.NAME_REPEAT_BY_WEEK;
        todo.repeat.type = RepeatEvent.TYPE_REPEAT_BY_WEEK;
        todo.repeat.interval = 2;
        todo.repeat.repeatUntil = LocalDate.of(2099, 12, 31)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.repeat.dayOfWeekBitmap = 0x7f;

        writeDatFile(testFile, true, true,
                "C:\\Program Files\\Palm\\User\\todo\\todo.dat",
                Collections.emptyList(), Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Repeat Weekly PalmSG-wrapped V2 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generate a V2 file with a To Do item with a monthly repeating interval
     * by day and week.  This is used to test
     * {@link PalmImportWorker.RepeatEvent} parsing.
     */
    //Test
    public void testV2FileWithRepeatByMonthOnDayOfWeek() throws IOException {

        File testFile = File.createTempFile(
                "Palm-repeat_monthly_on_day_SGv2-", ".dat");

        ToDoEntry todo = new ToDoEntry();
        todo.ID = 13;
        todo.categoryIndex = UNFILED.index;
        todo.description = "Every Seven Months on the Last Friday";
        todo.dueDate = LocalDate.of(2026, 2, 13)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.priority = 103;
        todo.repeatAfterCompleted = true;
        todo.completionDate = LocalDate.of(2026, 1, 13)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.repeat = new RepeatEvent();
        todo.repeat.typeName = RepeatEvent.NAME_REPEAT_BY_MONTH_DAY;
        todo.repeat.type = RepeatEvent.TYPE_REPEAT_BY_MONTH_DAY;
        todo.repeat.interval = 7;
        todo.repeat.repeatUntil = LocalDate.of(2099, 12, 31)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.repeat.dayOfWeek = 5;
        todo.repeat.weekOfMonth = 4;

        writeDatFile(testFile, true, true,
                "C:\\Program Files\\Palm\\User\\todo\\todo.dat",
                Collections.emptyList(), Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Monthly PalmSG-wrapped V2 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generate a V2 file with a To Do item with a monthly repeating interval
     * by date.  This is used to test {@link PalmImportWorker.RepeatEvent}
     * parsing.
     */
    //Test
    public void testV2FileWithRepeatByMonthOnDate() throws IOException {

        File testFile = File.createTempFile(
                "Palm-repeat_monthly_on_date_SGv2-", ".dat");

        ToDoEntry todo = new ToDoEntry();
        todo.ID = 14;
        todo.categoryIndex = UNFILED.index;
        todo.description = "Every Thirteen Months on the 13th";
        todo.dueDate = LocalDate.of(2026, 2, 13)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.priority = 104;
        todo.repeatAfterCompleted = true;
        todo.completionDate = LocalDate.of(2026, 1, 13)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.repeat = new RepeatEvent();
        todo.repeat.typeName = RepeatEvent.NAME_REPEAT_BY_MONTH_DATE;
        todo.repeat.type = RepeatEvent.TYPE_REPEAT_BY_MONTH_DATE;
        todo.repeat.interval = 13;
        todo.repeat.repeatUntil = LocalDate.of(2099, 12, 31)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.repeat.dateOfMonth = 13;

        writeDatFile(testFile, true, true,
                "C:\\Program Files\\Palm\\User\\todo\\todo.dat",
                Collections.emptyList(), Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Monthly PalmSG-wrapped V2 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generate a V2 file with a To Do item with a yearly repeating interval.
     * This is used to test {@link PalmImportWorker.RepeatEvent} parsing.
     */
    //Test
    public void testV2FileWithRepeatByYear() throws IOException {

        File testFile = File.createTempFile(
                "Palm-repeat_yearly_SGv2-", ".dat");

        ToDoEntry todo = new ToDoEntry();
        todo.ID = 15;
        todo.categoryIndex = UNFILED.index;
        todo.description = "Every Hundred Years on May 1";
        todo.dueDate = LocalDate.of(2100, 5, 1)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.priority = 105;
        todo.repeatAfterCompleted = true;
        todo.completionDate = LocalDate.of(2000, 5, 1)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.repeat = new RepeatEvent();
        todo.repeat.typeName = RepeatEvent.NAME_REPEAT_BY_YEAR;
        todo.repeat.type = RepeatEvent.TYPE_REPEAT_BY_YEAR;
        todo.repeat.interval = 100;
        todo.repeat.repeatUntil = LocalDate.of(2099, 12, 31)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        todo.repeat.dateOfMonth = 1;
        todo.repeat.monthOfYear = 4;

        writeDatFile(testFile, true, true,
                "C:\\Program Files\\Palm\\User\\todo\\todo.dat",
                Collections.emptyList(), Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Yearly PalmSG-wrapped V2 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generate a V1 file containing a category list.
     * This is used to test various modes of importing categories.
     * The file <i>must</i> contain at least one To Do item
     * in order for the import worker to consider it valid.
     */
    //Test
    public void generateCategoriesV1File() throws IOException {

        File testFile = File.createTempFile("Palm-categories-v1-", ".dat");

        List<CategoryEntry> categories = new ArrayList<>(10);

        CategoryEntry category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 1;
        category.longName = "Alpha";
        category.shortName = "A";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 2;
        category.longName = "Beta";
        category.shortName = "B";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 3;
        category.longName = "Gamma";
        category.shortName = "C";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 4;
        category.longName = "Delta";
        category.shortName = "D";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 5;
        category.longName = "Epsilon";
        category.shortName = "E";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 6;
        category.longName = "Zeta";
        category.shortName = "F";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 7;
        category.longName = "Eta";
        category.shortName = "G";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 8;
        category.longName = "Theta";
        category.shortName = "H";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 9;
        category.longName = "Iota";
        category.shortName = "I";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 10;
        category.longName = "Kappa";
        category.shortName = "J";
        categories.add(category);

        ToDoEntry todo = new ToDoEntry();
        todo.ID = 151;
        todo.description = "Add all categories";
        todo.categoryIndex = UNFILED.index;
        todo.priority = 1;
        todo.completed = true;

        writeDatFile(testFile, false, false,
        "C:\\Program Files\\Palm\\User\\todo\\categories.dat",
        categories, Collections.singletonList(todo));

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("Categories file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Test categories to add to data files for testing various To Do imports.
     * Note that the category {@code ID}&rsquo;s are all different from their
     * indices; the import should check that the categories are imported
     * with their corresponding {@code ID} (unless there is a conflict)
     * and that To Do records are imported with their correct categories.
     */
    public static final List<CategoryEntry> TEST_CATEGORIES;
    static {
        List<CategoryEntry> categories = new ArrayList<>(3);

        CategoryEntry category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 4;
        category.longName = "Maintenance";
        category.shortName = "Maintain";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 6;
        category.longName = "Shopping List";
        category.shortName = "Shopping";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 7;
        category.longName = "Work Tasks";
        category.shortName = "Work";
        categories.add(category);

        category = new CategoryEntry();
        category.index = categories.size() + 1;
        category.ID = 16;
        category.longName = "Top Secret Missions";
        category.shortName = "Secret";
        categories.add(category);

        TEST_CATEGORIES = Collections.unmodifiableList(categories);
    }

    /**
     * Generate a V1 file containing 7 To Do records with various
     * combinations of optional fields.  In version 1, the optional
     * fields are:
     * <ul>
     *     <li>Due Date</li>
     *     <li>Note</li>
     * </ul>
     * Other fields that should be varied to check their imported
     * values are:
     * <ul>
     *     <li>Completed (boolean)</li>
     *     <li>Priority (int)</li>
     *     <li>Private (boolean)</li>
     *     <li>Category index (int)</li>
     * </ul>
     */
    //Test
    public void generateToDoV1File() throws IOException {

        File testFile = File.createTempFile("Palm-todos-v1-", ".dat");

        List<ToDoEntry> records = new ArrayList<>();

        // Start with the simplest record
        ToDoEntry item = new ToDoEntry();
        item.ID = 1;
        item.description = "Write \"Hello, world\"";
        item.categoryIndex = UNFILED.index;
        item.priority = 7;
        records.add(item);

        // Add a few shopping items
        item = new ToDoEntry();
        item.ID = 6;
        item.description = "Milk";
        item.dueDate = LocalDate.of(2026, 1, 14)
                .atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        item.completed = true;
        item.priority = 1;
        item.categoryIndex = TEST_CATEGORIES.get(1).index;
        records.add(item);

        item = new ToDoEntry();
        item.ID = 13;
        item.description = "Batteries";
        item.dueDate = LocalDate.of(2025, 12, 13)
                        .atTime(21, 55).toEpochSecond(ZoneOffset.UTC);
        item.completed = true;
        item.priority = 2;
        item.categoryIndex = TEST_CATEGORIES.get(1).index;
        item.note = "8x AA, 6x AAA, 4x C, 2x D";
        records.add(item);

        item = new ToDoEntry();
        item.ID = 18;
        item.description = "TPS Report";
        item.dueDate = LocalDate.of(1999, 2, 13)
                        .atTime(17, 0).toEpochSecond(ZoneOffset.UTC);
        item.completed = false;
        item.priority = 5;
        item.categoryIndex = TEST_CATEGORIES.get(2).index;
        item.note = "Bill needs you to rewrite the cover sheet.";
        records.add(item);

        item = new ToDoEntry();
        item.ID = 34;
        item.description = "File vacation request";
        item.priority = 4;
        item.categoryIndex = TEST_CATEGORIES.get(2).index;
        records.add(item);

        item = new ToDoEntry();
        item.ID = 165;
        item.description = "Find Dr. No";
        item.priority = 1;
        item.isPrivate = true;
        item.categoryIndex = TEST_CATEGORIES.get(3).index;
        item.note = "John Strangways and his secretary have been murdered"
                + " in Jamaica.\r\nHe was helping the CIA investigate radio"
                + " jamming of rocket launches from Cape Canaveral.\r\n";
        records.add(item);

        item = new ToDoEntry();
        item.ID = 234;
        item.description = "Identify members of SPECTRE";
        item.dueDate = LocalDate.of(2021, 9, 28)
                .atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        item.completed = true;
        item.priority = 3;
        item.isPrivate = true;
        item.categoryIndex = TEST_CATEGORIES.get(3).index;
        item.note = "Most members presumed killed as of the release of"
                + " nanobots from Cuba.\r\nBlofeld eliminated at"
                + " Belmarsh.\r\nRemaining survivors destroyed along"
                + " with nanobots on Safin's island.\r\nAgent was"
                + " lost in the line of duty.";
        records.add(item);

        writeDatFile(testFile, false, false,
        "C:\\Program Files\\Palm\\User\\todo\\todos1.dat",
        TEST_CATEGORIES, records);

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("To Do version 1 file written to "
                + testFile.getAbsolutePath());

    }

    /**
     * Generate a V2 file containing several To Do records with various
     * combinations of optional fields.  On top of the fields retained
     * from V1 files, the new optional fields are:
     * <ul>
     *     <li>Completion date (int, seconds since the Epoch)</li>
     *     <li>Alarm Time (int, seconds since the Epoch, on Jan. 1 1971)</li>
     *     <li>Alarm Days in Advance (int)</li>
     *     <li>Repeat, which in turn has a tag followed by
     *     an optional field list:
     *     <ul>
     *         <li>Type Name (string)</li>
     *         <li>Type (int), which determines which other
     *         fields are required</li>
     *         <li>Interval (int)</li>
     *         <li>Repeat Until (long, seconds since the Epoch)</li>
     *         <li>Day of the Week (int, 0=Sunday)</li>
     *         <li>Day of Week bitmap (01=Sunday, 02=Monday, &hellip;
     *         0x20=Friday, 0x40=Saturday)</li>
     *         <li>Date of Month (int)</li>
     *         <li>Week of Month (int, 0=first week, 4=last week)</li>
     *         <li>Month of Year (int)</li>
     *     </ul>
     *     </li>
     * </ul>
     * There is also one more fields that should be varied, but only used
     * when there is a Repeat:
     * <ul>
     *     <li>Repeat After Completed (boolean)</li>
     * </ul>
     */
    //Test
    public void generateToDoSGV2File() throws IOException {

        File testFile = File.createTempFile("Palm-todos-v2-", ".dat");

        List<ToDoEntry> records = new ArrayList<>();

        // Start with a simple record
        ToDoEntry item = new ToDoEntry();
        item.ID = 2;
        item.categoryIndex = UNFILED.index;
        item.description = "Define categories";
        item.priority = 10;
        records.add(item);

        // This one includes a due date, a repeat (every 6 months),
        // and a completion date
        item = new ToDoEntry();
        item.ID = 7;
        item.categoryIndex = TEST_CATEGORIES.get(0).index;
        item.description = "Change smoke detector batteries";
        item.dueDate = LocalDate.of(2026, 4, 10)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        item.completed = false;
        item.priority = 2;
        item.repeatAfterCompleted = true;
        item.completionDate = LocalDate.of(2025, 10, 10)
                .atTime(15, 33).toEpochSecond(ZoneOffset.UTC);
        item.repeat = new RepeatEvent();
        item.repeat.tag = (short) 0x8000;
        item.repeat.typeName = RepeatEvent.NAME_REPEAT_BY_MONTH_DATE;
        item.repeat.type = RepeatEvent.TYPE_REPEAT_BY_MONTH_DATE;
        item.repeat.interval = 6;
        item.repeat.repeatUntil = UNSET_DATE;
        item.repeat.dayOfWeekBitmap = 0x7f;
        item.repeat.dateOfMonth = 10;
        records.add(item);

        // This one includes a due date and an alarm
        item = new ToDoEntry();
        item.ID = 42;
        item.categoryIndex = TEST_CATEGORIES.get(2).index;
        item.description = "Sell shares of PAQB";
        item.dueDate = LocalDate.of(2026, 7, 7)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        item.priority = 4;
        item.hasAlarm = true;
        item.alarmTime = LocalDate.of(1971, 1, 1)
                .atTime(17, 50).toEpochSecond(ZoneOffset.UTC);
        item.alarmDaysInAdvance = 3;
        records.add(item);

        // This one includes a due date, alarm, completion date,
        // and repeats every Thursday until Christmas Eve
        item = new ToDoEntry();
        item.ID = 67;
        item.categoryIndex = TEST_CATEGORIES.get(2).index;
        item.description = "Check all pressure gauges";
        item.dueDate = LocalDate.of(2026, 1, 15)
                        .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        item.priority = 3;
        item.repeatAfterCompleted = true;
        item.completionDate = LocalDate.of(2026, 1, 14)
                .atTime(11, 0).toEpochSecond(ZoneOffset.UTC);
        item.repeat = new RepeatEvent();
        item.repeat.tag = (short) 0x8000;
        item.repeat.typeName = RepeatEvent.NAME_REPEAT_BY_DAY;
        item.repeat.type = RepeatEvent.TYPE_REPEAT_BY_DAY;
        item.repeat.interval = 1;
        item.repeat.repeatUntil = LocalDate.of(2026, 12, 24)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        item.repeat.dayOfWeek = 4;
        records.add(item);

        // This one has a due date, no completion date or alarm,
        // and repeats weekly on three given days
        item = new ToDoEntry();
        item.ID = 133;
        item.categoryIndex = UNFILED.index;
        item.description = "Gym workout";
        item.dueDate = LocalDate.of(2026, 1, 3)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        item.priority = 5;
        item.repeatAfterCompleted = true;
        item.repeat = new RepeatEvent();
        item.repeat.tag = (short) 0x8000;
        item.repeat.typeName = RepeatEvent.NAME_REPEAT_BY_WEEK;
        item.repeat.type = RepeatEvent.TYPE_REPEAT_BY_WEEK;
        item.repeat.interval = 1;
        item.repeat.repeatUntil = UNSET_DATE;
        item.repeat.dayOfWeekBitmap = 0x54; // Tuesday, Thursday, Saturday
        records.add(item);

        // Annual event
        item = new ToDoEntry();
        item.ID = 226;
        item.categoryIndex = UNFILED.index;
        item.description = "Leap day!";
        item.dueDate = LocalDate.of(2028, 2, 29)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        item.priority = 99;
        item.repeatAfterCompleted = true;
        item.repeat = new RepeatEvent();
        item.repeat.tag = (short) 0x8000;
        item.repeat.typeName = RepeatEvent.NAME_REPEAT_BY_YEAR;
        item.repeat.type = RepeatEvent.TYPE_REPEAT_BY_YEAR;
        item.repeat.interval = 4;
        item.repeat.repeatUntil = UNSET_DATE;
        item.repeat.dateOfMonth = 29;
        item.repeat.monthOfYear = 2;
        records.add(item);

        // Finally, a monthly event by day of week
        item = new ToDoEntry();
        item.ID = 242;
        item.categoryIndex = TEST_CATEGORIES.get(3).index;
        item.isPrivate = true;
        item.description = "Submit mission report";
        item.dueDate = LocalDate.of(2026, 1, 30)
                .atTime(8, 0).toEpochSecond(ZoneOffset.UTC);
        item.priority = 86;
        item.repeatAfterCompleted = true;
        item.repeat = new RepeatEvent();
        item.repeat.tag = (short) 0x8000;
        item.repeat.typeName = RepeatEvent.NAME_REPEAT_BY_MONTH_DAY;
        item.repeat.type = RepeatEvent.TYPE_REPEAT_BY_MONTH_DAY;
        item.repeat.interval = 1;
        item.repeat.repeatUntil = UNSET_DATE;
        item.repeat.dayOfWeek = 5;
        item.repeat.weekOfMonth = 4;
        records.add(item);

        writeDatFile(testFile, true, true,
        "C:\\Program Files\\Palm\\User\\todo\\todos2.dat",
        TEST_CATEGORIES, records);

        assertTrue("No data was written", testFile.length() > 0);

        System.out.println("To Do version 2 file written to "
                + testFile.getAbsolutePath());

    }

}
