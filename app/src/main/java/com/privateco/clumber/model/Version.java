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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.privateco.clumber.annotation.Immutable;

@Immutable
public class Version {
    private final int code;
    private final String name;
    private final WhatsNewInfo whatsNewInfo; // can be null

    public Version(int code, @NonNull String name, @Nullable WhatsNewInfo whatsNewInfo) {
        this.code = code;
        this.name = name;
        this.whatsNewInfo = whatsNewInfo;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public WhatsNewInfo getWhatsNewInfo() {
        return whatsNewInfo;
    }

    @Immutable
    public static class WhatsNewInfo {
        private final String en;
        private final String zh;

        public WhatsNewInfo(@NonNull String en, @NonNull String zh) {
            this.en = en;
            this.zh = zh;
        }

        public String getEn() {
            return en;
        }

        public String getZh() {
            return zh;
        }
    }
}
