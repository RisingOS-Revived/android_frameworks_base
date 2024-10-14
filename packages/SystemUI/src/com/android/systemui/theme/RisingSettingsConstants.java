/*
 * Copyright (C) 2023-2024 The RisingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.theme;

import android.provider.Settings;
import lineageos.providers.LineageSettings;

public class RisingSettingsConstants {
    private static final String PREF_CHROMA_FACTOR ="monet_engine_chroma_factor";
    private static final String PREF_LUMINANCE_FACTOR ="monet_engine_luminance_factor";
    private static final String PREF_TINT_BACKGROUND ="monet_engine_tint_background";
    private static final String PREF_CUSTOM_ACCENT_COLOR ="monet_engine_accent_color";
    private static final String PREF_CUSTOM_BG_COLOR ="monet_engine_bg_color";
    private static final String PREF_OVERRIDE_COLOR_ACCENT ="monet_engine_color_override_accent";
    private static final String PREF_OVERRIDE_BGCOLOR ="monet_engine_override_bg_color";
    private static final String PREF_THEME_STYLE = "monet_engine_theme_style";
    private static final String PREF_COLOR_SOURCE = "monet_engine_color_source";
    private static final String PREF_WHOLE_PALETTE = "monet_engine_whole_palette";
    public static final String CLOCK_STYLE = "clock_style";
    public static final String VOLUME_STYLE = "custom_volume_styles";
    public static final String STATUSBAR_CLOCK_CHIP = Settings.System.STATUSBAR_CLOCK_CHIP;
    public static final String QS_PANEL_TILE_HAPTIC = "qs_panel_tile_haptic";
    public static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";
    public static final String STATUS_BAR_BATTERY_TEXT_CHARGING = "status_bar_battery_text_charging";
    public static final String STATUSBAR_BATTERY_BAR = "statusbar_battery_bar";
    public static final String STATUS_BAR_LOGO_POSITION = "status_bar_logo_position";
    public static final String SETTINGS_ICON_STYLE = "settings_icon_style";
    public static final String SEARCH_BAR_STYLE = "search_bar_style";
    public static final String QS_TILE_LABEL_HIDE = "qs_tile_label_hide";
    public static final String QS_TILE_VERTICAL_LAYOUT = "qs_tile_vertical_layout";
    public static final String LOCKSCREEN_WIDGETS_ENABLED = "lockscreen_widgets_enabled";
    public static final String LOCKSCREEN_WIDGETS = "lockscreen_widgets";
    public static final String LOCKSCREEN_WIDGETS_EXTRAS = "lockscreen_widgets_extras";

    public static final String[] SYSTEM_SETTINGS_KEYS = {
        STATUSBAR_CLOCK_CHIP,
        QS_PANEL_TILE_HAPTIC,
        STATUS_BAR_SHOW_BATTERY_PERCENT,
        STATUS_BAR_BATTERY_TEXT_CHARGING,
        STATUSBAR_BATTERY_BAR,
        STATUS_BAR_LOGO_POSITION,
        SETTINGS_ICON_STYLE,
        SEARCH_BAR_STYLE,
        QS_TILE_LABEL_HIDE,
        QS_TILE_VERTICAL_LAYOUT,
        LOCKSCREEN_WIDGETS_ENABLED,
        LOCKSCREEN_WIDGETS,
        LOCKSCREEN_WIDGETS_EXTRAS,
        "lockscreen_widgets_style",
        "qs_widgets_enabled",
        "qs_tile_label_size",
        "qs_tile_summary_size"
    };
    
    public static final String[] SECURE_SETTINGS_KEYS = {
        CLOCK_STYLE,
        PREF_CHROMA_FACTOR,
        PREF_LUMINANCE_FACTOR,
        PREF_TINT_BACKGROUND,
        PREF_CUSTOM_ACCENT_COLOR,
        PREF_CUSTOM_BG_COLOR,
        PREF_OVERRIDE_COLOR_ACCENT,
        PREF_OVERRIDE_BGCOLOR,
        PREF_THEME_STYLE,
        PREF_COLOR_SOURCE,
        PREF_WHOLE_PALETTE,
        "peek_display_style",
        "peek_display_location"
    };
    
    public static final String[] SYSTEM_SETTINGS_NOTIFY_ONLY_KEYS = {
        VOLUME_STYLE
    };
    
    public static final String[] SECURE_SETTINGS_NOTIFY_ONLY_KEYS = {
    };
    
    public static final String[] LINEAGE_SECURE_SETTINGS_KEYS = {
        LineageSettings.Secure.QS_SHOW_AUTO_BRIGHTNESS,
        LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER,
        LineageSettings.Secure.QS_BRIGHTNESS_SLIDER_POSITION
    };
    
    public static final String[] LINEAGE_SYSTEM_SETTINGS_KEYS = {
    };
}
