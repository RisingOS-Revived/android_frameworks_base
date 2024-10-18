/*
 * Copyright (C) 2023-2024 The risingOS Android Project
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
package com.android.server.policy;

import java.util.Arrays;
import java.util.List;

public class AiAssistantResponses {

    public final static List<String> DEVICE_ACTIONS_TORCH_TRIGGER_KEYWORDS = Arrays.asList(
        "turn", "trigger", "close", "on", "off"
    );

    public final static List<String> DEVICE_ACTIONS_TORCH_KEYWORDS = Arrays.asList(
        "torch", "flashlight", "light"
    );

    public final static List<String> DEVICE_ACTIONS_RINGER_MODE_HOT_WORDS = Arrays.asList(
        "silent", "vibrate", "normal"
    );

    public final static List<String> DEVICE_ACTIONS_SEARCH_HOT_WORDS = Arrays.asList(
        "what", "when", "why"
    );

    public final static List<String> HOTWORDS_CLEAR_NOTIFICATIONS = Arrays.asList(
        "clear all notifications"
    );

    public final static List<String> HOTWORDS_SHOW_VOLUME_PANEL = Arrays.asList(
        "show volume panel", "show volume"
    );

    public final static List<String> HOTWORDS_APP_LAUNCH = Arrays.asList(
        "open", "launch"
    );

    public final static List<String> HOTWORDS_TOGGLE_BLUETOOTH = Arrays.asList(
        "turn on bluetooth", "turn off bluetooth", "activate bluetooth", "de-activate bluetooth"
    );

    public final static List<String> HOTWORDS_CHAT_GPT = Arrays.asList(
        "chat gpt"
    );

    public final static List<String> MEDIA_ACTION_KEYWORDS = Arrays.asList(
        "play", "stop", "skip", "next", "previous", "return"
    );

    public final static List<String> MEDIA_TYPE_KEYWORDS = Arrays.asList(
        "song", "music", "media"
    );

    public final static List<String> MEDIA_PLAY_HOT_WORDS = Arrays.asList(
        "play"
    );

    public final static List<String> MEDIA_PAUSE_STOP_HOT_WORDS = Arrays.asList(
        "stop", "pause", "halt"
    );

    public final static List<String> MEDIA_NEXT_HOT_WORDS = Arrays.asList(
        "next", "skip", "fast forward"
    );

    public final static List<String> MEDIA_PREV_HOT_WORDS = Arrays.asList(
        "previous", "return", "last"
    );
}
