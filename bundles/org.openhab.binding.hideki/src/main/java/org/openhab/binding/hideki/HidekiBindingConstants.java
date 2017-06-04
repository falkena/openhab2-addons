/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hideki;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link HidekiBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
@NonNullByDefault
public class HidekiBindingConstants {

    private static final String BINDING_ID = "hideki";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_WEATHERSTATION = new ThingTypeUID(BINDING_ID, "weatherstation");

    // Sensor type IDs
    public static final Integer ANEMOMETER_TYPE_ID = Integer.valueOf(0x0C);
    public static final Integer THERMOMETER_TYPE_ID = Integer.valueOf(0x1E);
    public static final Integer PLUVIOMETER_TYPE_ID = Integer.valueOf(0x0E);
    public static final Integer UVMETER_TYPE_ID = Integer.valueOf(0x0D);

    // Common channels
    public static final String RECEIVED_UPDATE = "updated";
    public static final String BATTERY = "battery";
    public static final String RSSI_VALUE = "rssi";
    public static final String SENSOR_ID = "sensor";
    public static final String SENSOR_CHANNEL = "channel";
    public static final String SENSOR_TYPE = "type";
    public static final String MESSAGE_NUMBER = "message";
    public static final String TEMPERATURE = "temperature";

    // Anemometer channels
    public static final String CHILL = "chill";
    public static final String SPEED = "speed";
    public static final String GUST = "gust";
    public static final String DIRECTION = "direction";

    // Thermometer channels
    public static final String HUMIDITY = "humidity";

    // Pluviometer channels
    public static final String RAIN_LEVEL = "rain";

    // UV-Meter channels
    public static final String MED = "med";
    public static final String UV_INDEX = "uv";

    public static final Map<Integer, @Nullable String> SENSOR_GROUPS;
    static {
        Map<Integer, String> buffer = new HashMap<>();
        buffer.put(ANEMOMETER_TYPE_ID, "anemometer");
        buffer.put(THERMOMETER_TYPE_ID, "thermometer");
        buffer.put(PLUVIOMETER_TYPE_ID, "pluviometer");
        buffer.put(UVMETER_TYPE_ID, "uvmeter");
        SENSOR_GROUPS = Collections.unmodifiableMap(buffer);
    }

}
