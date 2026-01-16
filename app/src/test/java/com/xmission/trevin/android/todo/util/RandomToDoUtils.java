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
package com.xmission.trevin.android.todo.util;

import com.xmission.trevin.android.todo.data.ToDoAlarm;
import com.xmission.trevin.android.todo.data.ToDoItem;
import com.xmission.trevin.android.todo.data.repeat.RepeatInterval;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Utility methods for generating {@link ToDoItem}s with randomized data
 *
 * @author Trevin Beattie
 */
public class RandomToDoUtils {

    private static final Random RAND = new Random();

    /** Random word generator.  Just a string of lower-case letters. */
    public static String randomWord() {
        int targetLen = RAND.nextInt(7) + 1;
        return RandomStringUtils.randomAlphabetic(targetLen).toLowerCase();
    }

    /**
     * Random sentence generator.  Strings random words with the first
     * word capitalized, joined by spaces, ending in a period.
     */
    public static String randomSentence() {
        int numWords = RAND.nextInt(10) + 3;
        List<String> words = new ArrayList<>();
        words.add((char) ('A' + RAND.nextInt(26)) + randomWord());
        while (words.size() < numWords - 1)
            words.add(randomWord());
        words.add(randomWord() + ".");
        return StringUtils.join(words, ' ');
    }

    /**
     * Random paragraph generator.  Strings random sentences together
     * joined by two spaces each.  Ends the whole thing with a newline.
     */
    public static String randomParagraph() {
        int numSentences = RAND.nextInt(5) + 1;
        List<String> sentences = new ArrayList<>();
        while (sentences.size() < numSentences)
            sentences.add(randomSentence());
        return StringUtils.join(sentences, "  ") + "\n";
    }

    /**
     * Generate a random To Do item.  It will not have an ID.
     * It will be unencrypted, but may or may not be private.
     * Its category will always be &ldquo;Unfiled&rdquo; since
     * this class doesn&rsquo;t have access to any user-defined
     * categories.  It will not have a {@link ToDoAlarm} or
     * {@link RepeatInterval}; those may be generated separately
     * if needed.  If the item&rsquo;s {@code checked} field is
     * {@code true}, its {@code completionTime} will be set; otherwise
     * {@code completionTime} may or may not be set.
     */
    public static ToDoItem randomToDo() {
        ToDoItem item = new ToDoItem();
        item.setCreateTime(Instant.now()
                .minusSeconds(RAND.nextInt(7 * 86400)));
        item.setDescription(randomSentence());
        item.setPrivate(RAND.nextInt(2));
        if (RAND.nextBoolean()) {
            item.setDue(LocalDate.now().plusDays(RAND.nextInt(366)));
            if (RAND.nextBoolean())
                item.setHideDaysEarlier(RAND.nextInt(31));
        }
        item.setPriority(RAND.nextInt(10) + 1);
        item.setChecked(RAND.nextBoolean());
        if (item.isChecked() || RAND.nextBoolean())
            item.setCompleted(Instant.now()
                    .minusSeconds(RAND.nextInt(7 * 86400)));
        if (RAND.nextBoolean())
            item.setNote(randomParagraph());
        item.setModTimeNow();
        return item;
    }

    /**
     * Generate random alarm.  Its last notification time may or
     * may not be set.
     */
    public static ToDoAlarm randomAlarm() {
        ToDoAlarm alarm = new ToDoAlarm();
        alarm.setTime(LocalTime.ofSecondOfDay(RAND.nextInt(86400)));
        alarm.setAlarmDaysEarlier(RAND.nextInt(15));
        if (RAND.nextBoolean())
            alarm.setNotificationTime(Instant.now()
                    .minusSeconds(RAND.nextInt(86400)));
        return alarm;
    }

    /**
     * Generate a random repeat interval.
     */
    public static RepeatInterval randomRepeat() {
        // To Do: Implement method stub
        return null;
    }

}
