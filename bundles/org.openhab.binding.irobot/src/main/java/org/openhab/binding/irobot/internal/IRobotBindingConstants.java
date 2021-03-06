/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.irobot.internal;

import javax.net.ssl.TrustManager;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.io.net.http.TrustAllTrustManager;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link IRobotBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author hkuhn42 - Initial contribution
 * @author Pavel Fedin - rename and update
 */
@NonNullByDefault
public class IRobotBindingConstants {

    public static final String BINDING_ID = "irobot";

    // List of all type UIDs
    public static final ThingTypeUID THING_TYPE_ROOMBA = new ThingTypeUID(BINDING_ID, "roomba");
    public static final String CHANNEL_TYPE_NUMBER = "number";
    public static final String CHANNEL_TYPE_TEXT = "text";

    // Something goes wrong...
    public static final String UNKNOWN = "UNKNOWN";

    // Family definitions
    public static final String ROOMBA_980 = "Roomba980";
    public static final String ROOMBA_I7 = "RoombaI7";
    public static final String BRAAVA_M6 = "BraavaM6";

    // Common channel IDs
    public static final String CHANNEL_JSON = "json";

    /**
     * Network group ID and channels within
     */
    public static final String NETWORK_GROUP_ID = "network";
    public static final String CHANNEL_NETWORK_ADDRESS = "address";
    public static final String CHANNEL_NETWORK_BSSID = "bssid";
    public static final String CHANNEL_NETWORK_DHCP = "dhcp";
    public static final String CHANNEL_NETWORK_DNS1 = "primary_dns";
    public static final String CHANNEL_NETWORK_DNS2 = "secondary_dns";
    public static final String CHANNEL_NETWORK_GATEWAY = "gateway";
    public static final String CHANNEL_NETWORK_MAC = "mac";
    public static final String CHANNEL_NETWORK_MASK = "mask";
    public static final String CHANNEL_NETWORK_NOISE = "noise";
    public static final String CHANNEL_NETWORK_SECURITY = "security";
    public static final String CHANNEL_NETWORK_SSID = "ssid";
    public static final String CHANNEL_NETWORK_RSSI = "rssi";
    public static final String CHANNEL_NETWORK_SNR = "snr";

    /**
     * Position group ID and channels within
     */
    public static final String POSITION_GROUP_ID = "position";
    public static final String CHANNEL_POSITION_X = "x";
    public static final String CHANNEL_POSITION_Y = "y";
    public static final String CHANNEL_POSITION_THETA = "theta";

    /**
     * State group ID, channels within and possible states
     */
    public static final String STATE_GROUP_ID = "state";
    public static final String CHANNEL_STATE_BIN = "bin";
    public static final String CHANNEL_STATE_CHARGE = "charge";
    public static final String CHANNEL_STATE_TYPE = "type";
    public static final String CHANNEL_STATE_MISSIONS = "missions";
    public static final String STATE_BIN_OK = "ok";
    public static final String STATE_BIN_FULL = "full";
    public static final String STATE_BIN_REMOVED = "removed";

    /**
     * Schedule group ID and channels within
     * iRobot's JSON lists weekdays starting from Saturday
     */
    public static final String SCHEDULE_GROUP_ID = "schedule";
    // @formatter:off
    public static final String[] DAY_OF_WEEK = {
            "sunday", "monday", "tuesday", "wednesday", "thirsday", "friday", "saturday"
    };
    // @formatter:on
    public static final int DEFAULT_HOUR = 12;
    public static final int DEFAULT_MINUTE = 0;

    /**
     * Control group ID, channels within and possible commands
     */
    public static final String CONTROL_GROUP_ID = "control";
    public static final String CHANNEL_CONTROL_EDGE_CLEAN = "edge_clean";
    public static final String CHANNEL_CONTROL_ALWAYS_FINISH = "always_finish";

    public static final String CHANNEL_CONTROL_POWER_BOOST = "power_boost";
    public static final String BOOST_AUTO = "auto";
    public static final String BOOST_PERFORMANCE = "performance";
    public static final String BOOST_ECO = "eco";

    public static final String CHANNEL_CONTROL_CLEAN_PASSES = "clean_passes";
    public static final String PASSES_AUTO = "auto";
    public static final String PASSES_1 = "1";
    public static final String PASSES_2 = "2";

    public static final String CHANNEL_CONTROL_AUDIO = "audio";
    public static final String CHANNEL_CONTROL_LANGUAGE = "language";
    public static final String CHANNEL_CONTROL_MAP_LEARN = "learn_map";
    public static final String CHANNEL_CONTROL_MAP_UPLOAD = "upload_map";

    public static final String CHANNEL_CONTROL_COMMAND = "command";
    public static final String COMMAND_CLEAN = "clean";
    public static final String COMMAND_CLEAN_REGIONS = "cleanRegions";
    public static final String COMMAND_DOCK = "dock";
    public static final String COMMAND_SPOT = "spot";
    public static final String COMMAND_PAUSE = "pause";
    public static final String COMMAND_RESUME = "resume";
    public static final String COMMAND_START = "start";
    public static final String COMMAND_STOP = "stop";
    public static final String COMMAND_UI = "ui";

    /**
     * Clean mission group ID, channels within and possible commands
     */
    public static final String MISSION_GROUP_ID = "mission";
    public static final String CHANNEL_MISSION_CYCLE = "cycle";
    public static final String CHANNEL_MISSION_ERROR = "error";
    public static final String CHANNEL_MISSION_MAP = "map";
    public static final String CHANNEL_MISSION_PHASE = "phase";

    /**
     * Lifetime statistics group ID and channels within
     */
    public static final String STATISTICS_GROUP_ID = "statistics";
    public static final String CHANNEL_STATISTICS_DURATION = "duration";
    public static final String CHANNEL_STATISTICS_MISSION_COUNT = "mission_count";

    /**
     * Internal group IDs, there are no channels on GUI
     */
    public static final String INTERNAL_GROUP_ID = "internal";
    public static final String CHANNEL_INTERNAL_LAST_COMMAND = "command";

    public static final int MQTT_PORT = 8883;
    public static final int UDP_PORT = 5678;
    public static final TrustManager[] TRUST_MANAGERS = { TrustAllTrustManager.getInstance() };

    public static final String ROBOT_BLID = "blid";
    public static final String ROBOT_PASSWORD = "password";
}
