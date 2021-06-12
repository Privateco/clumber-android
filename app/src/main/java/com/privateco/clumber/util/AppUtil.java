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

package com.privateco.clumber.util;

import android.content.Context;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import com.privateco.clumber.BuildConfig;
import com.privateco.clumber.R;
import com.privateco.clumber.model.Version;

public class AppUtil {
    private static final Random random = new Random();

    private static final ArrayList<String> avatars = new ArrayList<>();
    static {
        avatars.add("http://i.imgur.com/pv1tBmT.png");
        avatars.add("http://i.imgur.com/R3Jm1CL.png");
        avatars.add("http://i.imgur.com/ROz4Jgh.png");
        avatars.add("http://i.imgur.com/Qn9UesZ.png");
    }

    public static String getRandomId() {
        return Long.toString(UUID.randomUUID().getLeastSignificantBits());
    }

    public static String getRandomAvatarUrl() {
        return avatars.get(random.nextInt(avatars.size()));
    }

    public static String getFirstAvatarUrl() {
        return avatars.get(0);
    }

    public static Version getCurrentVersion() {
        return new Version(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, null);
    }

    public static String buildNewVersionInformation(Context context, Version newVersion) {
        String language = Locale.getDefault().getLanguage();
        String whatsNewInfo = language.equalsIgnoreCase("zh")
                ? newVersion.getWhatsNewInfo().getZh()
                : newVersion.getWhatsNewInfo().getEn();
        return context.getString(R.string.hint_description_old_version_name_new_version) +
                " " +
                AppUtil.getCurrentVersion().getName() +
                "\n" +
                context.getString(R.string.hint_description_new_version_name_new_version) +
                " " +
                newVersion.getName() +
                "\n" +
                whatsNewInfo;
    }
}
