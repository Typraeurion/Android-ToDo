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

import static org.junit.Assert.*;

import com.xmission.trevin.android.todo.data.MockSharedPreferences;
import com.xmission.trevin.android.todo.data.ToDoCategory;
import com.xmission.trevin.android.todo.data.ToDoPreferences;
import com.xmission.trevin.android.todo.provider.MockToDoRepository;
import com.xmission.trevin.android.todo.service.XMLImporter.ImportType;
import com.xmission.trevin.android.todo.util.StringEncryption;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Unit tests for importing To Do data from an XML file.
 */
public class XMLImporterTests {

    private static MockSharedPreferences underlyingPrefs = null;
    private static ToDoPreferences mockPrefs = null;
    private static MockToDoRepository mockRepo = null;
    private StringEncryption globalEncryption = null;
    private String unfiledName;

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
        globalEncryption = StringEncryption.holdGlobalEncryption();
        if (unfiledName == null)
            unfiledName = mockRepo.getCategoryById(
                    ToDoCategory.UNFILED).getName();
    }

    @After
    public void releaseRepository() {
        StringEncryption.releaseGlobalEncryption();
    }

    /**
     * Run the importer for a given test file.  The operation is expected
     * to run successfully.
     *
     * @param inFileName the name of the XML file to import (relative to
     * the &ldquo;resources/&rdquo; directory).
     * @param importType the type of import to perform.
     * @param importPrivate whether to include private records.
     * @param xmlPassword the password with which the XML file was exported,
     * or {@code null}
     *
     * @return the progress indicator at the end of the import
     *
     * @throws IOException if there was an error reading the import file.
     * @throws RuntimeException if the import failed.
     */
    private MockProgressBar runImporter(
            String inFileName, ImportType importType,
            boolean importPrivate, String xmlPassword)
        throws IOException {
        String currentPassword = null;
        if (globalEncryption.hasKey())
            currentPassword = new String(globalEncryption.getPassword());
        MockProgressBar progress = new MockProgressBar();
        InputStream inStream = getClass().getResourceAsStream(inFileName);
        assertNotNull(String.format("Import test file %s not found",
                inFileName), inStream);
        try {
            XMLImporter.importData(mockPrefs, mockRepo,
                    inStream, importType, importPrivate, xmlPassword,
                    currentPassword, progress);
            progress.setEndTime();
        } finally {
            inStream.close();
        }
        return progress;
    }

    /**
     * <b>TEMPORARY TEST &mdash;</b> test reading a backup of To Do
     * data from an actual phone running an older version of the app
     * (version 1 XML data).  This backup file <i>must not</i>
     * be committed with the source files.  For security, the password
     * must be given as an environment variable.
     */
    @Test
    public void testImportPhoneV1() throws IOException {
        String xmlPassword = System.getenv("TODO_PASSWORD");
        runImporter("todo-phone-1.xml", ImportType.TEST, true, xmlPassword);
    }

    /**
     * <b>TEMPORARY TEST &mdash;</b> test reading a backup of To Do
     * data from an actual tablet running an older version of the app
     * (version 1 XML data).  This backup file <i>must not</i>
     * be committed with the source files.  For security, the password
     * must be given as an environment variable.
     */
    @Test
    public void testImportTabletV1() throws IOException {
        String xmlPassword = System.getenv("TODO_PASSWORD");
        runImporter("todo-tablet-1.xml", ImportType.TEST, true, xmlPassword);
    }

    /**
     * Test reading a version 2 XML file with no To Do records.
     */
    @Test
    public void testImportEmpty2() throws IOException {
        runImporter("todo-empty-2.xml", ImportType.TEST, false, null);
    }

    /**
     * Test reading preferences.
     */
    @Test
    public void testImportPreferences() throws IOException {
        /*
         * The import type doesn't matter for this section,
         * as long as it's not TEST.  Since the mock starts out empty,
         * we should have a value for every item in the input file.
         */
        runImporter("todo-preferences.xml", ImportType.CLEAN, false, null);

        // To Do: Add assertions
        fail("Not yet finished");
    }

    // To Do: Write the rest of the test cases ...

}
