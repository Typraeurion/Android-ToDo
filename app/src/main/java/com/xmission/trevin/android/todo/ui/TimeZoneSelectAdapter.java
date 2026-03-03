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

import android.content.Context;
import android.database.DataSetObserver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.xmission.trevin.android.todo.R;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.TextStyle;
import java.util.*;
import java.time.ZoneId;

/**
 * An adapter for selecting a fixed time zone.
 * This is a two-level list where zones are grouped by region
 * (delimited by the first &lsquo;/&rsquo; in the zone ID).
 *
 * @author Trevin Beattie
 */
public class TimeZoneSelectAdapter implements ExpandableListAdapter {

    public static final String TAG = "TimeZoneSelectAdapter";

    private final LayoutInflater inflater;

    /**
     * The region groupings are set in advance.  This container class
     * provides mappings from the zone ID prefix to display names
     * and the order in which they should appear.
     */
    public static class RegionalGroup implements Comparable<RegionalGroup> {
        public final int order;
        @NonNull
        public final String zonePrefix;
        public String displayName;

        /**
         * Construct a regional group where the display name is
         * the same as the zone prefix.
         *
         * @param prefix the zone prefix
         * @param order the sort order of this group
         */
        private RegionalGroup(int order,
                              @NonNull String prefix) {
            zonePrefix = prefix;
            this.order = order;
            displayName = prefix;
        }

        /**
         * Construct a regional group with a specified display name.
         *
         * @param prefix the zone prefix
         * @param order the sort order of this group
         * @param name the display name of this group
         */
        private RegionalGroup(int order,
                              @NonNull String prefix,
                              @NonNull String name) {
            zonePrefix = prefix;
            this.order = order;
            displayName = name;
        }

        /**
         * Change the display name of this group.
         *
         * @param name the display name of this group
         */
        public void setDisplayName(@NonNull String name) {
            displayName = name;
        }

        @Override
        public int hashCode() {
            return zonePrefix.hashCode() * 31 + Integer.hashCode(order);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof RegionalGroup))
                return false;
            RegionalGroup group2 = (RegionalGroup) other;
            return zonePrefix.equals(group2.zonePrefix) &&
                    order == group2.order;
        }

        @Override
        public int compareTo(@NonNull RegionalGroup group2) {
            return Integer.compare(order, group2.order);
        }

        @Override
        @NonNull
        public String toString() {
            return displayName;
        }

    }

    /**
     * The known time zone regions
     */
    private final List<RegionalGroup> REGIONS;

    /**
     * Time zones known to this adapter, grouped by zone prefix
     */
    private final Map<String,List<ZoneId>> REGION_MAP;

    private final List<DataSetObserver> observers = new ArrayList<>();

    /**
     * Initialize the adapter with all authoritative time zones from Java.
     * This is the contextless version for testing, so the display names
     * will be set to the zone prefix instead of from resource files.
     */
    public TimeZoneSelectAdapter() {
        List<RegionalGroup> regionList = new ArrayList<>();
        SortedMap<String,List<ZoneId>> zoneMap = new TreeMap<>();
        RegionalGroup group;
        group = new RegionalGroup(3, "Africa");
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(7, "Antarctica");
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(1, "America");
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(6, "Asia");
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(2, "Atlantic");
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(8, "Australia");
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(4, "Europe");
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(5, "Indian");
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(0, "Pacific");
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(9, "Etc");
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        Collections.sort(regionList);
        REGIONS = Collections.unmodifiableList(regionList);
        REGION_MAP = Collections.unmodifiableMap(zoneMap);
        fillRegionZones();
        inflater = null;
    }

    /**
     * Initialize the adapter with all authoritative time zones from
     * Android.  Their display names will be read from string resources.
     */
    public TimeZoneSelectAdapter(Context context) {
        Log.d(TAG, "created");
        List<RegionalGroup> regionList = new ArrayList<>();
        SortedMap<String,List<ZoneId>> zoneMap = new TreeMap<>();
        RegionalGroup group;
        group = new RegionalGroup(1, "Africa",
                context.getString(R.string.ZonePrefixAfrica));
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(2, "Antarctica",
                context.getString(R.string.ZonePrefixAntarctica));
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(3, "America",
                context.getString(R.string.ZonePrefixAmerica));
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(4, "Asia",
                context.getString(R.string.ZonePrefixAsia));
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(5, "Atlantic",
                context.getString(R.string.ZonePrefixAtlantic));
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(6, "Australia",
                context.getString(R.string.ZonePrefixAustralia));
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(7, "Europe",
                context.getString(R.string.ZonePrefixEurope));
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(8, "Indian",
                context.getString(R.string.ZonePrefixIndian));
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(9, "Pacific",
                context.getString(R.string.ZonePrefixPacific));
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        group = new RegionalGroup(10, "Etc",
                context.getString(R.string.ZonePrefixEtc));
        regionList.add(group);
        zoneMap.put(group.zonePrefix, new ArrayList<>());
        Collections.sort(regionList);
        REGIONS = Collections.unmodifiableList(regionList);
        REGION_MAP = Collections.unmodifiableSortedMap(zoneMap);
        fillRegionZones();
        inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
    }

    /**
     * Target specific zones to drop; these are known duplicates,
     * but we have no way of programmatically detecting them.
     */
    public static final Set<String> DUPLICATE_ZONES = Set.of(
            "Africa/Accra", "Africa/Addis_Ababa", "Africa/Asmara",
            "Africa/Asmera", "Africa/Bamako", "Africa/Banjui",
            "Africa/Banjul", "Africa/Blantyre", "Africa/Brazzaville",
            "Africa/Bujumbura", "Africa/Conakry", "Africa/Dakar",
            "Africa/Dar_es_Salaam", "Africa/Djibouti", "Africa/Douala",
            "Africa/Freetown", "Africa/Gaborone", "Africa/Harare",
            "Africa/Kampala", "Africa/Kigali", "Africa/Kinshasa",
            "Africa/Libreville", "Africa/Lome", "Africa/Luanda",
            "Africa/Lubumbashi", "Africa/Lusaka", "Africa/Malabo",
            "Africa/Maseru", "Africa/Mbabane", "Africa/Mogadishu",
            "Africa/Niamey", "Africa/Nouakchott", "Africa/Ouagadougou",
            "Africa/Porto-Novo", "Africa/Timbuktu",
            "America/Anguilla", "America/Antigua",
            "America/Argentina/ComodRivadavia", "America/Aruba",
            "America/Atikokan", "America/Atka", "America/Blanc-Sablon",
            "America/Buenos_Aires", "America/Catamarca", "America/Cayman",
            "America/Coral_Harbour", "America/Cordoba", "America/Creston",
            "America/Curacao", "America/Dominica", "America/Ensenada",
            "America/Fort_Wayne", "America/Godthab", "America/Grenada",
            "America/Guadeloupe", "America/Indianapolis", "America/Jujuy",
            "America/Knox_IN", "America/Kralendijk", "America/Louisville",
            "America/Lower_Princes", "America/Marigot", "America/Mendoza",
            "America/Montreal", "America/Montserrat", "America/Nassau",
            "America/Nipigon", "America/Pangnirtung", "America/Port_of_Spain",
            "America/Porto_Acre", "America/Rainy_River", "America/Rosario",
            "America/Santa_Isabel", "America/Shiprock",
            "America/St_Barthelemy", "America/St_Kitts", "America/St_Lucia",
            "America/St_Thomas", "America/St_Vincent", "America/Thunder_Bay",
            "America/Tortola", "America/Virgin", "America/Yellowknife",
            "Antarctica/DumontDUrville", "Antarctica/McMurdo",
            "Antarctica/Syowa",
            "Asia/Aden", "Asia/Ashkhabad", "Asia/Bahrain", "Asia/Brunei",
            "Asia/Calcutta", "Asia/Choibalsan", "Asia/Chongqing",
            "Asia/Chungking", "Asia/Dacca", "Asia/Harbin", "Asia/Istanbul",
            "Asia/Kashgar", "Asia/Katmandu", "Asia/Kuala_Lumpur",
            "Asia/Kuwait", "Asia/Macao", "Asia/Muscat", "Asia/Phnom_Penh",
            "Asia/Rangoon", "Asia/Saigon", "Asia/Tel_Aviv", "Asia/Thimbu",
            "Asia/Ujung_Pandang", "Asia/Ulan_Bator", "Asia/Vientiane",
            "Atlantic/Faeroe", "Atlantic/Jan_Mayen", "Atlantic/Reykjavik",
            "Atlantic/St_Helena",
            "Australia/ACT", "Australia/Canberra", "Australia/Currie",
            "Australia/LHI", "Australia/North", "Australia/NSW",
            "Australia/Queensland", "Australia/South", "Australia/Tasmania",
            "Australia/Victoria", "Australia/West", "Australia/Yancowinna",
            "Etc/GMT", "Etc/GMT+0", "Etc/GMT-0", "Etc/GMT0",
            "Etc/Greenwich", "Etc/UCT", "Etc/Universal", "Etc/Zulu",
            "Europe/Belfast", "Europe/Kiev", "Europe/Nicosia",
            "Europe/Vatican",
            "Indian/Antananarivo", "Indian/Christmas", "Indian/Cocos",
            "Indian/Comoro", "Indian/Kerguelen", "Indian/Mahe",
            "Indian/Mayotte", "Indian/Reunion",
            "Pacific/Chuuk", "Pacific/Enderbury", "Pacific/Funafuti",
            "Pacific/Johnston", "Pacific/Majuro", "Pacific/Midway",
            "Pacific/Pohnpei", "Pacific/Ponape", "Pacific/Saipan",
            "Pacific/Samoa", "Pacific/Truk", "Pacific/Wake",
            "Pacific/Wallis", "Pacific/Yap"
    );

    private void fillRegionZones() {
        for (String zoneName : TimeZone.getAvailableIDs()) {
            try {
                ZoneId zone = ZoneId.of(zoneName);
                if (DUPLICATE_ZONES.contains(zoneName))
                    // Skip known duplicates
                    continue;
                if (!zone.getId().equals(zoneName))
                    // Skip any non-canonical names
                    continue;
                String prefix = zone.getId().split("/")[0];
                if (!REGION_MAP.containsKey(prefix))
                    // Skip any names not in the known regions
                    continue;
//                Log.d(TAG, String.format(Locale.US,
//                        "Adding zone %s \"%s\" (%s)",
//                        zone.getId(), zone.getDisplayName(
//                                TextStyle.FULL, Locale.US),
//                        zone.getRules().getOffset(Instant.EPOCH)
//                                .getDisplayName(TextStyle.FULL, Locale.US)));
                REGION_MAP.get(prefix).add(zone);
            } catch (DateTimeException x) {
                // Skip unknown zones
            }
        }
        // Now sort all of the regions chronologically
        for (List<ZoneId> regionalZones : REGION_MAP.values())
            Collections.sort(regionalZones, ZONE_CHRONO_COMPARATOR);
    }

    /**
     * Compare two time zones for sorting chronologically.
     */
    public final Comparator<ZoneId> ZONE_CHRONO_COMPARATOR
            = new Comparator<ZoneId>() {
        @Override
        public int compare(@NonNull ZoneId zone1, @NonNull ZoneId zone2) {
            int offset1 = zone1.getRules().getStandardOffset(Instant.EPOCH)
                    .getTotalSeconds();
            int offset2 = zone2.getRules().getStandardOffset(Instant.EPOCH)
                    .getTotalSeconds();
            if (offset1 != offset2)
                return Integer.compare(offset1, offset2);
            if (zone1.equals(zone2))
                return 0;
            // When different zones have the same standard offset,
            // compare by ID.
            return zone1.getId().compareTo(zone2.getId());
        }
    };

    /**
     * All items in this adapter are available; they were validated
     * during construction.
     *
     * @return {@code true}
     */
    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    /**
     * Gets the data associated with the given child within the given group.
     *
     * @param groupPosition the position of the group
     *                     that the child resides in
     * @param childPosition the position of the child with respect
     *                      to other children in the group
     *
     * @return the child data
     */
    @Override
    public ZoneId getChild(int groupPosition, int childPosition) {
        if ((groupPosition < 0) || (groupPosition >= REGIONS.size()))
            throw new IllegalArgumentException("Invalid group position "
                    + groupPosition);
        List<ZoneId> regionalZones = REGION_MAP.get(
                REGIONS.get(groupPosition).zonePrefix);
        if ((childPosition < 0) || (childPosition >= regionalZones.size()))
            throw new IllegalArgumentException("Invalid child position "
                    + childPosition);
        return regionalZones.get(childPosition);
    }

    /**
     * Gets the ID for the given child within the given group.
     * This ID must be unique across all children within the group.
     * The combined ID must be unique across ALL items
     * (groups and all children).
     *
     * @param groupPosition the position of the group
     *                     that the child resides in
     * @param childPosition the position of the child with respect
     *                      to other children in the group
     *
     * @return the child ID
     */
    @Override
    public long getChildId(int groupPosition, int childPosition) {
        ZoneId zone = getChild(groupPosition, childPosition);
        return zone.hashCode() & 0xffffffffL; // cheat
    }

    /**
     * Gets a {@link View} that displays the time zone for the given
     * child within the given group.
     *
     * @param groupPosition the position of the group
     *                     that the child resides in
     * @param childPosition the position of the child with respect
     *                      to other children in the group
     * @param isLastChild whether the child is the last child within the group
     * @param convertView the old view to reuse, if possible
     * @param parent the parent that this view will eventually be attached to
     *
     * @return the {@link View} corresponding to the child at the
     * specified position
     */
    @Override
    public View getChildView(int groupPosition, int childPosition,
                             boolean isLastChild,
                             View convertView,
                             ViewGroup parent) {
        // For debug logging
        String cvDesc = "null";
        TextView t1 = null;
        TextView t2 = null;
        if (convertView != null) {
            cvDesc = convertView.getClass().getSimpleName();
            t1 = convertView.findViewById(android.R.id.text1);
            t2 = convertView.findViewById(android.R.id.text2);
            if (t1 != null) {
                if (t2 != null) {
                    cvDesc = String.format(Locale.US, "%s@%s[\"%s\",\"%s\"]",
                            cvDesc, Integer.toHexString(
                                    System.identityHashCode(convertView)),
                            t1.getText().toString(), t2.getText().toString());
                } else {
                    cvDesc = String.format(Locale.US, "%s@%s[\"%s\"]", cvDesc,
                            Integer.toHexString(System
                                    .identityHashCode(convertView)),
                            t1.getText().toString());
                }
            }
        }
        Log.d(TAG, String.format(Locale.US, ".getChildView(%d,%d,%s,%s,%s)",
                groupPosition, childPosition, isLastChild,
                cvDesc, parent));
        RegionalGroup group = getGroup(groupPosition);
        ZoneId zone = getChild(groupPosition, childPosition);
        View returnView;
        if (t2 != null) {
            returnView = convertView;
        } else {
            Log.d(TAG, "Creating a new child view");
            returnView = inflater.inflate(
                    android.R.layout.simple_expandable_list_item_2,
                    parent, false);
            t1 = returnView.findViewById(android.R.id.text1);
            t2 = returnView.findViewById(android.R.id.text2);
        }
        if ((t1 == null) || (t2 == null)) {
            Log.e(TAG, "Failed to find two text views in expandable list item");
            return null;
        }
        /*
         * If zone names are not localized, their display name may be the
         * same as the ID.  If this is the case, use the ID without the
         * region prefix.
         */
        String zoneName = zone.getDisplayName(TextStyle.FULL,
                Locale.getDefault());
        String zoneSuffix = zone.getId().replaceFirst(String.format(
                Locale.US, "^%s/", group.zonePrefix), "");
        if (zone.getId().equals(zoneName)) {
            zoneName = zoneSuffix;
            String altSuffix = zone.getDisplayName(TextStyle.SHORT,
                    Locale.getDefault());
            if (zone.getId().equals(altSuffix))
                zoneSuffix = "";
            else
                zoneSuffix = altSuffix;
        }
        t1.setText(zoneName);
        /*
         * Many of the "display name"s are the same for different
         * ID's; for the second row, include both the ID
         * (without the regional prefix) and its offset.
         */
        String offsetName = zone.getRules().getOffset(Instant.now())
                .getDisplayName(TextStyle.FULL, Locale.getDefault());
        if (offsetName.equals("Z"))
            offsetName = "+0";
        t2.setText(String.format(Locale.US, "%s (UTC%s)",
                zoneSuffix, offsetName));
        return returnView;
    }

    /**
     * Gets the number of children in a specified group.
     *
     * @param groupPosition  the position of the group for which
     *                      the children count should be returned
     *
     * @return the number of children in the group
     */
    @Override
    public int getChildrenCount(int groupPosition) {
        if ((groupPosition < 0) || (groupPosition >= REGIONS.size()))
            throw new IllegalArgumentException("Invalid group position "
                    + groupPosition);
        return REGION_MAP.get(REGIONS.get(groupPosition).zonePrefix).size();
    }

    /**
     * Gets an ID for a child that is unique across any item
     * (either group or child) that is in this list.
     *
     * @param groupId the ID of the group that contains this child
     * @param childId the ID of the child
     *
     * @return the child ID
     */
    @Override
    public long getCombinedChildId(long groupId, long childId) {
        return (groupId & 0xffffffff00000000L) + (childId & 0xffffffffL);
    }

    /**
     * Gets an ID for a group that is unique across any item
     * (either group or child) that is in this list.
     *
     * @param groupId the ID of the group
     *
     * @return the group ID
     */
    @Override
    public long getCombinedGroupId(long groupId) {
        return groupId;
    }

    /**
     * Get the data associated with the given group.
     *
     * @param groupPosition the position of the group
     */
    @Override
    public RegionalGroup getGroup(int groupPosition) {
        if ((groupPosition < 0) || (groupPosition >= REGIONS.size()))
            throw new IllegalArgumentException("Invalid group position "
                    + groupPosition);
        return REGIONS.get(groupPosition);
    }

    /**
     * @return the number of groups
     */
    @Override
    public int getGroupCount() {
        return REGIONS.size();
    }

    /**
     * Gets the ID for the group at the given position.
     * This group ID must be unique across groups.
     *
     * @param groupPosition the position of the group
     *
     * @return the group ID
     */
    @Override
    public long getGroupId(int groupPosition) {
        if ((groupPosition < 0) || (groupPosition >= REGIONS.size()))
            throw new IllegalArgumentException("Invalid group position "
                    + groupPosition);
        return ((long) REGIONS.get(groupPosition).hashCode() << 32);
    }

    /**
     * Gets a {@link View} that displays the given group.
     * This {@link View} is only for the group&mdash;the {@link View}s
     * for the group&rsquo;s children will be fetched using
     * {@link #getChildView(int, int, boolean, View, ViewGroup)}.
     *
     * @param groupPosition  the position of the group for which
     *                      the {@link View} should is returned
     * @param isExpanded whether the group is expanded or collapsed
     * @param convertView the old view to reuse, if possible
     * @param parent the parent that this view will eventually be attached to
     *
     * @return the {@link View} corresponding to the group at the
     * specified position
     */
    @Override
    public View getGroupView(int groupPosition,
                             boolean isExpanded,
                             View convertView,
                             ViewGroup parent) {
        // For debug logging
        String cvDesc = (convertView == null) ? "null"
                : convertView.getClass().getSimpleName();
        if (convertView instanceof TextView)
            cvDesc = String.format(Locale.US, "%s@%s(\"%s\")", cvDesc,
                    Integer.toHexString(System.identityHashCode(convertView)),
                    ((TextView) convertView).getText().toString());
        Log.d(TAG, String.format(Locale.US, ".getGroupView(%d,%s,%s,%s)",
                groupPosition, isExpanded,
                cvDesc, parent));
        RegionalGroup group = getGroup(groupPosition);
        TextView tv;
        if (convertView instanceof TextView) {
            tv = (TextView) convertView;
        } else {
            Log.d(TAG, "Creating a new group view");
            tv = (TextView) inflater.inflate(
                    android.R.layout.simple_expandable_list_item_1,
                    parent, false);
        }
        tv.setText(group.displayName);
        return tv;
    }

    /**
     * Since we use hash codes for entry ID&rsquo;s, they should be stable.
     *
     * @return {@code true}
     */
    @Override
    public boolean hasStableIds() {
        return true;
    }

    /**
     * All zones in this adapter are selectable
     *
     * @param groupPosition the position of the group
     *                     that the child resides in
     * @param childPosition the position of the child with respect
     *                      to other children in the group
     *
     * @return {@code true}
     */
    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public boolean isEmpty() {
        for (List<ZoneId> regionalZones : REGION_MAP.values())
            if (!regionalZones.isEmpty())
                return false;
        return true;
    }

    /**
     * Called when a group is collapsed.
     *
     * @param groupPosition the group being collapsed
     */
    @Override
    public void onGroupCollapsed(int groupPosition) {
    }

    /**
     * Called when a group is expanded.
     *
     * @param groupPosition the group being expanded
     */
    @Override
    public void onGroupExpanded(int groupPosition) {
    }

    /**
     * Pointless registration; our data set never changes after initialization.
     */
    @Override
    public void registerDataSetObserver(DataSetObserver dataSetObserver) {
        observers.add(dataSetObserver);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {
        observers.remove(dataSetObserver);
    }

}
