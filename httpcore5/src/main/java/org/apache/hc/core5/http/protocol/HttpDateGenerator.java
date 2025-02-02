/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.core5.http.protocol;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.TimeZone;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Generates a date in the format required by the HTTP protocol.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class HttpDateGenerator {

    private static final int GRANULARITY_MILLIS = 1000;

    /** Date format pattern used to generate the header in RFC 1123 format. */
    public static final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";

    /**
     * @deprecated This attribute is no longer supported as a part of the public API.
     * The time zone to use in the date header.
     */
    @Deprecated
    public static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    public static final ZoneId GMT_ID = ZoneId.of("GMT");

    /** Singleton instance. */
    public static final HttpDateGenerator INSTANCE = new HttpDateGenerator(PATTERN_RFC1123, GMT_ID);

    private final DateTimeFormatter dateTimeFormatter;
    private long dateAsMillis;
    private String dateAsText;
    private ZoneId zoneId;

    HttpDateGenerator() {
        dateTimeFormatter =new DateTimeFormatterBuilder()
                .parseLenient()
                .parseCaseInsensitive()
                .appendPattern(PATTERN_RFC1123)
                .toFormatter();
        zoneId = GMT_ID;

    }

    private HttpDateGenerator(final String pattern, final ZoneId zoneId) {
        dateTimeFormatter = new DateTimeFormatterBuilder()
                .parseLenient()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter();
        this.zoneId =  zoneId;
    }

    public synchronized String getCurrentDate() {
        final long now = System.currentTimeMillis();
        if (now - this.dateAsMillis > GRANULARITY_MILLIS) {
            // Generate new date string
            dateAsText = dateTimeFormatter.format(Instant.now().atZone(zoneId));
            dateAsMillis = now;
        }
        return dateAsText;
    }

}
