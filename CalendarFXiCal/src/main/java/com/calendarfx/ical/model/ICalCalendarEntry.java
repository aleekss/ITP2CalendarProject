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

package com.calendarfx.ical.model;

import com.calendarfx.model.Entry;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Summary;

import java.util.Optional;

public class ICalCalendarEntry extends Entry<VEvent> {

    public ICalCalendarEntry(VEvent event) {
        Optional<Summary> summary = event.getProperty(Property.SUMMARY);
        if (summary.isPresent()) {
            setTitle(summary.get().getValue());
        }
        Optional<Location> optionalLocation = event.getProperty(Property.LOCATION);
        if (optionalLocation.isPresent()) {
            setLocation(optionalLocation.get().getValue());
        }
    }
}
