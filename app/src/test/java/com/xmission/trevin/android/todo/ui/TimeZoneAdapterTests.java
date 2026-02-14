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
package com.xmission.trevin.android.todo.ui;

import static org.junit.Assert.fail;

import org.junit.Test;

import java.time.*;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Stand-alone tests for the {@link TimeZoneSelectAdapter}.
 * This basically just previews the available time zones
 * in a standard Java runtime so that we can adjust the
 * region order as needed.
 */
public class TimeZoneAdapterTests {

    static final TimeZoneSelectAdapter adapter = new TimeZoneSelectAdapter();

    @Test
    public void showRegions() {
        int totalCount = 0;
        for (int position = 0; position < adapter.getGroupCount(); position++) {
            TimeZoneSelectAdapter.RegionalGroup group =
                    adapter.getGroup(position);
            int groupCount = adapter.getChildrenCount(position);
            System.out.println(String.format("%d: %s \"%s\" (%d zones)",
                    position, group.zonePrefix,
                    group.displayName, groupCount));
            totalCount += groupCount;
        }
        System.out.println(String.format("Total zones: %d", totalCount));
    }

    @Test
    public void testRegionOrderUnique() {
        Map<Integer, TimeZoneSelectAdapter.RegionalGroup> orderMap
                = new HashMap<>();
        for (int groupPosition = 0;
             groupPosition < adapter.getGroupCount();
             groupPosition++) {
            TimeZoneSelectAdapter.RegionalGroup group =
                    adapter.getGroup(groupPosition);
            if (orderMap.containsKey(group.order)) {
                fail(String.format("Duplicate order %d in groups %s and %s",
                        group.order, orderMap.get(group.order).zonePrefix,
                        group.zonePrefix));
            }
            orderMap.put(group.order, group);
        }
    }

    @Test
    public void testGroupIdUnique() {
        Map<Long, TimeZoneSelectAdapter.RegionalGroup> idMap
                = new HashMap<>();
        for (int groupPosition = 0;
             groupPosition < adapter.getGroupCount();
             groupPosition++) {
            TimeZoneSelectAdapter.RegionalGroup group =
                    adapter.getGroup(groupPosition);
            long groupId = adapter.getGroupId(groupPosition);
            if (idMap.containsKey(groupId)) {
                fail(String.format("Duplicate group ID %s for groups %s and %s",
                        Long.toHexString(groupId),
                        idMap.get(groupId).zonePrefix, group.zonePrefix));
            }
            idMap.put(groupId, group);
        }
    }

    @Test
    public void showZones() {
        for (int groupPosition = 0;
             groupPosition < adapter.getGroupCount();
             groupPosition++) {
            TimeZoneSelectAdapter.RegionalGroup group =
                    adapter.getGroup(groupPosition);
            System.out.println(String.format("%s zones:", group.zonePrefix));
            ZoneOffset minOffset = null;
            ZoneOffset maxOffset = null;
            int minVal = Integer.MAX_VALUE;
            int maxVal = Integer.MIN_VALUE;
            for (int childPosition = 0;
                 childPosition < adapter.getChildrenCount(groupPosition);
                 childPosition++) {
                ZoneId zone = adapter.getChild(groupPosition, childPosition);
                ZoneOffset offset = zone.getRules().getOffset(Instant.EPOCH);
                int offsetVal = offset.getTotalSeconds();
                if (offsetVal < minVal) {
                    minOffset = offset;
                    minVal = offsetVal;
                }
                if (offsetVal > maxVal) {
                    maxOffset = offset;
                    maxVal = offsetVal;
                }
                String offsetText = offset.getDisplayName(
                        TextStyle.FULL, Locale.US);
                if (offsetText.equals("Z"))
                    offsetText = "";
                System.out.println(String.format("%d.%d: %s \"%s\" (UTC%s)",
                        groupPosition, childPosition, zone.getId(),
                        zone.getDisplayName(TextStyle.FULL, Locale.US),
                        offsetText));
            }
            System.out.println(String.format("%s range: %s\u2013%s",
                    group.zonePrefix, minOffset.getId(), maxOffset.getId()));
        }
    }

    @Test
    public void testChildIdUnique() {
        Map<Long,String> idMap = new HashMap<>();
        for (int groupPosition = 0;
             groupPosition < adapter.getGroupCount();
             groupPosition++) {
            idMap.clear();
            for (int childPosition = 0;
                 childPosition < adapter.getChildrenCount(groupPosition);
                 childPosition++) {
                ZoneId zone = adapter.getChild(groupPosition, childPosition);
                long childId = adapter.getChildId(groupPosition, childPosition);
                if (idMap.containsKey(childId)) {
                    fail(String.format("Duplicate child ID %s for zones %s and %s",
                            Long.toHexString(childId),
                            idMap.get(childId), zone.getId()));
                }
                idMap.put(childId, zone.getId());
            }
        }
    }

    @Test
    public void testCombinedIdUnique() {
        Map<Long,String> idMap = new HashMap<>();
        for (int groupPosition = 0;
             groupPosition < adapter.getGroupCount();
             groupPosition++) {
            for (int childPosition = 0;
                 childPosition < adapter.getChildrenCount(groupPosition);
                 childPosition++) {
                ZoneId zone = adapter.getChild(groupPosition, childPosition);
                long combinedId = adapter.getCombinedChildId(
                        groupPosition, childPosition);
                if (idMap.containsKey(combinedId)) {
                    fail(String.format("Duplicate combined ID %s for zones %s and %s",
                            Long.toHexString(combinedId),
                            idMap.get(combinedId), zone.getId()));
                }
            }
        }
    }

}
