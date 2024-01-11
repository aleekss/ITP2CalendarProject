/*
 *  Copyright (C) 2017 Dirk Lemmermann Software & Consulting (dlsc.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.calendarfx.ical;

import com.calendarfx.ical.model.ICalCalendar;
import com.calendarfx.model.Calendar;
import com.calendarfx.model.CalendarSource;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Created by gdiaz on 7/01/2017.
 */
public final class ICalRepository {

    private static final String SETTINGS_DIR = "/.store/calendarfx/";

    private static final String SETTINGS_FILE = "iCalCalendars";

    private static final Map<WebCalendarData, ICalCalendar> webCalendars = new LinkedHashMap<>();

    public static CalendarSource familyCalendars = new CalendarSource("Family");

    public static CalendarSource communityCalendars = new CalendarSource("Others");

    public static DoubleProperty workDoneProperty = new SimpleDoubleProperty();

    public static DoubleProperty totalWorkProperty = new SimpleDoubleProperty();

    public static StringProperty messageProperty = new SimpleStringProperty();

    public static void loadSources() throws IOException, ParserException {

        loadWebSource();

        if (true) { //familyCalendars.getCalendars().isEmpty()) {
            totalWorkProperty.set(10);
            String icsFileUrl = "https://www.feiertage-oesterreich.at/kalender-download/ics/schulferien-ooe.ics";
            String icsFileUrl2 = "webcal://www.wien.gv.at/amtshelfer/feiertage/ics/feiertage.ics";
            String calendarName = "Austrian Holidays";
            Calendar.Style calendarStyle = Calendar.Style.STYLE2;
            CalendarSource calendarSource = ICalRepository.getCommunityCalendarSource();


            ICalRepository.createWebCalendar(icsFileUrl, calendarName, calendarStyle, calendarSource);
            ICalRepository.createWebCalendar(icsFileUrl2, calendarName, calendarStyle, calendarSource);


        }
    }

    public static void loadWebSource() {
        try (FileInputStream fin = new FileInputStream(new File(System.getProperty("user.home") + SETTINGS_DIR, SETTINGS_FILE)); ObjectInputStream ois = new ObjectInputStream(fin)) {

            List<WebCalendarData> webCalendars = (List<WebCalendarData>) ois.readObject();

            if (webCalendars != null) {
                totalWorkProperty.set(webCalendars.size());
                double progress = 0;
                for (WebCalendarData data : webCalendars) {
                    putWebCalendar(data, data.isFamily() ? familyCalendars : communityCalendars);
                    progress++;
                    workDoneProperty.set(progress);
                }
            }
        } catch (FileNotFoundException ex) {
            // we can ignore this, this will happen first time we start the app
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static CalendarSource getCommunityCalendarSource() {
        return communityCalendars;
    }

    public static boolean existsWebCalendar(String url) {
        return getWebCalendar(url) != null;
    }

    public static ICalCalendar getWebCalendar(String url) {
        for (WebCalendarData webCalendar : webCalendars.keySet()) {
            if (webCalendar.getUrl().equals(url)) {
                return webCalendars.get(webCalendar);
            }
        }
        return null;
    }

    public static void createWebCalendar(String url, String name, Calendar.Style style, CalendarSource source) {
        if (url == null || url.isEmpty() || name == null || name.isEmpty() || style == null) {
            return;
        }

        try {
            WebCalendarData data = new WebCalendarData(url, name, style, source == familyCalendars);

            putWebCalendar(data, source);

            List<WebCalendarData> webCalendarDatas = new ArrayList<>();
            webCalendarDatas.addAll(webCalendars.keySet());

            if (!webCalendarDatas.isEmpty()) {
                final File directory = new File(System.getProperty("user.home") + SETTINGS_DIR);
                boolean directoryExists = true;
                if (!directory.exists()) {
                    directoryExists = directory.mkdirs();
                }

                if (directoryExists) {
                    final File file = new File(directory, SETTINGS_FILE);

                    boolean fileExists = true;
                    if (!file.exists()) {
                        fileExists = file.createNewFile();
                    }

                    if (fileExists) {
                        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                            oos.writeObject(webCalendarDatas);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        } catch (Throwable t) {}
    }

    private static void putWebCalendar(WebCalendarData data, CalendarSource source) throws IOException, ParserException {
        ICalCalendar cal = getWebCalendar(data.getUrl());
        if (cal == null) {

            messageProperty.set("Calendar: " + data.getName());

            URL urlObj = new URL(data.getUrl().replace("webcal", "https"));

            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);

            CalendarBuilder builder = new CalendarBuilder();
            InputStream inputStream = conn.getInputStream();

            net.fortuna.ical4j.model.Calendar calendar = builder.build(inputStream);

            cal = new ICalCalendar(data.getName(), calendar);
            cal.setStyle(data.getStyle());

            webCalendars.put(data, cal);

            final ICalCalendar fcal = cal;

            Platform.runLater(() -> {
                source.getCalendars().add(fcal);
            });
        }


    }

    private static class WebCalendarData implements Serializable {

        private final String url;
        private final String name;
        private final Calendar.Style style;
        private final boolean family;

        public WebCalendarData(String url, String name, Calendar.Style style, boolean family) {
            this.url = url;
            this.name = name;
            this.style = style;
            this.family = family;
        }

        public boolean isFamily() {
            return family;
        }

        public String getUrl() {
            return url;
        }

        public String getName() {
            return name;
        }

        public Calendar.Style getStyle() {
            return style;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WebCalendarData that = (WebCalendarData) o;

            return Objects.equals(url, that.url);
        }

        @Override
        public int hashCode() {
            return url != null ? url.hashCode() : 0;
        }
    }

}
