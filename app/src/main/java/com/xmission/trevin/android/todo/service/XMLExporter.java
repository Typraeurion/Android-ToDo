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

import android.util.Log;

import com.xmission.trevin.android.todo.data.repeat.*;
import com.xmission.trevin.android.todo.data.*;
import com.xmission.trevin.android.todo.provider.ToDoCursor;
import com.xmission.trevin.android.todo.provider.ToDoRepository;
import com.xmission.trevin.android.todo.provider.ToDoRepositoryImpl;
import com.xmission.trevin.android.todo.provider.ToDoSchema;
import com.xmission.trevin.android.todo.util.StringEncryption;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class exports the To Do list to a given XML output stream.
 *
 * @author Trevin Beattie
 */
public class XMLExporter {

    /** Tag for the debug logger */
    public static final String LOG_TAG = "XMLExporter";

    /** The document element name */
    public static final String DOCUMENT_TAG = "ToDoApp";

    /**
     * The export file version attribute name.
     * If omitted, readers should assume version 1 which is what
     * the old {@code XMLExporterService} class wrote.  This
     * class uses version 2, which writes date and time fields
     * using ISO 8601 representations of their new data types
     * instead of long &ldquo;milliseconds since the epoch&rdquo; values.
     */
    public static final String ATTR_VERSION = "version";

    /** The database version attribute name */
    public static final String ATTR_DB_VERSION = "db-version";

    /** The exported timestamp attribute name */
    public static final String ATTR_EXPORTED = "exported";

    /** The total record count attribute name */
    public static final String ATTR_TOTAL_RECORDS = "total-records";

    /** The preferences element name */
    public static final String PREFERENCES_TAG = "Preferences";

    /** The metadata element name */
    public static final String METADATA_TAG = "Metadata";

    /** Name of metadata child elements */
    public static final String METADATA_ITEM = "item";

    /** The ID attribute name */
    public static final String ATTR_ID = "id";

    /** The name attribute &hellip; name */
    public static final String ATTR_NAME = "name";

    /** The categories element name */
    public static final String CATEGORIES_TAG = "Categories";

    /** The count attribute name */
    public static final String ATTR_COUNT = "count";

    /** The maximum ID attribute name */
    public static final String ATTR_MAX_ID = "max-id";

    /** Name of categories child elements */
    public static final String CATEGORIES_ITEM = "category";

    /** The to-do items element name */
    public static final String ITEMS_TAG = "ToDoList";

    /** Name of To Do list child elements */
    public static final String TODO_ITEM = "to-do";

    /** The checked flag attribute name */
    public static final String ATTR_CHECKED = "checked";

    /** The category ID attribute name */
    public static final String ATTR_CATEGORY_ID = "category";

    /** The priority attribute name */
    public static final String ATTR_PRIORITY = "priority";

    /** The private flag attribute name */
    public static final String ATTR_PRIVATE = "private";

    /** The encryption level attribute name */
    public static final String ATTR_ENCRYPTION = "encryption";

    /** The name of the description sub-element */
    public static final String TODO_DESCRIPTION = "description";

    /** The name of the creation time sub-element */
    public static final String TODO_CREATED = "created";

    /** The name of the modification time sub-element */
    public static final String TODO_MODIFIED = "modified";

    /** The name of the completed time sub-element */
    public static final String TODO_COMPLETED = "completed";

    /** The time attribute name */
    public static final String ATTR_TIME = "time";

    /** The name of the due time sub-element */
    public static final String TODO_DUE = "due";

    /** The date attribute name */
    public static final String ATTR_DATE = "date";

    /** The name of the alarm sub-sub-element */
    public static final String DUE_ALARM = "alarm";

    /** The name of the repeat sub-sub-element */
    public static final String DUE_REPEAT = "repeat";

    /** The interval attribute name (the value is a {@link RepeatInterval} ID) */
    public static final String ATTR_INTERVAL = "interval";

    /** The increment attribute name */
    public static final String ATTR_INCREMENT = "increment";

    /** The week-days attribute name */
    public static final String ATTR_WEEK_DAYS = "week-days";

    /** The weekday direction attribute name */
    public static final String ATTR_DIRECTION = "weekday-direction";

    /** The (first) date attribute name */
    public static final String ATTR_DAY1 = "day1";

    /** The (first) week attribute name */
    public static final String ATTR_WEEK1 = "week1";

    /** The second date attribute name */
    public static final String ATTR_DAY2 = "day2";

    /** The second week attribute name */
    public static final String ATTR_WEEK2 = "week2";

    /** The month attribute name */
    public static final String ATTR_MONTH = "month";

    /** The end date (or timestamp) attribute name */
    public static final String ATTR_END = "end";

    /** The name of the hide sub-sub-element */
    public static final String DUE_HIDE = "hide";

    /** The &ldquo;days earlier&rdquo; attribute name (for {@code hide}) */
    public static final String ATTR_DAYS_EARLIER = "days-earlier";

    /** The name of the last notification time sub-sub-element */
    public static final String DUE_NOTIFICATION = "notification";

    /** The name of the note sub-element */
    public static final String TODO_NOTE = "note";

    /** Modes of operation */
    public enum OpMode {
        START, SETTINGS, CATEGORIES, ITEMS, FINISH
    }
    /**
     * Text to pass to the {@link ProgressBarUpdater} for each
     * mode of operation.  This may be overridden when the class
     * is initialized.
     */
    private static final Map<OpMode,String> modeText = new HashMap<>();
    static {
        modeText.put(OpMode.START, "Starting\u2026");
        modeText.put(OpMode.SETTINGS, "Exporting application settings\u2026");
        modeText.put(OpMode.CATEGORIES, "Exporting categories\u2026");
        modeText.put(OpMode.ITEMS, "Exporting To-Do items\u2026");
        modeText.put(OpMode.FINISH, "Finishing\u2026");
    }

    /**
     * Change the text associated with a mode of operation.
     *
     * @param mode the mode whose text to change
     * @param text the new text to use
     */
    static void setModeText(OpMode mode, String text) {
        modeText.put(mode, text);
    }

    /**
     * Export the preferences, metadata, categories, and To Do records
     * from the database to an XML file.
     *
     * @param prefs the To Do preferences.
     * @param repository The repository from which to read records.
     * It should have already been opened by the caller.
     * @param outStream the stream to which we should write the data in XML.
     * @param exportPrivate whether to include private records and the
     * password hash in the export.  This will include encrypted records;
     * we don&rsquo;t decrypted anything here, just write the encrypted data.
     * @param progressUpdater a class to call back while we are processing
     * the data to mark our progress.
     */
    public static void export(ToDoPreferences prefs,
                              ToDoRepository repository,
                              OutputStream outStream,
                              boolean exportPrivate,
                              ProgressBarUpdater progressUpdater) {

        PrintStream out = new PrintStream(outStream);
        try {
            // Get all of the preferences, metadata, and categories;
            // these should be very short collections.
            Map<String,?> prefsMap = prefs.getAllPreferences();
            List<ToDoMetadata> metadata = repository.getMetadata();
            List<ToDoCategory> categories = repository.getCategories();
            // Get the total count of items to export
            int itemCount = repository.countItems();
            if (!exportPrivate) {
                itemCount -= repository.countPrivateItems();
                // Exclude the password hash
                Iterator<ToDoMetadata> iter = metadata.iterator();
                while (iter.hasNext()) {
                    ToDoMetadata meta = iter.next();
                    if (StringEncryption.METADATA_PASSWORD_HASH
                            .equals(meta.getName()))
                        iter.remove();
                }
            }
            int totalCount = prefsMap.size() + metadata.size()
                    + categories.size() + itemCount;

            out.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            out.printf(Locale.US, "<%s %s=\"2\" %s=\"%d\" %s=\"%s\" %s=\"%d\">\n",
                    DOCUMENT_TAG, ATTR_VERSION,
                    ATTR_DB_VERSION, ToDoRepositoryImpl.DATABASE_VERSION,
                    ATTR_EXPORTED, Instant.now().atOffset(ZoneOffset.UTC)
                            .format(DateTimeFormatter.ISO_INSTANT),
                    ATTR_TOTAL_RECORDS, totalCount);

            progressUpdater.updateProgress(modeText.get(OpMode.SETTINGS),
                    0, totalCount, true);
            int prefsCount = writePreferences(prefsMap, out);
            int metaCount = writeMetadata(metadata, out);

            progressUpdater.updateProgress(modeText.get(OpMode.CATEGORIES),
                    prefsCount + metaCount, totalCount, true);
            long maxCatId = repository.getMaxCategoryId();
            int catCount = writeCategories(categories, maxCatId, out);

            progressUpdater.updateProgress(modeText.get(OpMode.ITEMS),
                    prefsCount + metaCount + catCount, totalCount, true);
            long maxItemId = repository.getMaxItemId();
            ToDoCursor cursor = repository.getItems(
                    ToDoPreferences.ALL_CATEGORIES, true,
                    LocalDate.now(prefs.getTimeZone()),
                    exportPrivate, exportPrivate,
                    ToDoRepositoryImpl.TODO_TABLE_NAME + "."
                            + ToDoSchema.ToDoItemColumns._ID);
            itemCount = writeToDoItems(cursor, maxItemId, out, progressUpdater,
                    prefsCount + metaCount + catCount, totalCount);

            progressUpdater.updateProgress(modeText.get(OpMode.FINISH),
                    prefsCount + metaCount + catCount + itemCount,
                    totalCount, false);

            out.printf(Locale.US, "</%s>\n", DOCUMENT_TAG);
        } finally {
            out.close();
        }
    }

    private static final Pattern XML_RESERVED_CHARACTERS =
        Pattern.compile("[\"&'<>]");

    /** Escape a string for XML sequences */
    public static String escapeXML(String raw) {
        if (raw == null)
            raw = "";
        Matcher m = XML_RESERVED_CHARACTERS.matcher(raw);
        if (m.find()) {
            String step1 = raw.replace("&", "&amp;");
            String step2 = step1.replace("<", "&lt;");
            String step3 = step2.replace(">", "&gt;");
            String step4 = step3.replace("\"", "&quot;");
            String step5 = step4.replace("'", "&apos;");
            return step5;
        } else {
            return raw;
        }
    }

    /** RFC 3548 sec. 4 */
    private static final char[] BASE64_CHARACTERS = {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
        'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
        'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
        'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_',
    };

    /** Convert a stream of bytes to Base64 */
    public static String encodeBase64(byte[] data) {
        StringBuilder sb = new StringBuilder();
        // Process bytes in groups of three
        int i;
        for (i = 0; i + 3 <= data.length; i += 3) {
            // Insert line breaks every 64 characters
            if ((i > 0) && (i % 48 == 0))
                sb.append(System.getProperty("line.separator", "\n"));
            sb.append(BASE64_CHARACTERS[(data[i] >> 2) & 0x3f])
            .append(BASE64_CHARACTERS[((data[i] & 3) << 4) + ((data[i+1] >> 4) & 0x0f)])
            .append(BASE64_CHARACTERS[((data[i+1] & 0xf) << 2) + ((data[i+2] >> 6) & 3)])
            .append(BASE64_CHARACTERS[data[i+2] & 0x3f]);
        }
        // Special handling for the last one or two bytes -- no padding
        if (i < data.length) {
            sb.append(BASE64_CHARACTERS[(data[i] >> 2) & 0x3f]);
            if (i + 1 < data.length) {
                sb.append(BASE64_CHARACTERS[((data[i] & 3) << 4) + ((data[i+1] >> 4) & 0x0f)]);
                sb.append(BASE64_CHARACTERS[(data[i+1] & 0xf) << 2]);
            } else {
                sb.append(BASE64_CHARACTERS[(data[i] & 3) << 4]);
            }
        }
        return sb.toString();
    }

    /**
     * Write out the preferences section
     *
     * @param prefs the preferences to write out
     * @param out the PrintStream to which we should write the data
     *
     * @return the total number of preference items written
     */
    static int writePreferences(
            Map<String,?> prefs, PrintStream out) {
        out.printf(Locale.US, "  <%s %s=\"%d\">\n",
                PREFERENCES_TAG, ATTR_COUNT, prefs.size());
        for (String key : prefs.keySet()) {
            Object value = prefs.get(key);
            out.printf(Locale.US, "    <%s>%s</%s>\n",
                    key, escapeXML((value == null) ? null
                            : value.toString()), key);
        }
        out.printf(Locale.US, "  </%s>\n", PREFERENCES_TAG);
        Log.i(LOG_TAG, String.format("Wrote %d preference settings",
                prefs.size()));
        return prefs.size();
    }

    /**
     * Write out the metadata
     *
     * @param metadata the metadata to write out
     * @param out the PrintStream to which we should write the data
     *
     * @return the total number of metadata written
     */
    static int writeMetadata(
            List<ToDoMetadata> metadata, PrintStream out) {
        out.printf(Locale.US, "  <%s %s=\"%d\">\n",
                METADATA_TAG, ATTR_COUNT, metadata.size());
        for (ToDoMetadata datum : metadata) {
            out.printf(Locale.US, "    <%s %s=\"%d\" %s=\"%s\"",
                    METADATA_ITEM, ATTR_ID, datum.getId(), ATTR_NAME,
                    escapeXML(datum.getName()));
            if (datum.getValue() == null)
                out.println("/>");
            else out.printf(Locale.US, ">%s</%s>\n",
                    encodeBase64(datum.getValue()), METADATA_ITEM);
        }
        out.printf(Locale.US, "  </%s>\n", METADATA_TAG);
        Log.i(LOG_TAG, String.format("Wrote %d metadata items",
                metadata.size()));
        return metadata.size();
    }

    /**
     * Write the category list
     *
     * @param categories the categories to write
     * @param maxId the highest category ID in the database
     * @param out the PrintStream to which we should write the data
     *
     * @return the total number of categories written
     */
    static int writeCategories(
            List<ToDoCategory> categories, long maxId, PrintStream out) {
        out.printf(Locale.US, "  <%s %s=\"%d\" %s=\"%d\">\n",
                CATEGORIES_TAG, ATTR_COUNT, categories.size(),
                ATTR_MAX_ID, maxId);
        for (ToDoCategory category : categories) {
            out.printf(Locale.US, "    <%s %s=\"%d\">%s</%s>\n",
                    CATEGORIES_ITEM, ATTR_ID, category.getId(),
                    escapeXML(category.getName()), CATEGORIES_ITEM);
        }
        out.printf(Locale.US, "  </%s>\n", CATEGORIES_TAG);
        Log.i(LOG_TAG, String.format("Wrote %d categories",
                categories.size()));
        return categories.size();
    }

    /**
     * Write the To Do list
     *
     * @param cursor the cursor over the items to write
     * @param maxId the highest item ID in the database
     * @param out the PrintStream to which we should write the data
     * @param progressUpdater a class to call back while we are processing
     * the data to mark our progress.
     * @param baseCount the number of records written from previous stages
     * @param totalCount the total number of records to be written
     * for the progress bar
     *
     * @return the total number of items written
     */
    static int writeToDoItems(ToDoCursor cursor, long maxId, PrintStream out,
                              ProgressBarUpdater progressUpdater,
                              int baseCount, int totalCount) {
        out.printf(Locale.US, "  <%s %s=\"%d\" %s=\"%d\">\n",
                ITEMS_TAG, ATTR_COUNT, cursor.getCount(),
                ATTR_MAX_ID, maxId);
        int count = 0;
        while (cursor.moveToNext()) {
            ToDoItem item = cursor.getItem();
            writeToDoItem(item, out);
            count++;
            progressUpdater.updateProgress(modeText.get(OpMode.ITEMS),
                    baseCount + count, totalCount, true);
        }
        out.printf(Locale.US, "  </%s>\n", ITEMS_TAG);
        return count;
    }

    /**
     * Write out a single To Do item
     *
     * @param item the item to write
     * @param out the PrintStream to which we should write the item element
     */
    static void writeToDoItem(ToDoItem item, PrintStream out) {
        out.printf(Locale.US,
                "    <%s %s=\"%d\" %s=\"%s\" %s=\"%d\" %s=\"%d\"",
                TODO_ITEM, ATTR_ID, item.getId(),
                ATTR_CHECKED, item.isChecked(),
                ATTR_CATEGORY_ID, item.getCategoryId(),
                ATTR_PRIORITY, item.getPriority());
        if (item.isPrivate()) {
            out.printf(Locale.US, " %s=\"%s\"",
                    ATTR_PRIVATE, item.isPrivate());
            if (item.isEncrypted())
                out.printf(Locale.US, " %s=\"%d\"",
                        ATTR_ENCRYPTION, item.getPrivate());
        }
        out.println(">");
        out.printf(Locale.US, "      <%s>%s</%s>\n",
                TODO_DESCRIPTION, item.isEncrypted()
                        ? encodeBase64(item.getEncryptedDescription())
                        : escapeXML(item.getDescription()),
                TODO_DESCRIPTION);
        out.printf(Locale.US, "      <%s %s=\"%s\"/>\n",
                TODO_CREATED, ATTR_TIME,
                item.getCreateTime().atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_INSTANT));
        out.printf(Locale.US, "      <%s %s=\"%s\"/>\n",
                TODO_MODIFIED, ATTR_TIME,
                item.getModTime().atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_INSTANT));
        if (item.getCompleted() != null)
            out.printf(Locale.US, "      <%s %s=\"%s\"/>\n",
                    TODO_COMPLETED, ATTR_TIME,
                    item.getCompleted().atOffset(ZoneOffset.UTC)
                            .format(DateTimeFormatter.ISO_INSTANT));

        // The hide, alarm, repeat interval, and notification timestamp
        // are all contingent on having a due date.
        if (item.getDue() != null) {
            out.printf(Locale.US, "      <%s %s=\"%s\">\n",
                            TODO_DUE, ATTR_DATE,
                    item.getDue().format(DateTimeFormatter.ISO_DATE));

            if (item.getHideDaysEarlier() != null)
                out.printf(Locale.US, "        <%s %s=\"%d\"/>\n",
                        DUE_HIDE, ATTR_DAYS_EARLIER,
                        item.getHideDaysEarlier());

            if (item.getAlarm() != null) {
                out.printf(Locale.US, "        <%s %s=\"%d\" %s=\"%s\"/>\n",
                        DUE_ALARM, ATTR_DAYS_EARLIER,
                        item.getAlarm().getAlarmDaysEarlier(),
                        ATTR_TIME, item.getAlarm().getTime().format(
                                DateTimeFormatter.ISO_LOCAL_TIME));
                if (item.getAlarm().getNotificationTime() != null)
                    out.printf(Locale.US, "        <%s %s=\"%s\"/>\n",
                            DUE_NOTIFICATION, ATTR_TIME,
                            item.getAlarm().getNotificationTime()
                                    .atOffset(ZoneOffset.UTC)
                                    .format(DateTimeFormatter.ISO_INSTANT));
            }

            if (item.getRepeatInterval() != null) {
                switch (item.getRepeatInterval().getType()) {
                    case NONE:
                        writeNoneRepeat((RepeatNone)
                                item.getRepeatInterval(), out);
                        break;
                    case DAILY:
                    case DAY_AFTER:
                    case WEEK_AFTER:
                    case MONTH_AFTER:
                    case YEAR_AFTER:
                        writeAdjustableRepeat((AbstractAdjustableRepeat)
                                item.getRepeatInterval(), out);
                        break;
                    case WEEKLY:
                        writeWeeklyRepeat((RepeatWeekly)
                                item.getRepeatInterval(), out);
                        break;
                    case MONTHLY_ON_DATE:
                        writeMonthlyOnDateRepeat((RepeatMonthlyOnDate)
                                item.getRepeatInterval(), out);
                        break;
                    case MONTHLY_ON_DAY:
                        writeMonthlyOnDayRepeat((RepeatMonthlyOnDay)
                                item.getRepeatInterval(), out);
                        break;
                    case SEMI_MONTHLY_ON_DATES:
                        writeSemiMonthlyOnDatesRepeat((RepeatSemiMonthlyOnDates)
                                item.getRepeatInterval(), out);
                        break;
                    case SEMI_MONTHLY_ON_DAYS:
                        writeSemiMonthlyOnDaysRepeat((RepeatSemiMonthlyOnDays)
                                item.getRepeatInterval(), out);
                        break;
                    case YEARLY_ON_DATE:
                        writeYearlyOnDateRepeat((RepeatYearlyOnDate)
                                item.getRepeatInterval(), out);
                        break;
                    case YEARLY_ON_DAY:
                        writeYearlyOnDayRepeat((RepeatYearlyOnDay)
                                item.getRepeatInterval(), out);
                        break;
                }
            }

            out.printf(Locale.US, "      </%s>\n", TODO_DUE);
        }

        if ((item.isEncrypted() ? item.getEncryptedNote()
                : item.getNote()) != null) {
            out.printf(Locale.US, "        <%s>%s</%s>\n",
                    TODO_NOTE, item.isEncrypted()
                            ? encodeBase64(item.getEncryptedNote())
                            : escapeXML(item.getNote()),
                    TODO_NOTE);
        }

        out.printf(Locale.US, "    </%s>\n", TODO_ITEM);
    }

    /**
     * Write out a &ldquo;no repeat&rdquo; interval
     *
     * @param repeat the repeat interval to write
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeNoneRepeat(
            RepeatNone repeat, PrintStream out) {
        out.printf(Locale.US, "        <%s %s=\"%d\" %s=\"%s\"/>\n",
                DUE_REPEAT, ATTR_INTERVAL, repeat.getId(),
                ATTR_NAME, repeat.getType());
    }

    /**
     * Write out the header of an abstract repeat interval
     *
     * @param repeat the repeat interval
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeRepeatHeader(
            AbstractRepeat repeat, PrintStream out) {
        out.printf(Locale.US, "        <%s %s=\"%d\" %s=\"%s\" %s=\"%d\"",
                DUE_REPEAT, ATTR_INTERVAL, repeat.getId(),
                ATTR_NAME, repeat.getType(),
                ATTR_INCREMENT, repeat.getIncrement());
    }

    /**
     * Write out the allowed weekdays for an adjustable repeat interval
     *
     * @param repeat the repeat interval
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeRepeatWeekdays(
            AbstractAdjustableRepeat repeat, PrintStream out) {
        StringBuilder daysStr = new StringBuilder();
        if (WeekDays.ALL.equals(repeat.getAllowedWeekDays()))
            daysStr.append("ALL");
        else {
            for (WeekDays day : repeat.getAllowedWeekDays()) {
                if (daysStr.length() > 0)
                    daysStr.append(",");
                daysStr.append(day.name());
            }
        }
        out.printf(Locale.US, " %s=\"%s\"", ATTR_WEEK_DAYS,
                daysStr.toString());
        out.printf(Locale.US, " %s=\"%s\"", ATTR_DIRECTION,
                repeat.getDirection().name());
    }

    /**
     * Write out the trailer for an abstract repeat interval
     *
     * @param repeat the repeat interval
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeRepeatTail(
            AbstractRepeat repeat, PrintStream out) {
        if (repeat.getEnd() != null)
            out.printf(Locale.US, " %s=\"%s\"",
                    ATTR_END, repeat.getEnd().format(
                    DateTimeFormatter.ISO_LOCAL_DATE));
        out.println("/>");
    }

    /**
     * Write out a basic adjustable repeat interval.
     * This covers daily, day-after, week-after, month-after, and year-after.
     *
     * @param repeat the repeat interval to write
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeAdjustableRepeat(
            AbstractAdjustableRepeat repeat, PrintStream out) {
        writeRepeatHeader(repeat, out);
        writeRepeatWeekdays(repeat, out);
        writeRepeatTail(repeat, out);
    }

    /**
     * Write out a weekly repeat interval
     *
     * @param repeat the repeat interval to write
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeWeeklyRepeat(
            RepeatWeekly repeat, PrintStream out) {
        writeRepeatHeader(repeat, out);
        StringBuilder daysStr = new StringBuilder();
        if (WeekDays.ALL.equals(repeat.getWeekDays()))
            daysStr.append("ALL");
        else {
            for (WeekDays day : repeat.getWeekDays()) {
                if (daysStr.length() > 0)
                    daysStr.append(",");
                daysStr.append(day.name());
            }
        }
        out.printf(Locale.US, " %s=\"%s\"", ATTR_WEEK_DAYS, daysStr);
        writeRepeatTail(repeat, out);
    }

    /**
     * Write out a monthly on date repeat interval
     *
     * @param repeat the repeat interval to write
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeMonthlyOnDateRepeat(
            RepeatMonthlyOnDate repeat, PrintStream out) {
        writeRepeatHeader(repeat, out);
        writeRepeatWeekdays(repeat, out);
        out.printf(Locale.US, " %s=\"%d\"",
                ATTR_DAY1, repeat.getDate());
        writeRepeatTail(repeat, out);
    }

    /**
     * Write out a monthly on day of week repeat interval
     *
     * @param repeat the repeat interval to write
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeMonthlyOnDayRepeat(
            RepeatMonthlyOnDay repeat, PrintStream out) {
        writeRepeatHeader(repeat, out);
        out.printf(Locale.US, " %s=\"%s\" %s=\"%d\"",
                ATTR_DAY1, repeat.getDay().name(),
                ATTR_WEEK1, repeat.getWeek());
        writeRepeatTail(repeat, out);
    }

    /**
     * Write out a semi-monthly on dates repeat interval
     *
     * @param repeat the repeat interval to write
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeSemiMonthlyOnDatesRepeat(
            RepeatSemiMonthlyOnDates repeat, PrintStream out) {
        writeRepeatHeader(repeat, out);
        writeRepeatWeekdays(repeat, out);
        out.printf(Locale.US, " %s=\"%d\" %s=\"%d\"",
                ATTR_DAY1, repeat.getDate(),
                ATTR_DAY2, repeat.getDate2());
        writeRepeatTail(repeat, out);
    }

    /**
     * Write out a semi-monthly on days of weeks repeat interval
     *
     * @param repeat the repeat interval to write
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeSemiMonthlyOnDaysRepeat(
            RepeatSemiMonthlyOnDays repeat, PrintStream out) {
        writeRepeatHeader(repeat, out);
        out.printf(Locale.US, " %s=\"%s\" %s=\"%d\" %s=\"%s\" %s=\"%d\"",
                ATTR_DAY1, repeat.getDay().name(),
                ATTR_WEEK1, repeat.getWeek(),
                ATTR_DAY2, repeat.getDay2().name(),
                ATTR_WEEK2, repeat.getWeek2());
        writeRepeatTail(repeat, out);
    }

    /**
     * Write out a yearly on date repeat interval
     *
     * @param repeat the repeat interval to write
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeYearlyOnDateRepeat(
            RepeatYearlyOnDate repeat, PrintStream out) {
        writeRepeatHeader(repeat, out);
        writeRepeatWeekdays(repeat, out);
        out.printf(Locale.US, " %s=\"%d\" %s=\"%s\"",
                ATTR_DAY1, repeat.getDate(),
                ATTR_MONTH, repeat.getMonth().name());
        writeRepeatTail(repeat, out);
    }

    /**
     * Write out a yearly on day and week of month repeat interval
     *
     * @param repeat the repeat interval to write
     * @param out the PrintStream to which to write the repeat
     */
    private static void writeYearlyOnDayRepeat(
            RepeatYearlyOnDay repeat, PrintStream out) {
        writeRepeatHeader(repeat, out);
        out.printf(Locale.US, " %s=\"%s\" %s=\"%d\" %s=\"%s\"",
                ATTR_DAY1, repeat.getDay().name(),
                ATTR_WEEK1, repeat.getWeek(),
                ATTR_MONTH, repeat.getMonth().name());
        writeRepeatTail(repeat, out);
    }

}
