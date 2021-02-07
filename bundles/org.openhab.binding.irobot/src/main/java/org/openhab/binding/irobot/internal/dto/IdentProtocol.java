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
package org.openhab.binding.irobot.internal.dto;

import static org.openhab.binding.irobot.internal.IRobotBindingConstants.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import org.openhab.binding.irobot.internal.utils.JSONUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * iRobot discovery and identification protocol
 *
 * @author Pavel Fedin - Initial contribution
 *
 */
public class IdentProtocol {
    private static final String UDP_PACKET_CONTENTS = "irobotmcs";
    private static final int REMOTE_UDP_PORT = 5678;

    public static DatagramSocket sendRequest(InetAddress host) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        socket.setReuseAddress(true);

        byte[] packetContents = UDP_PACKET_CONTENTS.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(packetContents, packetContents.length, host, REMOTE_UDP_PORT);

        socket.send(packet);
        return socket;
    }

    public static DatagramPacket receiveResponse(DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket incomingPacket = new DatagramPacket(buffer, buffer.length);

        socket.setSoTimeout(1000); // One second
        socket.receive(incomingPacket);

        return incomingPacket;
    }

    public static IdentData decodeResponse(DatagramPacket packet) throws JsonParseException {
        /*
         * packet is a JSON of the following contents (addresses are undisclosed):
         * @formatter:off
         * {
         *   "ver":"3",
         *   "hostname":"Roomba-3168820480607740",
         *   "robotname":"Roomba",
         *   "ip":"XXX.XXX.XXX.XXX",
         *   "mac":"XX:XX:XX:XX:XX:XX",
         *   "sw":"v2.4.6-3",
         *   "sku":"R981040",
         *   "nc":0,
         *   "proto":"mqtt",
         *   "cap":{
         *     "pose":1,
         *     "ota":2,
         *     "multiPass":2,
         *     "carpetBoost":1,
         *     "pp":1,
         *     "binFullDetect":1,
         *     "langOta":1,
         *     "maps":1,
         *     "edge":1,
         *     "eco":1,
         *     "svcConf":1
         *   }
         * }
         * @formatter:on
         */
        final JsonParser jsonParser = new JsonParser();
        String reply = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);

        final JsonElement tree = jsonParser.parse(reply);
        BigDecimal version = JSONUtils.getAsDecimal("ver", tree);
        String hostname = JSONUtils.getAsString("hostname", tree);
        if ((version != null) && (hostname != null)) {
            // Synthesize missing properties. This also comes from Roomba980-Python. Comments there say that
            // "Roomba" prefix is used by 980 and below, "iRobot" prefix is used by i7.
            String[] parts = hostname.split("-");
            if (parts.length == 2) {
                IdentData data = new IdentData();
                data.version = version.intValue();
                data.name = JSONUtils.getAsString("robotname", tree);
                data.address = JSONUtils.getAsString("ip", tree);
                if (parts[0].equals("Roomba")) {
                    data.product = ROOMBA_980;
                } else if (parts[0].equals("iRobot")) {
                    data.product = ROOMBA_I7;
                } else {
                    data.product = UNKNOWN;
                }
                data.blid = parts[1];
                return data;
            }
        }
        return null;
    }

    public static class IdentData {
        public static int MIN_SUPPORTED_VERSION = 2;

        public int version;
        public String name;
        public String address;
        public String product;
        public String blid;
    }
}
