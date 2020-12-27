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
package org.openhab.binding.irobot;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.irobot.internal.utils.JSONUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * @author Alexander Falkenstern - Initial contribution
 */
@NonNullByDefault
public class JSONUtilsTest {
    private @NonNullByDefault({}) JsonElement tree;
    private final JsonParser jsonParser = new JsonParser();

    @BeforeEach
    public void setupTest() {
        InputStream stream = JSONUtilsTest.class.getResourceAsStream("roomba.json");
        assertNotNull(stream, "Couldn't find JSON data for test");

        try {
            tree = jsonParser.parse(new String(stream.readAllBytes()));
        } catch (IOException | JsonParseException exception) {
            fail("Couldn't read JSON data for test", exception);
        }
    }

    @AfterEach
    public void cleanupTest() {
    }

    @Test
    public void getAsStringTest() {
        String signal = JSONUtils.getAsString("name", tree);
        assertNotNull(signal, "Got null for existing key");

        String invalid = JSONUtils.getAsString("invalid", tree);
        assertNull(invalid, "Got non null for not existing key");
    }

    @Test
    public void getAsDecimalTest() {
        BigDecimal rssi = JSONUtils.getAsDecimal("rssi", tree);
        assertNotNull(rssi, "Got null for numeric value");

        BigDecimal name = JSONUtils.getAsDecimal("name", tree);
        assertNull(name, "Got non null for string value");
    }

    @Test
    public void subtreeParsingTest() {
        String json = JSONUtils.getAsJSONString("pose", tree);
        assertNotNull(json, "Got null for existing key");

        JsonElement pose = jsonParser.parse(json.toString());
        BigDecimal xPos = JSONUtils.getAsDecimal("x", pose);
        assertNotNull(xPos, "Got null for numeric value");
    }
}
