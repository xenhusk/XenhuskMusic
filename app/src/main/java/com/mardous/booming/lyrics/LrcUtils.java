/*
 * Copyright (c) 2024 Christians Martínez Alvarado
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
package com.mardous.booming.lyrics;

import android.text.format.DateUtils;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcUtils {

    private static final Pattern LRC_LINE_PATTERN = Pattern.compile("((?:\\[.*?])+)(.*)");
    private static final Pattern LRC_TIME_PATTERN = Pattern.compile("\\[(\\d+):(\\d{2}(?:\\.\\d+)?)]");
    private static final Pattern LRC_ATTRIBUTE_PATTERN = Pattern.compile("\\[(\\D+):(.+)]");

    private static final float LRC_SECONDS_TO_MS_MULTIPLIER = 1000f;
    private static final int LRC_MINUTES_TO_MS_MULTIPLIER = 60 * 1000;

    @NonNull
    public static LrcLyrics parseLrcFromFile(@NonNull File file) {
        try {
            return parseInputStream(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return new LrcLyrics();
    }

    @NotNull
    public static LrcLyrics parse(@NonNull String lyrics) {
        return parseReader(new StringReader(lyrics));
    }

    @NonNull
    private static LrcLyrics parseInputStream(InputStream inputStream) {
        try (InputStreamReader isr = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return parseReader(isr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new LrcLyrics();
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    private static LrcLyrics parseReader(@NonNull final Reader reader) {
        long offset = 0;
        final List<LrcEntry> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line == null || line.trim().isEmpty()) continue;

                final Matcher attrMatcher = LRC_ATTRIBUTE_PATTERN.matcher(line);
                if (attrMatcher.find()) {
                    try {
                        final String attr = attrMatcher.group(1).toLowerCase().trim();
                        final String value = attrMatcher.group(2).toLowerCase().trim();
                        if ("offset".equals(attr)) {
                            offset = Integer.parseInt(value);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else {
                    final Matcher matcher = LRC_LINE_PATTERN.matcher(line);
                    if (matcher.find()) {
                        String time = matcher.group(1).trim();
                        String text = matcher.group(2).trim();

                        final Matcher timeMatcher = LRC_TIME_PATTERN.matcher(time);
                        while (timeMatcher.find()) {
                            int m = 0;
                            float s = 0f;
                            try {
                                m = Integer.parseInt(timeMatcher.group(1));
                                s = Float.parseFloat(timeMatcher.group(2));
                            } catch (NumberFormatException ex) {
                                ex.printStackTrace();
                            }
                            long ms = (long) (s * LRC_SECONDS_TO_MS_MULTIPLIER) + (long) m * LRC_MINUTES_TO_MS_MULTIPLIER;

                            lines.add(new LrcEntry(ms, text));
                        }
                    }
                }
            }

            return new LrcLyrics(offset, lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new LrcLyrics();
    }

    /**
     * 转为[分:秒]
     */
    public static String formatTime(long milli) {
        int m = (int) (milli / DateUtils.MINUTE_IN_MILLIS);
        int s = (int) ((milli / DateUtils.SECOND_IN_MILLIS) % 60);
        String mm = String.format(Locale.getDefault(), "%02d", m);
        String ss = String.format(Locale.getDefault(), "%02d", s);
        return mm + ":" + ss;
    }
}
