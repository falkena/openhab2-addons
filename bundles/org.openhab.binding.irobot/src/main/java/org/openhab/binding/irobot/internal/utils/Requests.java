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
package org.openhab.binding.irobot.internal.utils;

import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * iRobot MQTT requests
 *
 * @author Pavel Fedin - Initial contribution
 *
 */
public class Requests {
    public interface Request {
        public String getTopic();

        public byte[] getPayload();
    }

    /*
     * public static class CleanRoomsRequest extends CommandRequest {
     * public int ordered;
     * public String pmap_id;
     * public List<Region> regions;
     * 
     * public CleanRoomsRequest(String cmd, String mapId, String[] regions) {
     * super(cmd);
     * ordered = 1;
     * pmap_id = mapId;
     * this.regions = Arrays.stream(regions).map(i -> new Region(i)).collect(Collectors.toList());
     * }
     * 
     * public static class Region {
     * public String region_id;
     * public String type;
     * 
     * public Region(String id) {
     * this.region_id = id;
     * this.type = "rid";
     * }
     * }
     * }
     */
    public static class CommandRequest implements Request {
        private JsonElement command;
        private final JsonObject payload = new JsonObject();

        public CommandRequest(JsonElement command) {
            this.command = command;
        }

        @Override
        public String getTopic() {
            return "cmd";
        }

        @Override
        public byte[] getPayload() {
            payload.add("command", command);
            payload.addProperty("time", System.currentTimeMillis() / 1000);
            payload.addProperty("initiator", "openhab");
            return payload.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    public static class DeltaRequest implements Request {
        private JsonElement element;
        private final JsonObject payload = new JsonObject();

        public DeltaRequest(final JsonElement element) {
            this.element = element;
        }

        @Override
        public String getTopic() {
            return "delta";
        }

        @Override
        public byte[] getPayload() {
            payload.add("state", element);
            return payload.toString().getBytes(StandardCharsets.UTF_8);
        }
    }
}
