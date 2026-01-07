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

import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;

import com.xmission.trevin.android.todo.data.ToDoItem;

import java.util.List;

/**
 * Implementation of the {@link ToDoCursor} to be used for tests.
 * This should only be used by the {@link MockToDoRepository}
 * to provide a wrapper around an in-memory {@link List}.
 *
 * @author Trevin Beattie
 */
public class MockToDoCursor implements ToDoCursor {

    /**
     * The underlying {@link List} from which we obtain
     * {@link ToDoItem} data
     */
    private final List<ToDoItem> queryRows;

    private boolean isClosed = false;

    private int currentPosition = -1;

    MockToDoCursor(List<ToDoItem> todoList) {
        queryRows = todoList;
    }

    @Override
    public void close() {
        isClosed = true;
    }

    /**
     * Determine whether the cursor is open and in a valid position.
     *
     * @throws SQLException if the cursor is closed, is positioned
     * before the first &ldquo;row&rdquo;, or is positioned after
     * the last &rdquo;row&rdquo;.
     */
    private void checkAccess() throws SQLException {
        if (isClosed)
            throw new SQLException("This cursor has been closed");
        if ((currentPosition < 0) || (currentPosition >= queryRows.size()))
            throw new CursorIndexOutOfBoundsException(
                    currentPosition, queryRows.size());
    }

    @Override
    public ToDoItem getItem() {
        checkAccess();
        return queryRows.get(currentPosition).clone();
    }

    @Override
    public int getCount() {
        return queryRows.size();
    }

    @Override
    public int getPosition() {
        return currentPosition;
    }

    @Override
    public boolean isAfterLast() {
        return queryRows.isEmpty() || (currentPosition >= queryRows.size());
    }

    @Override
    public boolean isBeforeFirst() {
        return queryRows.isEmpty() || (currentPosition < 0);
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public boolean isFirst() {
        return !queryRows.isEmpty() && (currentPosition == 0);
    }

    @Override
    public boolean isLast() {
        return !queryRows.isEmpty() &&
                (currentPosition == queryRows.size() - 1);
    }

    @Override
    public boolean move(int offset) {
        return moveToPosition(currentPosition + offset);
    }

    @Override
    public boolean moveToFirst() {
        return moveToPosition(0);
    }

    @Override
    public boolean moveToLast() {
        return moveToPosition(queryRows.size() - 1);
    }

    @Override
    public boolean moveToNext() {
        return moveToPosition(currentPosition + 1);
    }

    @Override
    public boolean moveToPosition(int position) {
        // Cap the last and first positions
        if (position >= queryRows.size()) {
            currentPosition = queryRows.size();
            return false;
        }

        if (position < 0) {
            currentPosition = -1;
            return false;
        }

        currentPosition = position;
        return true;
    }

    @Override
    public boolean moveToPrevious() {
        return moveToPosition(currentPosition - 1);
    }

}
