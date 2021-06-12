/*
 * Copyright (c) 2021 Privateco and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.privateco.clumber.model;

import com.privateco.clumber.annotation.Immutable;

@Immutable
public class MessageEvent {
    private final String event;
    private final Object attachment;

    public MessageEvent(String event) {
        this(event, null);
    }

    public MessageEvent(String event, Object attachment) {
        this.event = event;
        this.attachment = attachment;
    }

    public String getEvent() {
        return event;
    }
    public Object getAttachment() { return attachment; }
}
