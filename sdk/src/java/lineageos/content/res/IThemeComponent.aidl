/*
 * SPDX-FileCopyrightText: 2014-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.content.res;

import lineageos.content.res.IThemeChangeListener;
import lineageos.content.res.ThemeChangeRequest;
import lineageos.content.res.ThemeConfig;

/** @hide */
interface IThemeComponent {
    oneway void requestThemeChangeUpdates(in IThemeChangeListener listener);
    oneway void removeUpdates(in IThemeChangeListener listener);

    oneway void requestThemeChange(in ThemeChangeRequest request, int userId);
    oneway void applyDefaultTheme(int userId);
    boolean isThemeApplying();
    int getProgress();

    ThemeConfig getThemeConfig(int userId);
    Map getAppliedComponents(int userId);

    long getLastThemeChangeTime(int userId);
    int getLastThemeChangeRequestType(int userId);
}
