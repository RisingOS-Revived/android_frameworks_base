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

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.media.session.MediaSessionLegacyHelper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AiUtils  {

    private final static String TAG = "AiUtils"; 

    private static final String ACTION_ASSISTANT_STATE_CHANGED = "com.android.server.policy.ASSISTANT_STATE_CHANGED";
    private static final String ASSISTANT_STATE = "assistant_listening";

    public static boolean isMediaPlaybackCommand(String command) {
        return containsAny(command, AiAssistantResponses.MEDIA_ACTION_KEYWORDS) 
               && containsAny(command, AiAssistantResponses.MEDIA_TYPE_KEYWORDS);
    }

    public static boolean containsAny(String command, List<String> commands) {
        for (String cmd : commands) {
            if (command.contains(cmd)) {
                return true;
            }
        }
        return false;
    }
    
    public static boolean matchesAny(String command, List<String> keywords) {
        return keywords.stream().anyMatch(command::contains);
    }
    
    public static List<String> getInstalledAppList(Context context) {
        final List<String> launchableAppLabels = new ArrayList<>();
        List<ApplicationInfo> packages = context.getPackageManager().getInstalledApplications(0);
        for (ApplicationInfo appInfo : packages) {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(appInfo.packageName);
            if (launchIntent != null) {
                String label = context.getPackageManager().getApplicationLabel(appInfo).toString().toLowerCase();
                launchableAppLabels.add(label);
            }
        }
        return launchableAppLabels;
    }

    public static String getPackageNameFromAppName(Context context, String appName) {
        List<ApplicationInfo> packages = context.getPackageManager().getInstalledApplications(0);
        for (ApplicationInfo appInfo : packages) {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(appInfo.packageName);
            if (launchIntent != null) {
                String label = context.getPackageManager().getApplicationLabel(appInfo).toString().toLowerCase();
                if (label.equalsIgnoreCase(appName)) {
                    return appInfo.packageName;
                }
            }
        }
        return null;
    }

    public static String extractAppName(Context context, String command) {
        String[] words = command.toLowerCase().split("\\s+");
        int triggerIndex = -1;
        for (int i = 0; i < words.length; i++) {
            if (words[i].equals("open") || words[i].equals("launch")) {
                triggerIndex = i;
                break;
            }
        }
        if (triggerIndex == -1 || triggerIndex + 1 >= words.length) {
            return null;
        }
        StringBuilder appNameBuilder = new StringBuilder();
        for (int i = triggerIndex + 1; i < words.length; i++) {
            if (words[i].equals("in") || words[i].equals("free") || words[i].equals("form")) {
                break;
            }
            appNameBuilder.append(words[i]).append(" ");
        }
        String userQuery = appNameBuilder.toString().trim();
        String bestMatch = null;
        double bestSimilarity = 0.0;
        for (String installedApp : getInstalledAppList(context)) {
            if (installedApp.contains(userQuery)) {
                return installedApp;
            }
            double similarityScore = similarity(userQuery, installedApp);
            if (similarityScore > bestSimilarity && similarityScore > 0.5) {
                bestSimilarity = similarityScore;
                bestMatch = installedApp;
            }
        }
        return bestMatch;
    }

    public static String extractRingerMode(String command) {
        if (command.contains("silent")) {
            return "silent";
        } else if (command.contains("vibrate")) {
            return "vibrate";
        } else if (command.contains("normal")) {
            return "normal";
        }
        return null;
    }
    
    public static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(dp[i - 1][j - 1] 
                        + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1), 
                        Math.min(dp[i - 1][j] + 1, 
                        dp[i][j - 1] + 1));
                }
            }
        }
        return dp[a.length()][b.length()];
    }
    
    public static double similarity(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        int dist = levenshteinDistance(a.toLowerCase(), b.toLowerCase());
        return 1.0 - (double) dist / maxLen;
    }

    public static int toggleBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
                return 1;
            } else if (bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.disable();
                return 0;
            }
        }
        return -1;
    }
    
    public static void playSong(Context context) {
        dispatchMediaKeyWithWakeLockToMediaSession(context, KeyEvent.KEYCODE_MEDIA_PLAY);
    }
    
    public static void pauseSong(Context context) {
        dispatchMediaKeyWithWakeLockToMediaSession(context, KeyEvent.KEYCODE_MEDIA_PAUSE);
    }

    public static void prevSong(Context context) {
        dispatchMediaKeyWithWakeLockToMediaSession(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }
    
    public static void nextSong(Context context) {
        dispatchMediaKeyWithWakeLockToMediaSession(context, KeyEvent.KEYCODE_MEDIA_NEXT);
    }
    
    public static void dispatchMediaKeyWithWakeLockToMediaSession(Context context, final int keycode) {
        final MediaSessionLegacyHelper helper = MediaSessionLegacyHelper.getHelper(context);
        if (helper == null) {
            return;
        }
        KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keycode, 0);
        helper.sendMediaButtonEvent(event, true);
        event = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
        helper.sendMediaButtonEvent(event, true);
    }
    
    public static boolean openApp(Context context, String appName) {
        if (appName == null) {
            return false;
        }
        String packageName = AiUtils.getPackageNameFromAppName(context, appName);
        if (packageName != null) {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(intent);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    
    public static void sendAssistantActionStateChange(Context context, boolean isListening) {
        Intent intent = new Intent(ACTION_ASSISTANT_STATE_CHANGED);
        intent.putExtra(ASSISTANT_STATE, isListening);
        context.sendBroadcast(intent);
    }
    
    public static String getResponseFromGemini(String apiKey, String prompt, boolean isBrief) {
        if (isBrief) {
            prompt = "Please provide a brief summary: " + prompt;
        }
        try {
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=" + apiKey;
            Log.d(TAG, "API URL: " + apiUrl);
            JSONObject jsonBody = new JSONObject();
            JSONArray contentsArray = new JSONArray();
            JSONObject contentsObj = new JSONObject();
            JSONArray partsArray = new JSONArray();
            JSONObject partObj = new JSONObject();
            partObj.put("text", prompt);
            Log.d(TAG, "Prompt: " + prompt);
            partsArray.put(partObj);
            contentsObj.put("parts", partsArray);
            contentsArray.put(contentsObj);
            jsonBody.put("contents", contentsArray);
            Log.d(TAG, "Request body: " + jsonBody.toString());
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(jsonBody.toString().getBytes("UTF-8"));
            os.close();
            Log.d(TAG, "Request sent successfully.");
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine);
            }
            br.close();
            Log.d(TAG, "Response: " + response.toString());
            JSONObject jsonResponse = new JSONObject(response.toString());
            JSONArray candidates = jsonResponse.getJSONArray("candidates");
            JSONObject firstCandidate = candidates.getJSONObject(0);
            JSONObject content = firstCandidate.getJSONObject("content");
            JSONArray responseParts = content.getJSONArray("parts");
            String result = responseParts.getJSONObject(0).getString("text");
            result = result.replaceAll("\\*", "").replaceAll("\\n+", " ").replaceAll("\\s+", " ").trim();
            Log.d(TAG, "Parsed result: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error occurred", e);
            return "Sorry, i'm having trouble staying in touch with Gemini.";
        }
    }

    public static String getBriefResponseFromGemini(String apiKey, String prompt) {
        return getResponseFromGemini(apiKey, prompt, true);
    }
    
    public static boolean openChatGPT(Context context) {
        Intent intent = new Intent();
        intent.setClassName("com.openai.chatgpt", "com.openai.voice.assistant.AssistantActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
