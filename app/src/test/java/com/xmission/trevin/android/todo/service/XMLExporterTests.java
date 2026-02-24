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

import static com.xmission.trevin.android.todo.data.ToDoPreferences.*;
import static com.xmission.trevin.android.todo.service.XMLExporter.*;
import static com.xmission.trevin.android.todo.service.XMLImporter.decodeBase64;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomAlarm;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomToDo;
import static com.xmission.trevin.android.todo.util.RandomToDoUtils.randomWeek;

import static org.junit.Assert.*;

import com.xmission.trevin.android.todo.data.repeat.*;
import com.xmission.trevin.android.todo.data.*;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.util.StringEncryption;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Unit tests for exporting To Do data to an XML file.
 */
public class XMLExporterTests {

    private static final Random RAND = new Random();
    private static final RandomStringUtils SRAND = RandomStringUtils.insecure();

    private static MockSharedPreferences underlyingPrefs = null;
    private static ToDoPreferences mockPrefs = null;
    private static MockToDoRepository mockRepo = null;
    // Common XPath instance for evaluating XML document items
    XPath xpath = XPathFactory.newInstance().newXPath();

    @Before
    public void initializeRepository() {
        if (mockPrefs == null) {
            underlyingPrefs = new MockSharedPreferences();
            ToDoPreferences.setSharedPreferences(underlyingPrefs);
            mockPrefs = ToDoPreferences.getInstance(null);
        }
        if (mockRepo == null) {
            mockRepo = MockToDoRepository.getInstance();
        }
        underlyingPrefs.resetMock();
        mockRepo.clear();
    }

    /**
     * Test the root node and major section wrappers with no data
     * (except the repository&rsquo;s default &ldquo;Unfiled&rdquo; category).
     */
    @Test
    public void testExportWrapper() throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        MockProgressBar progress = new MockProgressBar();
        XMLExporter.export(mockPrefs, mockRepo, outStream, false, progress);
        progress.setEndTime();
        outStream.close();

        ByteArrayInputStream inStream = new ByteArrayInputStream(
                outStream.toByteArray());
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(inStream);
        assertEquals("Document root", DOCUMENT_TAG,
                doc.getDocumentElement().getTagName());

        XPath xpath = XPathFactory.newInstance().newXPath();
        assertNotNull(String.format(Locale.US,
                        "Missing <%s> section", PREFERENCES_TAG),
                xpath.evaluate(String.format(Locale.US, "/%s/%s",
                        DOCUMENT_TAG, PREFERENCES_TAG), doc));
        assertNotNull(String.format(Locale.US,
                        "Missing <%s> section", METADATA_TAG),
                xpath.evaluate(String.format(Locale.US, "/%s/%s",
                        DOCUMENT_TAG, METADATA_TAG), doc));
        assertNotNull(String.format(Locale.US,
                        "Missing <%s> section", CATEGORIES_TAG),
                xpath.evaluate(String.format(Locale.US, "/%s/%s",
                        DOCUMENT_TAG, CATEGORIES_TAG), doc));
        assertNotNull(String.format(Locale.US,
                        "Missing <%s> section", ITEMS_TAG),
                xpath.evaluate(String.format(Locale.US, "/%s/%s",
                        DOCUMENT_TAG, ITEMS_TAG), doc));

        String attrValue = xpath.evaluate(String.format(Locale.US,
                "/%s/@%s", DOCUMENT_TAG, ATTR_VERSION), doc);
        assertNotNull(String.format(Locale.US,
                        "Missing %s attribute in document root",
                        ATTR_VERSION), attrValue);
        assertEquals(String.format(Locale.US,
                "Document root %s attribute", ATTR_VERSION),
                "2", attrValue);

        attrValue = xpath.evaluate(String.format(Locale.US,
                "/%s/@%s", DOCUMENT_TAG, ATTR_TOTAL_RECORDS), doc);
        assertNotNull(String.format(Locale.US,
                        "Missing %s attribute in document root",
                        ATTR_TOTAL_RECORDS), attrValue);

        attrValue = xpath.evaluate(String.format(Locale.US,
                "/%s/@%s", DOCUMENT_TAG, ATTR_EXPORTED), doc);
        assertNotNull(String.format(Locale.US,
                        "Missing %s attribute in document root",
                        ATTR_EXPORTED), attrValue);

        attrValue = xpath.evaluate(String.format(Locale.US,
                "/%s/%s/@%s", DOCUMENT_TAG, CATEGORIES_TAG, ATTR_COUNT), doc);
        assertNotNull(String.format(Locale.US,
                        "Missing %s attribute in %s section",
                        ATTR_COUNT, CATEGORIES_TAG), attrValue);

        attrValue = xpath.evaluate(String.format(Locale.US,
                "/%s/%s/@%s", DOCUMENT_TAG, ITEMS_TAG, ATTR_COUNT), doc);
        assertNotNull(String.format(Locale.US,
                        "Missing %s attribute in %s section",
                        ATTR_COUNT, ITEMS_TAG), attrValue);
    }

    /**
     * Helper method for checking a preference value.
     *
     * @param message the string to show if the assertion fails
     * (should describe which preference is being checked)
     * @param element the name of the preference element to look at
     * @param expected the expected value as a string
     * @param doc the XML document to look at
     *
     * @throws AssertionError if the value of the element
     * does not match the expected value
     * @throws XPathExpressionException if the XPath evaluation fails
     */
    private void assertPreferenceEquals(
            String message, String element, String expected, Document doc)
            throws AssertionError, XPathExpressionException {
        assertEquals(message, expected, xpath.evaluate(String.format(
                "/%s/%s/%s", DOCUMENT_TAG, PREFERENCES_TAG, element), doc));
    }

    /**
     * Test writing out preferences
     */
    @Test
    public void testExportPreferences() throws Exception {
        mockPrefs.edit()
                .setExportFile(SRAND.nextAlphabetic(12, 24))
                .setExportPrivate(RAND.nextBoolean())
                .setImportFile(SRAND.nextAlphabetic(12, 24))
                .setImportPrivate(RAND.nextBoolean())
                .setImportType(ToDoPreferences.ImportType.values()[RAND
                        .nextInt(ToDoPreferences.ImportType.values().length)])
                .setNotificationSound(RAND.nextLong(100000))
                .setNotificationVibrate(RAND.nextBoolean())
                .setSelectedCategory(RAND.nextLong(100000))
                .setShowCategory(RAND.nextBoolean())
                .setShowChecked(RAND.nextBoolean())
                .setShowDueDate(RAND.nextBoolean())
                .setShowEncrypted(RAND.nextBoolean())
                .setShowPriority(RAND.nextBoolean())
                .setShowPrivate(RAND.nextBoolean())
                .finish();
        if (RAND.nextBoolean())
            mockPrefs.setTimeZoneLocal();
        else
            mockPrefs.setTimeZone(ZoneId.systemDefault());

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        MockProgressBar progress = new MockProgressBar();
        XMLExporter.export(mockPrefs, mockRepo, outStream, false, progress);
        progress.setEndTime();
        outStream.close();

        ByteArrayInputStream inStream = new ByteArrayInputStream(
                outStream.toByteArray());
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(inStream);

        String countStr = xpath.evaluate(String.format("/%s/%s/@%s",
                DOCUMENT_TAG, PREFERENCES_TAG, ATTR_COUNT), doc);
        assertFalse(String.format("Missing %s %s",
                PREFERENCES_TAG, ATTR_COUNT), countStr.isEmpty());
        int expectedCount = mockPrefs.getAllPreferences().size();
        try {
            int actualCount = Integer.parseInt(countStr);
            assertEquals("Number of preferences exported",
                    expectedCount, actualCount);
        } catch (NumberFormatException x) {
            fail(String.format("%s %s \"%s\" is not an integer",
                    PREFERENCES_TAG, ATTR_COUNT, countStr));
        }

        assertPreferenceEquals("Export file", TPREF_EXPORT_FILE,
                mockPrefs.getExportFile(""), doc);

        assertPreferenceEquals("Export private records", TPREF_EXPORT_PRIVATE,
                Boolean.toString(mockPrefs.exportPrivate()), doc);

        assertPreferenceEquals("Import file", TPREF_IMPORT_FILE,
                mockPrefs.getImportFile(""), doc);

        assertPreferenceEquals("Import private records", TPREF_IMPORT_PRIVATE,
                Boolean.toString(mockPrefs.importPrivate()), doc);

        assertPreferenceEquals("Import type", TPREF_IMPORT_TYPE,
                Integer.toString(mockPrefs.getImportType().ordinal()), doc);

        assertPreferenceEquals("Notification sound", TPREF_NOTIFICATION_SOUND,
                Long.toString(mockPrefs.getNotificationSound()), doc);

        assertPreferenceEquals("Notification vibrate", TPREF_NOTIFICATION_VIBRATE,
                Boolean.toString(mockPrefs.notificationVibrate()), doc);

        assertPreferenceEquals("Selected category", TPREF_SELECTED_CATEGORY,
                Long.toString(mockPrefs.getSelectedCategory()), doc);

        assertPreferenceEquals("Show categories", TPREF_SHOW_CATEGORY,
                Boolean.toString(mockPrefs.showCategory()), doc);

        assertPreferenceEquals("Show completed tasks", TPREF_SHOW_CHECKED,
                Boolean.toString(mockPrefs.showChecked()), doc);

        assertPreferenceEquals("Show due dates", TPREF_SHOW_DUE_DATE,
                Boolean.toString(mockPrefs.showDueDate()), doc);

        assertPreferenceEquals("Show encrypted records", TPREF_SHOW_ENCRYPTED,
                Boolean.toString(mockPrefs.showEncrypted()), doc);

        assertPreferenceEquals("Show priorities", TPREF_SHOW_PRIORITY,
                Boolean.toString(mockPrefs.showPriority()), doc);

        assertPreferenceEquals("Show private records", TPREF_SHOW_PRIVATE,
                Boolean.toString(mockPrefs.showPrivate()), doc);

        assertPreferenceEquals("Use Device Time Zone", TPREF_LOCAL_TIME_ZONE,
                Boolean.toString(mockPrefs.useLocalTimeZone()), doc);

        if (!mockPrefs.useLocalTimeZone())
            assertPreferenceEquals("Fixed Time Zone", TPREF_FIXED_TIME_ZONE,
                    mockPrefs.getTimeZone().getId(), doc);

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Final progress data", endProgress);
        assertEquals("Total records to be processed",
                // Need to add one record for the Unfiled category
                expectedCount + 1, endProgress.total);
        assertEquals("Number of records processed",
                expectedCount + 1, endProgress.current);
    }

    /**
     * Test writing out metadata, excluding the password hash.
     * Since we control the mock repository, we can come up with any
     * random metadata we need.
     */
    @Test
    public void testExportPublicMetadata() throws Exception {
        Map<String,String> expectedMetadata = new TreeMap<>();
        for (int i = RAND.nextInt(5) + 5; i >= 0; --i) {
            String key = SRAND.nextAlphabetic(10, 13);
            String value = SRAND.nextAlphanumeric(20, 25);
            expectedMetadata.put(key, value);
            mockRepo.upsertMetadata(key,
                    value.getBytes(StandardCharsets.UTF_8));
        }
        // The password "hash" doesn't have to be a real hash for this test
        byte[] hash = new byte[32];
        RAND.nextBytes(hash);
        mockRepo.upsertMetadata(StringEncryption.METADATA_PASSWORD_HASH, hash);

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        MockProgressBar progress = new MockProgressBar();
        XMLExporter.export(mockPrefs, mockRepo, outStream, false, progress);
        progress.setEndTime();
        outStream.close();

        ByteArrayInputStream inStream = new ByteArrayInputStream(
                outStream.toByteArray());
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(inStream);

        String countStr = xpath.evaluate(String.format("/%s/%s/@%s",
                DOCUMENT_TAG, METADATA_TAG, ATTR_COUNT), doc);
        assertFalse(String.format("Missing %s %s",
                METADATA_TAG, ATTR_COUNT), countStr.isEmpty());
        try {
            int actualCount = Integer.parseInt(countStr);
            assertEquals("Number of metadata exported",
                    expectedMetadata.size(), actualCount);
        } catch (NumberFormatException x) {
            fail(String.format("%s %s \"%s\" is not an integer",
                    METADATA_TAG, ATTR_COUNT, countStr));
        }

        Map<String,String> actualMetadata = new TreeMap<>();
        NodeList children = (NodeList) xpath.evaluate(String.format(
                "/%s/%s/%s", DOCUMENT_TAG, METADATA_TAG, METADATA_ITEM),
                doc, XPathConstants.NODESET);
        for (int i = 0; i < children.getLength(); i++) {
            Element el = (Element) children.item(i);
            assertEquals("Metadata item tag",
                    METADATA_ITEM, el.getTagName());
            String elementStr = el.toString();
            assertTrue(String.format("Metadata %s has no ID", elementStr),
                    el.hasAttribute(ATTR_ID));
            assertTrue(String.format("Metadata %s has no name", elementStr),
                    el.hasAttribute(ATTR_NAME));
            String value = el.getTextContent();
            assertTrue(String.format("Metadata %s has no content", elementStr),
                    StringUtils.isNotEmpty(value));
            // All values must be base64-encoded
            try {
                byte[] b = decodeBase64(value);
                value = new String(b, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException x) {
                fail(String.format("Metadata %s has corrupted content: %s",
                        elementStr, x.getMessage()));
            }
            actualMetadata.put(el.getAttribute(ATTR_NAME), value);
        }

        assertEquals("Exported metadata",
                expectedMetadata, actualMetadata);

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Final progress data", endProgress);
        assertEquals("Total records to be processed",
                // Need to add one record for the Unfiled category
                expectedMetadata.size() + 1, endProgress.total);
        assertEquals("Number of records processed",
                expectedMetadata.size() + 1, endProgress.current);
    }

    /**
     * Test writing out metadata, including the password hash.
     */
    @Test
    public void testExportPrivateMedatata() throws Exception {
        // The password "hash" doesn't have to be a real hash for this test
        byte[] expectedHash = new byte[32];
        RAND.nextBytes(expectedHash);
        mockRepo.upsertMetadata(StringEncryption.METADATA_PASSWORD_HASH,
                expectedHash);

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        MockProgressBar progress = new MockProgressBar();
        XMLExporter.export(mockPrefs, mockRepo, outStream, true, progress);
        progress.setEndTime();
        outStream.close();

        ByteArrayInputStream inStream = new ByteArrayInputStream(
                outStream.toByteArray());
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(inStream);

        String countStr = xpath.evaluate(String.format("/%s/%s/@%s",
                DOCUMENT_TAG, METADATA_TAG, ATTR_COUNT), doc);
        assertEquals(String.format("%s %s", METADATA_TAG, ATTR_COUNT),
                "1", countStr);

        assertNotNull(String.format("%s %s is missing",
                METADATA_TAG, METADATA_ITEM),
                xpath.evaluate(String.format("(/%s/%s/%s)[1]",
                        DOCUMENT_TAG, METADATA_TAG, METADATA_ITEM), doc));
        String name = xpath.evaluate(String.format("(/%s/%s/%s)[1]/@%s",
                DOCUMENT_TAG, METADATA_TAG, METADATA_ITEM, ATTR_NAME), doc);
        assertEquals("Metadata name",
                StringEncryption.METADATA_PASSWORD_HASH, name);
        String value = xpath.evaluate(String.format("(/%s/%s/%s)[1]/text()",
                DOCUMENT_TAG, METADATA_TAG, METADATA_ITEM), doc);
        assertEquals(String.format("%s value (encoded as Base64)",
                StringEncryption.METADATA_PASSWORD_HASH),
                encodeBase64(expectedHash), value);

    }

    /**
     * Test writing out categories
     */
    @Test
    public void testExportCategories() throws Exception {
        Map<Long,String> expectedCategories = new TreeMap<>();
        for (int i = RAND.nextInt(5) + 5; i >= 0; --i) {
            ToDoCategory category = mockRepo.insertCategory(
                    SRAND.nextAlphabetic(5, 8) + " "
                            + SRAND.nextAlphanumeric(5, 9));
            expectedCategories.put(category.getId(), category.getName());
        }
        expectedCategories.put((long) ToDoCategory.UNFILED,
                mockRepo.getCategoryById(ToDoCategory.UNFILED).getName());

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        MockProgressBar progress = new MockProgressBar();
        XMLExporter.export(mockPrefs, mockRepo, outStream, false, progress);
        progress.setEndTime();
        outStream.close();

        ByteArrayInputStream inStream = new ByteArrayInputStream(
                outStream.toByteArray());
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(inStream);

        String countStr = xpath.evaluate(String.format("/%s/%s/@%s",
                DOCUMENT_TAG, CATEGORIES_TAG, ATTR_COUNT), doc);
        assertFalse(String.format("Missing %s %s",
                CATEGORIES_TAG, ATTR_COUNT), countStr.isEmpty());
        try {
            int actualCount = Integer.parseInt(countStr);
            assertEquals("Number of categories exported",
                    expectedCategories.size(), actualCount);
        } catch (NumberFormatException x) {
            fail(String.format("%s %s \"%s\" is not an integer",
                    CATEGORIES_TAG, ATTR_COUNT, countStr));
        }

        String maxIdStr = xpath.evaluate(String.format("/%s/%s/@%s",
                DOCUMENT_TAG, CATEGORIES_TAG, ATTR_MAX_ID), doc);
        assertFalse(String.format("Missing %s %S",
        CATEGORIES_TAG, ATTR_MAX_ID), maxIdStr.isEmpty());
        try {
            long actualId = Long.parseLong(maxIdStr);
            assertEquals("Maximum category ID",
                    mockRepo.getMaxCategoryId(), actualId);
        } catch (NumberFormatException x) {
            fail(String.format("%s %s \"%s\" is not an integer",
                    CATEGORIES_TAG, ATTR_MAX_ID, countStr));
        }

        Map<Long,String> actualCategories = new TreeMap<>();
        NodeList children = (NodeList) xpath.evaluate(String.format(
                "/%s/%s/%s", DOCUMENT_TAG, CATEGORIES_TAG, CATEGORIES_ITEM),
                doc, XPathConstants.NODESET);
        for (int i = 0; i < children.getLength(); i++) {
            Element el = (Element) children.item(i);
            assertEquals("Category item tag",
                    CATEGORIES_ITEM, el.getTagName());
            String elementStr = el.toString();
            assertTrue(String.format("Category %s has no ID", elementStr),
                    el.hasAttribute(ATTR_ID));
            long id = -1;
            try {
                id = Long.parseLong(el.getAttribute(ATTR_ID));
            } catch (NumberFormatException x) {
                fail(String.format("Category %s ID is not an integer",
                        elementStr));
            }
            String value = el.getTextContent();
            assertTrue(String.format("Category %s has no content", elementStr),
                    StringUtils.isNotEmpty(value));
            actualCategories.put(id, value);
        }

        assertEquals("Exported categories",
                expectedCategories, actualCategories);

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Final progress data", endProgress);
        assertEquals("Total records to be processed",
                expectedCategories.size(), endProgress.total);
        assertEquals("Number of records processed",
                expectedCategories.size(), endProgress.current);
    }

    /**
     * Get the ID of a to-do XML element.
     *
     * @param element the to-do element
     *
     * @return the item ID
     *
     * @throws AssertionError if the element has no ID attribute
     * or if its value is not an integer
     */
    private long getItemId(Element element) throws AssertionError {
        assertEquals("To Do item tag", TODO_ITEM, element.getTagName());
        String elementStr = element.toString();
        assertTrue(String.format("To Do item %s has no ID", elementStr),
                element.hasAttribute(ATTR_ID));
        try {
            return Long.parseLong(element.getAttribute(ATTR_ID));
        } catch (NumberFormatException x) {
            fail(String.format("To Do item %s ID is not an integer",
                    elementStr));
            // Unreachable
            return -1;
        }
    }

    /**
     * Get a single child Element from a to-do XML node.
     * Throw an error if there is not exactly one child.
     *
     * @param id the ID of the To Do item
     * @param tag the name of the element to find
     * @param parent the parent element
     *
     * @throws AssertionError if there is not exactly one {@code tag} child.
     */
    private Element getOnlyChild(long id, String tag, Element parent)
        throws AssertionError, Exception {
        NodeList children = (NodeList) xpath.evaluate(
                tag, parent, XPathConstants.NODESET);
        if (children.getLength() != 1) {
            if (children.getLength() < 1)
                fail(String.format("Exported item #%d has no %s child element",
                        id, tag));
            else
                assertEquals(String.format(
                        "Exported item #%d number of %s child elements",
                                id, tag), 1, children.getLength());
        }
        return (Element) children.item(0);
    }

    /**
     * Check that an optional child element does not exist when not required.
     *
     * @param id the ID of the To Do item
     * @param tag the name of the element to find
     * @param parent the parent element
     *
     * @throws AssertionError if there are any {@code tag} children.
     */
    private void assertNoChild(long id, String tag, Element parent)
            throws AssertionError, Exception {
        NodeList children = (NodeList) xpath.evaluate(
                tag, parent, XPathConstants.NODESET);
        assertEquals(String.format(
                "Exported item #%d number of %s child elements", id, tag),
                0, children.getLength());
    }

    /**
     * Check the value of the week-days attribute.  If the expected
     * days is the full set of seven, we will accept <i>either</i>
     * the word &ldquo;ALL&rdquo; or a full list.  The comparison
     * is case-insensitive (value converted to upper-case).
     *
     * @param id the ID of the To Do item
     * @param expectedDays the expected set of days
     * @param expectedDirection the expected direction, or
     * {@code null} if we do not expect a direction.
     * @param element the repeat XML element
     *
     * @throws AssertionError if the value of the week-days attribute
     * does not correspond to the expected set
     */
    private void assertWeekDays(long id,
                                Set<WeekDays> expectedDays,
                                WeekdayDirection expectedDirection,
                                Element element)
            throws AssertionError {
        assertTrue(String.format("Exported item #%d %s has no %s attribute",
                        id, DUE_REPEAT, ATTR_WEEK_DAYS),
                element.hasAttribute(ATTR_WEEK_DAYS));
        Set<WeekDays> actualDays;
        String value = element.getAttribute(ATTR_WEEK_DAYS)
                .toUpperCase(Locale.US);
        if ("ALL".equals(value))
            actualDays = WeekDays.ALL;
        else {
            actualDays = new TreeSet<>();
            for (String day : value.split(",")) {
                day = day.trim();
                try {
                    actualDays.add(WeekDays.valueOf(day));
                } catch (IllegalArgumentException x) {
                    fail(String.format("Exported item #%d %s has an invalid %s value",
                            id, DUE_REPEAT, ATTR_WEEK_DAYS));
                }
            }
        }
        assertEquals(String.format("Exported item #%d %s %s",
                id, DUE_REPEAT, ATTR_WEEK_DAYS),
                expectedDays, actualDays);
        if (expectedDirection != null) {
            assertTrue(String.format("Exported item #%d %s has no %s attribute",
                    id, DUE_REPEAT, ATTR_DIRECTION),
                    element.hasAttribute(ATTR_DIRECTION));
            assertEquals(String.format("Exported item #%d %s %s",
                    id, DUE_REPEAT, ATTR_DIRECTION),
                    expectedDirection.name(),
                    element.getAttribute(ATTR_DIRECTION));
        }
    }

    /**
     * Check whether a To Do element parsed from XML matches the
     * expected item.
     *
     * @param expectedItem the original To Do item
     * @param element the XML element
     *
     * @throws AssertionError if the element doesn&rsquo;t contain
     * any of the expected attributes or child elements or if their
     * values don&rsquo;t match.
     */
    private void checkToDoXML(ToDoItem expectedItem, Element element)
            throws AssertionError, Exception {
        long itemId = expectedItem.getId();
        assertTrue(String.format("To Do item #%d has no %s attribute",
                        itemId, ATTR_CHECKED),
                element.hasAttribute(ATTR_CHECKED));
        assertTrue(String.format("To Do item #%d has no %s attribute",
                        itemId, ATTR_CATEGORY_ID),
                element.hasAttribute(ATTR_CATEGORY_ID));
        assertTrue(String.format("To Do item #%d has no %s attribute",
                        itemId, ATTR_PRIORITY),
                element.hasAttribute(ATTR_PRIORITY));
        assertEquals(String.format("Exported item #%d %s attribute",
                        itemId, ATTR_CHECKED),
                Boolean.toString(expectedItem.isChecked()),
                element.getAttribute(ATTR_CHECKED));
        assertEquals(String.format("Exported item #%d %s attribute",
                        itemId, ATTR_CATEGORY_ID),
                Long.toString(expectedItem.getCategoryId()),
                element.getAttribute(ATTR_CATEGORY_ID));
        assertEquals(String.format("Exported item #%d %s attribute",
                        itemId, ATTR_PRIORITY),
                Integer.toString(expectedItem.getPriority()),
                element.getAttribute(ATTR_PRIORITY));

        // The `private' and `encryption' attributes are optional if public
        if (expectedItem.isPrivate()) {
            assertTrue(String.format("Private item #%d has no %s attribute",
                            itemId, ATTR_PRIVATE),
                    element.hasAttribute(ATTR_PRIVATE));
            if (expectedItem.isEncrypted()) {
                assertTrue(String.format(
                        "Encrypted item #%d has no %s attribute",
                                itemId, ATTR_ENCRYPTION),
                        element.hasAttribute(ATTR_ENCRYPTION));
            }
        }
        if (element.hasAttribute(ATTR_PRIVATE)) {
            assertEquals(String.format("Exported item #%d %s attribute",
                            itemId, ATTR_PRIVATE),
                    Boolean.toString(expectedItem.isPrivate()),
                    element.getAttribute(ATTR_PRIVATE));
        }
        if (element.hasAttribute(ATTR_ENCRYPTION)) {
            assertEquals(String.format("Exported item #%d %s attribute",
                            itemId, ATTR_ENCRYPTION),
                    Integer.toString(expectedItem.getPrivate()),
                    element.getAttribute(ATTR_ENCRYPTION));
        }

        Element childElement = getOnlyChild(itemId, TODO_DESCRIPTION, element);
        String content = childElement.getTextContent();
        if (expectedItem.isEncrypted()) {
            assertEquals(String.format(
                            "Exported item #%d encrypted description", itemId),
                    encodeBase64(expectedItem.getEncryptedDescription()),
                    content);
        } else {
            assertEquals(String.format(
                    "Exported item #%d description", itemId),
                    expectedItem.getDescription(), content);
        }

        childElement = getOnlyChild(itemId, TODO_CREATED, element);
        assertTrue(String.format("Exported item #%d %s has no %s",
                itemId, TODO_CREATED, ATTR_TIME),
                childElement.hasAttribute(ATTR_TIME));
        assertEquals(String.format("Exported item #%d created time", itemId),
                expectedItem.getCreateTime().atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_INSTANT),
                childElement.getAttribute(ATTR_TIME));

        childElement = getOnlyChild(itemId, TODO_MODIFIED, element);
        assertTrue(String.format("Exported item #%d %s has no %s",
                itemId, TODO_MODIFIED, ATTR_TIME),
                childElement.hasAttribute(ATTR_TIME));
        assertEquals(String.format("Exported item #%d last modified time",
                itemId), expectedItem.getModTime().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT),
                childElement.getAttribute(ATTR_TIME));

        if (expectedItem.isEncrypted()) {
            if (expectedItem.getEncryptedNote() == null)
                assertNoChild(itemId, TODO_NOTE, element);
            else {
                childElement = getOnlyChild(itemId, TODO_NOTE, element);
                content = childElement.getTextContent();
                assertEquals(String.format(
                                "Exported item #%d encrypted note", itemId),
                        encodeBase64(expectedItem.getEncryptedNote()),
                        content);
            }
        } else {
            if (expectedItem.getNote() == null)
                assertNoChild(itemId, TODO_NOTE, element);
            else {
                childElement = getOnlyChild(itemId, TODO_NOTE, element);
                content = childElement.getTextContent();
                assertEquals(String.format("Exported item #%d note", itemId),
                        expectedItem.getNote(), content);
            }
        }

        if ((expectedItem.getDue() == null) &&
                (expectedItem.getHideDaysEarlier() == null) &&
                (expectedItem.getAlarm() == null) &&
                (expectedItem.getRepeatInterval() == null)) {
            assertNoChild(itemId, TODO_DUE, element);
        } else {
            childElement = getOnlyChild(itemId, TODO_DUE, element);
            if (expectedItem.getDue() == null)
                assertFalse(String.format("Exported item #%d %s element has a"
                                + " %s attribute but the item has no due date",
                                itemId, TODO_DUE, ATTR_DATE),
                        childElement.hasAttribute(ATTR_DATE));
            else {
                assertTrue(String.format("Exported item #%d %s element"
                                + " has no %s attribute",
                                itemId, TODO_DUE, ATTR_DATE),
                        childElement.hasAttribute(ATTR_DATE));
                assertEquals(String.format("Exported item #%d %s %s",
                                itemId, TODO_DUE, ATTR_DATE),
                        expectedItem.getDue().format(
                                DateTimeFormatter.ISO_LOCAL_DATE),
                        childElement.getAttribute(ATTR_DATE));
            }

            if (expectedItem.getHideDaysEarlier() == null)
                assertNoChild(itemId, DUE_HIDE, childElement);
            else {
                Element grandchild = getOnlyChild(
                        itemId, DUE_HIDE, childElement);
                assertTrue(String.format("Exported item #%d %s has no %s"
                        + " attribute", itemId, DUE_HIDE, ATTR_DAYS_EARLIER),
                        grandchild.hasAttribute(ATTR_DAYS_EARLIER));
                assertEquals(String.format("Exported item #%d %s %s",
                                itemId, DUE_HIDE, ATTR_DAYS_EARLIER),
                        Integer.toString(expectedItem.getHideDaysEarlier()),
                        grandchild.getAttribute(ATTR_DAYS_EARLIER));
            }

            if (expectedItem.getAlarm() == null) {
                assertNoChild(itemId, DUE_ALARM, childElement);
                assertNoChild(itemId, DUE_NOTIFICATION, childElement);
            } else {
                Element grandchild = getOnlyChild(
                        itemId, DUE_ALARM, childElement);
                assertTrue(String.format("Exported item #%d %s has no %s"
                        + " attribute", itemId, DUE_ALARM, ATTR_DAYS_EARLIER),
                        grandchild.hasAttribute(ATTR_DAYS_EARLIER));
                assertTrue(String.format("Exported item #%d %s has no %s"
                        + " attribute", itemId, DUE_ALARM, ATTR_TIME),
                        grandchild.hasAttribute(ATTR_TIME));
                assertEquals(String.format("Exported item #%d %s %s",
                                itemId, DUE_ALARM, ATTR_DAYS_EARLIER),
                        Integer.toString(expectedItem.getAlarm().getAlarmDaysEarlier()),
                        grandchild.getAttribute(ATTR_DAYS_EARLIER));
                assertEquals(String.format("Exported item #%s %s %S",
                                itemId, DUE_ALARM, ATTR_TIME),
                        expectedItem.getAlarm().getTime().format(
                        DateTimeFormatter.ISO_LOCAL_TIME),
                        grandchild.getAttribute(ATTR_TIME));
                if (expectedItem.getAlarm().getNotificationTime() == null)
                    assertNoChild(itemId, DUE_NOTIFICATION, childElement);
                else {
                    grandchild = getOnlyChild(
                            itemId, DUE_NOTIFICATION, childElement);
                    assertTrue(String.format("Exported item #%d %s has no %s"
                            + " attribute", itemId, DUE_NOTIFICATION, ATTR_TIME),
                            grandchild.hasAttribute(ATTR_TIME));
                    assertEquals(String.format("Exported item #%d %s %s",
                                    itemId, DUE_NOTIFICATION, ATTR_TIME),
                            expectedItem.getAlarm().getNotificationTime()
                                    .atOffset(ZoneOffset.UTC)
                                    .format(DateTimeFormatter.ISO_INSTANT),
                            grandchild.getAttribute(ATTR_TIME));
                }
            }

            if (expectedItem.getRepeatInterval() == null)
                assertNoChild(itemId, DUE_REPEAT, childElement);
            else {
                Element grandchild = getOnlyChild(
                        itemId, DUE_REPEAT, childElement);
                assertTrue(String.format("Exported item #%d %s has no %s"
                                + " attribute", itemId, DUE_REPEAT, ATTR_TYPE),
                        grandchild.hasAttribute(ATTR_TYPE));
                RepeatInterval repeat = expectedItem.getRepeatInterval();
                assertEquals(String.format("Exported item #%d %s %s",
                                itemId, DUE_REPEAT, ATTR_TYPE),
                        repeat.getType().toString(),
                        grandchild.getAttribute(ATTR_TYPE));

                if (repeat instanceof AbstractAdjustableRepeat) {
                    AbstractAdjustableRepeat aar =
                            (AbstractAdjustableRepeat) repeat;
                    assertWeekDays(itemId, aar.getAllowedWeekDays(),
                            aar.getDirection(), grandchild);
                }

                // Since many attributes overlap between interval types,
                // check their existence first in these switches.
                switch (repeat.getType()) {
                    case SEMI_MONTHLY_ON_DAYS:
                        assertTrue(String.format("Exported item #%d %s has no %s attribute",
                                itemId, DUE_REPEAT, ATTR_WEEK2),
                                grandchild.hasAttribute(ATTR_WEEK2));
                        // Fall through
                    case YEARLY_ON_DAY:
                    case MONTHLY_ON_DAY:
                        assertTrue(String.format("Exported item #%d %s has no %s attribute",
                                itemId, DUE_REPEAT, ATTR_WEEK1),
                                grandchild.hasAttribute(ATTR_WEEK1));
                }
                switch (repeat.getType()) {
                    case SEMI_MONTHLY_ON_DATES:
                    case SEMI_MONTHLY_ON_DAYS:
                        assertTrue(String.format("Exported item #%d %s has no %s attribute",
                                        itemId, DUE_REPEAT, ATTR_DAY2),
                                grandchild.hasAttribute(ATTR_DAY2));
                        // Fall through
                    case YEARLY_ON_DATE:
                    case YEARLY_ON_DAY:
                    case MONTHLY_ON_DATE:
                    case MONTHLY_ON_DAY:
                        assertTrue(String.format("Exported item #%d %s has no %s attribute",
                                        itemId, DUE_REPEAT, ATTR_DAY1),
                                grandchild.hasAttribute(ATTR_DAY1));
                }
                switch (repeat.getType()) {
                    case YEARLY_ON_DATE:
                    case YEARLY_ON_DAY:
                        assertTrue(String.format("Exported item #%d %s has no %s attribute",
                                        itemId, DUE_REPEAT, ATTR_MONTH),
                                grandchild.hasAttribute(ATTR_MONTH));
                }

                if (repeat instanceof RepeatWeekly) {
                    assertWeekDays(itemId, ((RepeatWeekly) repeat)
                            .getWeekDays(), null, grandchild);
                }

                if (repeat instanceof AbstractDateRepeat) {
                    assertEquals(String.format("Exported item #%d %s %s",
                                    itemId, DUE_REPEAT, ATTR_DAY1),
                            Integer.toString(((AbstractDateRepeat) repeat)
                                    .getDate()),
                            grandchild.getAttribute(ATTR_DAY1));
                }

                if (repeat instanceof RepeatMonthlyOnDay) {
                    RepeatMonthlyOnDay rmd = (RepeatMonthlyOnDay) repeat;
                    assertEquals(String.format("Exported item #%d %s %s",
                                    itemId, DUE_REPEAT, ATTR_DAY1),
                            rmd.getDay().name(),
                            grandchild.getAttribute(ATTR_DAY1)
                                    .toUpperCase(Locale.US));
                    assertEquals(String.format("Exported item #%d %s %s",
                                    itemId, DUE_REPEAT, ATTR_WEEK1),
                            Integer.toString(rmd.getWeek()),
                            grandchild.getAttribute(ATTR_WEEK1));
                }

                if (repeat instanceof RepeatSemiMonthlyOnDates) {
                    RepeatSemiMonthlyOnDates rsm =
                            (RepeatSemiMonthlyOnDates) repeat;
                    assertEquals(String.format("Exported item #%d %s %s",
                                    itemId, DUE_REPEAT, ATTR_DAY2),
                            Integer.toString(rsm.getDate2()),
                            grandchild.getAttribute(ATTR_DAY2));
                }

                if (repeat instanceof RepeatSemiMonthlyOnDays) {
                    RepeatSemiMonthlyOnDays rsm =
                            (RepeatSemiMonthlyOnDays) repeat;
                    assertEquals(String.format("Exported item #%d %s %s",
                                    itemId, DUE_REPEAT, ATTR_DAY2),
                            rsm.getDay2().name(),
                            grandchild.getAttribute(ATTR_DAY2)
                                    .toUpperCase(Locale.US));
                    assertEquals(String.format("Exported item #%d %s %s",
                                    itemId, DUE_REPEAT, ATTR_WEEK2),
                            Integer.toString(rsm.getWeek2()),
                            grandchild.getAttribute(ATTR_WEEK2));
                }

                if (repeat instanceof RepeatYearlyOnDate) {
                    RepeatYearlyOnDate ryd = (RepeatYearlyOnDate) repeat;
                    assertEquals(String.format("Exported item #%d %s %s",
                                    itemId, DUE_REPEAT, ATTR_MONTH),
                            ryd.getMonth().name(),
                            grandchild.getAttribute(ATTR_MONTH)
                                    .toUpperCase(Locale.US));
                }

                if (repeat instanceof RepeatYearlyOnDay) {
                    RepeatYearlyOnDay ryd = (RepeatYearlyOnDay) repeat;
                    assertEquals(String.format("Exported item #%d %s %s",
                                    itemId, DUE_REPEAT, ATTR_MONTH),
                            ryd.getMonth().name(),
                            grandchild.getAttribute(ATTR_MONTH)
                                    .toUpperCase(Locale.US));
                }

                if (repeat instanceof AbstractRepeat) {
                    AbstractRepeat ar = (AbstractRepeat) repeat;
                    if (ar.getEnd() == null) {
                        assertFalse(String.format("Exported item #%d %s has a %s"
                                + " but the original item does not",
                                        itemId, DUE_REPEAT, ATTR_END),
                                grandchild.hasAttribute(ATTR_END));
                    } else {
                        assertTrue(String.format("Exported item #%d %s does"
                                                + " not have an %s attribute",
                                        itemId, DUE_REPEAT, ATTR_END),
                                grandchild.hasAttribute(ATTR_END));
                        assertEquals(String.format("Exported item #%d %s %s",
                                        itemId, DUE_REPEAT, ATTR_END),
                                ar.getEnd().format(
                                        DateTimeFormatter.ISO_LOCAL_DATE),
                                grandchild.getAttribute(ATTR_END));
                    }
                }
            }
        }

    }

    /**
     * Test writing out basic To Do items &mdash; no due dates,
     * alarms, repeating intervals, etc.  Private records only.
     */
    @Test
    public void testExportPublicItems() throws Exception {
        List<ToDoCategory> testCategories = new ArrayList<>();
        ToDoCategory category = mockRepo
                .getCategoryById(ToDoCategory.UNFILED);
        testCategories.add(category);
        for (int i = RAND.nextInt(5) + 5; i >= 0; --i) {
            category = mockRepo.insertCategory(
                    SRAND.nextAlphabetic(5, 8) + " "
                            + SRAND.nextAlphanumeric(5, 9));
            testCategories.add(category);
        }

        Map<Long,ToDoItem> expectedItems = new TreeMap<>();
        for (int i = RAND.nextInt(10) + 10; i >= 0; --i) {
            ToDoItem todo = randomToDo();
            todo.setDue(null);
            todo.setHideDaysEarlier(null);
            category = testCategories.get(
                    RAND.nextInt(testCategories.size()));
            todo.setCategoryId(category.getId());
            todo = mockRepo.insertItem(todo);
            if (!todo.isPrivate())
                expectedItems.put(todo.getId(), todo);
        }

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        MockProgressBar progress = new MockProgressBar();
        XMLExporter.export(mockPrefs, mockRepo, outStream, false, progress);
        progress.setEndTime();
        outStream.close();

        ByteArrayInputStream inStream = new ByteArrayInputStream(
                outStream.toByteArray());
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(inStream);

        String countStr = xpath.evaluate(String.format("/%s/%s/@%s",
                DOCUMENT_TAG, ITEMS_TAG, ATTR_COUNT), doc);
        assertFalse(String.format("Missing %s %s",
                ITEMS_TAG, ATTR_COUNT), countStr.isEmpty());
        try {
            int actualCount = Integer.parseInt(countStr);
            assertEquals("Number of To Do items exported",
                    expectedItems.size(), actualCount);
        } catch (NumberFormatException x) {
            fail(String.format("%s %s \"%s\" is not an integer",
                    ITEMS_TAG, ATTR_COUNT, countStr));
        }

        String maxIdStr = xpath.evaluate(String.format("/%s/%s/@%s",
                DOCUMENT_TAG, ITEMS_TAG, ATTR_MAX_ID), doc);
        assertFalse(String.format("Missing %s %S",
                ITEMS_TAG, ATTR_MAX_ID), maxIdStr.isEmpty());
        try {
            long actualId = Long.parseLong(maxIdStr);
            assertEquals("Maximum item ID",
                    mockRepo.getMaxItemId(), actualId);
        } catch (NumberFormatException x) {
            fail(String.format("%s %s \"%s\" is not an integer",
                    ITEMS_TAG, ATTR_MAX_ID, countStr));
        }

        Set<Long> idsSeen = new TreeSet<>();
        NodeList children = (NodeList) xpath.evaluate(String.format(
                "/%s/%s/%s", DOCUMENT_TAG, ITEMS_TAG, TODO_ITEM),
                doc, XPathConstants.NODESET);
        for (int i = 0; i < children.getLength(); i++) {
            Element el = (Element) children.item(i);
            String elementStr = el.toString();
            long id = getItemId(el);
            idsSeen.add(id);
            ToDoItem expectedItem = expectedItems.get(id);
            if (expectedItem == null) {
                ToDoItem unexpectedItem = mockRepo.getItemById(id);
                if (unexpectedItem == null)
                    fail(String.format(
                            "Exported %s does not exist in the repository",
                            elementStr));
                if (unexpectedItem.isPrivate())
                    fail(String.format("Exported To Do item #%d is private",
                            id));
                fail(String.format("TEST ERROR: %s wasn't added"
                        + " to the expected items map", unexpectedItem));
                // Unreachable
                continue;
            }
            checkToDoXML(expectedItem, el);
        }
        if (!idsSeen.equals(expectedItems.keySet())) {
            assertFalse("No To Do items were exported", idsSeen.isEmpty());
            Set<Long> missingIds = new TreeSet<>(expectedItems.keySet());
            missingIds.removeAll(idsSeen);
            assertFalse(String.format("Most To Do items were not exported: %s",
                    missingIds), missingIds.size() >= idsSeen.size());
            List<ToDoItem> missingItems = new ArrayList<>();
            for (Long id : missingIds)
                missingItems.add(expectedItems.get(id));
            fail(String.format("Expected To Do items were not exported: %s",
                    missingItems));
        }

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Final progress data", endProgress);
        assertEquals("Total records to be processed",
                testCategories.size() + expectedItems.size(),
                endProgress.total);
        assertEquals("Number of records processed",
                testCategories.size() + expectedItems.size(),
                endProgress.current);
    }

    /**
     * Test writing out private To Do items.
     */
    @Test
    public void testExportPrivateItems() throws Exception {
        Map<Long,ToDoItem> expectedItems = new HashMap<>();
        for (int i = RAND.nextInt(10) + 15; i >= 0; --i) {
            ToDoItem todo = randomToDo();
            todo.setDue(null);
            todo.setHideDaysEarlier(null);
            if (todo.isPrivate() && RAND.nextBoolean()) {
                // "encrypt" the record
                todo.setPrivate(StringEncryption.BUNDLED_ENCRYPTION);
                todo.setEncryptedDescription(todo.getDescription()
                        .getBytes(StandardCharsets.UTF_8));
                todo.setDescription(null);
                if (todo.getNote() != null) {
                    todo.setEncryptedNote(todo.getNote()
                            .getBytes(StandardCharsets.UTF_8));
                    todo.setNote(null);
                }
            }
            todo = mockRepo.insertItem(todo);
            expectedItems.put(todo.getId(), todo);
        }

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        MockProgressBar progress = new MockProgressBar();
        XMLExporter.export(mockPrefs, mockRepo, outStream, true, progress);
        progress.setEndTime();
        outStream.close();

        ByteArrayInputStream inStream = new ByteArrayInputStream(
                outStream.toByteArray());
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(inStream);

        String countStr = xpath.evaluate(String.format("/%s/%s/@%s",
                DOCUMENT_TAG, ITEMS_TAG, ATTR_COUNT), doc);
        assertFalse(String.format("Missing %s %s",
                ITEMS_TAG, ATTR_COUNT), countStr.isEmpty());
        try {
            int actualCount = Integer.parseInt(countStr);
            assertEquals("Number of To Do items exported",
                    expectedItems.size(), actualCount);
        } catch (NumberFormatException x) {
            fail(String.format("%s %s \"%s\" is not an integer",
                    ITEMS_TAG, ATTR_COUNT, countStr));
        }

        Set<Long> idsSeen = new TreeSet<>();
        NodeList children = (NodeList) xpath.evaluate(String.format(
                "/%s/%s/%s", DOCUMENT_TAG, ITEMS_TAG, TODO_ITEM),
                doc, XPathConstants.NODESET);
        for (int i = 0; i < children.getLength(); i++) {
            Element el = (Element) children.item(i);
            String elementStr = el.toString();
            long id = getItemId(el);
            idsSeen.add(id);
            ToDoItem expectedItem = expectedItems.get(id);
            if (expectedItem == null) {
                ToDoItem unexpectedItem = mockRepo.getItemById(id);
                if (unexpectedItem == null)
                    fail(String.format(
                            "Exported %s does not exist in the repository",
                            elementStr));
                fail(String.format("TEST ERROR: %s wasn't added"
                        + " to the expected items map", unexpectedItem));
                // Unreachable
                continue;
            }
            checkToDoXML(expectedItem, el);
        }
        if (!idsSeen.equals(expectedItems.keySet())) {
            assertFalse("No To Do items were exported", idsSeen.isEmpty());
            Set<Long> missingIds = new TreeSet<>(expectedItems.keySet());
            missingIds.removeAll(idsSeen);
            assertFalse(String.format("Most To Do items were not exported: %s",
                    missingIds), missingIds.size() >= idsSeen.size());
            List<ToDoItem> missingItems = new ArrayList<>();
            for (Long id : missingIds)
                missingItems.add(expectedItems.get(id));
            fail(String.format("Expected To Do items were not exported: %s",
                    missingItems));
        }

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Final progress data", endProgress);
        assertEquals("Total records to be processed",
                // Add 1 for the Unfiled category
                expectedItems.size() + 1, endProgress.total);
        assertEquals("Number of records processed",
                expectedItems.size() + 1, endProgress.current);
    }

    /**
     * Test writing out items with due dates (but no alarm or repeat).
     */
    @Test
    public void testExportDueItems() throws Exception {
        Map<Long,ToDoItem> expectedItems = new HashMap<>();
        for (int i = RAND.nextInt(5) + 5; i >= 0; --i) {
            ToDoItem todo = randomToDo();
            if (todo.getDue() == null) {
                todo.setDue(LocalDate.of(2026,
                        RAND.nextInt(12) + 1,
                        RAND.nextInt(28) + 1));
            }
            todo = mockRepo.insertItem(todo);
            expectedItems.put(todo.getId(), todo);
        }

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        MockProgressBar progress = new MockProgressBar();
        XMLExporter.export(mockPrefs, mockRepo, outStream, true, progress);
        progress.setEndTime();
        outStream.close();

        ByteArrayInputStream inStream = new ByteArrayInputStream(
                outStream.toByteArray());
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(inStream);

        NodeList children = (NodeList) xpath.evaluate(String.format(
                "/%s/%s/%s", DOCUMENT_TAG, ITEMS_TAG, TODO_ITEM),
                doc, XPathConstants.NODESET);
        assertEquals(String.format("Number of %s child elements", ITEMS_TAG),
                expectedItems.size(), children.getLength());
        for (int i = 0; i < children.getLength(); i++) {
            Element el = (Element) children.item(i);
            long id = getItemId(el);
            ToDoItem expectedItem = expectedItems.get(id);
            assertNotNull(String.format("Exported item #%d not among"
                    + " the test items", id), expectedItem);
            checkToDoXML(expectedItem, el);
        }

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Final progress data", endProgress);
        assertEquals("Total records to be processed",
                // Add 1 for the Unfiled category
                expectedItems.size() + 1, endProgress.total);
        assertEquals("Number of records processed",
                expectedItems.size() + 1, endProgress.current);
    }

    /**
     * Test writing out items with an alarm.
     * These may or may not have notification times.
     */
    @Test
    public void testExportAlarmItems() throws Exception {
        Map<Long,ToDoItem> expectedItems = new HashMap<>();
        for (int i = RAND.nextInt(10) + 10; i >= 0; --i) {
            ToDoItem todo = randomToDo();
            if (todo.getDue() == null) {
                todo.setDue(LocalDate.of(2026,
                        RAND.nextInt(12) + 1,
                        RAND.nextInt(28) + 1));
            }
            todo.setAlarm(randomAlarm());
            todo = mockRepo.insertItem(todo);
            expectedItems.put(todo.getId(), todo);
        }

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        MockProgressBar progress = new MockProgressBar();
        XMLExporter.export(mockPrefs, mockRepo, outStream, true, progress);
        progress.setEndTime();
        outStream.close();

        ByteArrayInputStream inStream = new ByteArrayInputStream(
                outStream.toByteArray());
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(inStream);

        NodeList children = (NodeList) xpath.evaluate(String.format(
                "/%s/%s/%s", DOCUMENT_TAG, ITEMS_TAG, TODO_ITEM),
                doc, XPathConstants.NODESET);
        assertEquals(String.format("Number of %s child elements", ITEMS_TAG),
                expectedItems.size(), children.getLength());
        for (int i = 0; i < children.getLength(); i++) {
            Element el = (Element) children.item(i);
            long id = getItemId(el);
            ToDoItem expectedItem = expectedItems.get(id);
            assertNotNull(String.format("Exported item #%d not among"
                    + " the test items", id), expectedItem);
            checkToDoXML(expectedItem, el);
        }

        MockProgressBar.Progress endProgress = progress.getEndProgress();
        assertNotNull("Final progress data", endProgress);
        assertEquals("Total records to be processed",
                // Add 1 for the Unfiled category
                expectedItems.size() + 1, endProgress.total);
        assertEquals("Number of records processed",
                expectedItems.size() + 1, endProgress.current);
    }

    /**
     * Common method for testing an item with an explicit repeat.
     *
     * @param repeat the repeat to test
     */
    private void runRepeatTest(RepeatInterval repeat) throws Exception {
        ToDoItem expectedItem = randomToDo();
        expectedItem.setDue(LocalDate.now().plusDays(RAND.nextInt(400)));
        expectedItem.setRepeatInterval(repeat);
        expectedItem = mockRepo.insertItem(expectedItem);

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        MockProgressBar progress = new MockProgressBar();
        XMLExporter.export(mockPrefs, mockRepo, outStream, true, progress);
        progress.setEndTime();
        outStream.close();

        ByteArrayInputStream inStream = new ByteArrayInputStream(
                outStream.toByteArray());
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(inStream);

        NodeList children = (NodeList) xpath.evaluate(String.format(
                "/%s/%s/%s", DOCUMENT_TAG, ITEMS_TAG, TODO_ITEM),
                doc, XPathConstants.NODESET);
        assertEquals(String.format("Number of %s child elements", ITEMS_TAG),
                1, children.getLength());
        Element el = (Element) children.item(0);
        long id = getItemId(el);
        assertEquals("Exported item ID",
                Long.valueOf(id), expectedItem.getId());
        checkToDoXML(expectedItem, el);
    }

    /**
     * Test writing out an item with an explicit no-repeat (i.e.
     * {@link RepeatNone})
     */
    @Test
    public void testExportRepeatNone() throws Exception {
        runRepeatTest(new RepeatNone());
    }

    /**
     * Test writing out an item with a daily repeat
     */
    @Test
    public void testExportRepeatDaily() throws Exception {
        RepeatDaily repeat = new RepeatDaily();
        repeat.setIncrement(RAND.nextInt(50) + 1);
        repeat.setAllowedWeekDays(WeekDays.fromBitMap(
                RAND.nextInt(WeekDays.DAYS_BIT_MASK) + 1));
        repeat.setDirection(WeekdayDirection.values()[RAND.nextInt(
                WeekdayDirection.values().length)]);
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

    /**
     * Test writing out an item with a day-after repeat
     */
    @Test
    public void testExportRepeatDayAfter() throws Exception {
        RepeatDayAfter repeat = new RepeatDayAfter();
        repeat.setIncrement(RAND.nextInt(50) + 1);
        repeat.setAllowedWeekDays(WeekDays.fromBitMap(
                RAND.nextInt(WeekDays.DAYS_BIT_MASK) + 1));
        repeat.setDirection(WeekdayDirection.values()[RAND.nextInt(
                WeekdayDirection.values().length)]);
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

    /**
     * Test writing out an item with a weekly repeat
     */
    @Test
    public void testExportRepeatWeekly() throws Exception {
        RepeatWeekly repeat = new RepeatWeekly();
        repeat.setIncrement(RAND.nextInt(50) + 1);
        repeat.setWeekDays(WeekDays.fromBitMap(
                RAND.nextInt(WeekDays.DAYS_BIT_MASK) + 1));
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

    /**
     * Test writing out an item with a week-after repeat
     */
    @Test
    public void testExportRepeatWeekAfter() throws Exception {
        RepeatWeekAfter repeat = new RepeatWeekAfter();
        repeat.setIncrement(RAND.nextInt(50) + 1);
        repeat.setAllowedWeekDays(WeekDays.fromBitMap(
                RAND.nextInt(WeekDays.DAYS_BIT_MASK) + 1));
        repeat.setDirection(WeekdayDirection.values()[RAND.nextInt(
                WeekdayDirection.values().length)]);
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

    /**
     * Test writing out an item with a monthly-on-date repeat
     */
    @Test
    public void testExportRepeatMonthlyOnDate() throws Exception {
        RepeatMonthlyOnDate repeat = new RepeatMonthlyOnDate();
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setDate(RAND.nextInt(31) + 1);
        repeat.setAllowedWeekDays(WeekDays.fromBitMap(
                RAND.nextInt(WeekDays.DAYS_BIT_MASK) + 1));
        repeat.setDirection(WeekdayDirection.values()[RAND.nextInt(
                WeekdayDirection.values().length)]);
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

    /**
     * Test writing out an item with a monthly-on-day repeat
     */
    @Test
    public void testExportRepeatMonthlyOnDay() throws Exception {
        RepeatMonthlyOnDay repeat = new RepeatMonthlyOnDay();
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(randomWeek());
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

    /**
     * Test writing out an item with a month-after repeat
     */
    @Test
    public void testExportRepeatMonthAfter() throws Exception {
        RepeatMonthAfter repeat = new RepeatMonthAfter();
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setAllowedWeekDays(WeekDays.fromBitMap(
                RAND.nextInt(WeekDays.DAYS_BIT_MASK) + 1));
        repeat.setDirection(WeekdayDirection.values()[RAND.nextInt(
                WeekdayDirection.values().length)]);
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

    /**
     * Test writing out an item with a semi-monthly-on-dates repeat
     */
    @Test
    public void testExportRepeatSemiMonthlyOnDates() throws Exception {
        RepeatSemiMonthlyOnDates repeat = new RepeatSemiMonthlyOnDates();
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setDate(RAND.nextInt(31) + 1);
        repeat.setDate2(RAND.nextInt(31) + 1);
        repeat.setAllowedWeekDays(WeekDays.fromBitMap(
                RAND.nextInt(WeekDays.DAYS_BIT_MASK) + 1));
        repeat.setDirection(WeekdayDirection.values()[RAND.nextInt(
                WeekdayDirection.values().length)]);
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

    /**
     * Test writing out an item with a semi-monthly-on-days repeat
     */
    @Test
    public void testExportRepeatSemiMonthlyOnDays() throws Exception {
        RepeatSemiMonthlyOnDays repeat = new RepeatSemiMonthlyOnDays();
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(randomWeek());
        repeat.setDay2(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek2(randomWeek());
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

    /**
     * Test writing out an item with a yearly-on-date repeat
     */
    @Test
    public void testExportRepeatYearlyOnDate() throws Exception {
        RepeatYearlyOnDate repeat = new RepeatYearlyOnDate();
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setMonth(Months.values()[RAND.nextInt(Months.values().length)]);
        repeat.setDate(RAND.nextInt(31) + 1);
        repeat.setAllowedWeekDays(WeekDays.fromBitMap(
                RAND.nextInt(WeekDays.DAYS_BIT_MASK) + 1));
        repeat.setDirection(WeekdayDirection.values()[RAND.nextInt(
                WeekdayDirection.values().length)]);
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

    /**
     * Test writing out an item with a yearly-on-day repeat
     */
    @Test
    public void testExportRepeatYearlyOnDay() throws Exception {
        RepeatYearlyOnDay repeat = new RepeatYearlyOnDay();
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setMonth(Months.values()[RAND.nextInt(Months.values().length)]);
        repeat.setDay(WeekDays.values()[RAND.nextInt(WeekDays.values().length)]);
        repeat.setWeek(randomWeek());
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

    /**
     * Test writing out an item with a year-after repeat
     */
    @Test
    public void testExportRepeatYearAfter() throws Exception {
        RepeatYearAfter repeat = new RepeatYearAfter();
        repeat.setIncrement(RAND.nextInt(10) + 1);
        repeat.setAllowedWeekDays(WeekDays.fromBitMap(
                RAND.nextInt(WeekDays.DAYS_BIT_MASK) + 1));
        repeat.setDirection(WeekdayDirection.values()[RAND.nextInt(
                WeekdayDirection.values().length)]);
        if (RAND.nextBoolean())
            repeat.setEnd(LocalDate.now().plusDays(RAND.nextInt(30) + 1));
        runRepeatTest(repeat);
    }

}
