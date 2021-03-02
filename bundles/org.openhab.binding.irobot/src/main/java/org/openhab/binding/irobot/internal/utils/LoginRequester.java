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

/**
 * Helper functions to get blid and password. Seems pretty much reinventing a bicycle,
 * but it looks like HiveMq doesn't provide for sending and receiving custom packets.
 *
 * @author Pavel Fedin - Initial contribution
 * @author Alexander Falkenstern - Fix password fetching
 *
 */

import static org.openhab.binding.irobot.internal.IRobotBindingConstants.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.net.ssl.SSLContext;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

@NonNullByDefault
public class LoginRequester {
    public static @Nullable String getBlid(final String ip) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(1000); // One second
        socket.setReuseAddress(true);

        final byte[] bRequest = "irobotmcs".getBytes(StandardCharsets.UTF_8);
        DatagramPacket request = new DatagramPacket(bRequest, bRequest.length, InetAddress.getByName(ip), UDP_PORT);
        socket.send(request);

        final byte[] bReply = new byte[1024];
        DatagramPacket reply = new DatagramPacket(bReply, bReply.length);
        socket.receive(reply);

        final JsonParser jsonParser = new JsonParser();
        final JsonElement tree = jsonParser
                .parse(new String(reply.getData(), reply.getOffset(), reply.getLength(), StandardCharsets.UTF_8));

        String blid = JSONUtils.getAsString("robotid", tree);
        final String hostname = JSONUtils.getAsString("hostname", tree);
        if ((blid == null) && (hostname != null)) {
            String[] parts = hostname.split("-");
            if (parts.length == 2) {
                blid = parts[1];
            }
        }

        return blid;
    }

    public static @Nullable String getPassword(final String ip)
            throws KeyManagementException, NoSuchAlgorithmException, IOException {
        String password = null;

        SSLContext context = SSLContext.getInstance("SSL");
        context.init(null, TRUST_MANAGERS, new java.security.SecureRandom());

        Socket socket = context.getSocketFactory().createSocket(ip, MQTT_PORT);
        socket.setSoTimeout(3000);

        // 1st byte: MQTT reserved message: 0xF0
        // 2nd byte: Data length: 0x05
        // from 3d byte magic packet data: 0xEFCC3B2900
        final byte[] request = { (byte) 0xF0, (byte) 0x05, (byte) 0xEF, (byte) 0xCC, (byte) 0x3B, (byte) 0x29, 0x00 };
        socket.getOutputStream().write(request);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            socket.getInputStream().transferTo(buffer);
        } catch (IOException exception) {
            // Roomba 980 send no properly EOF, so eat the exception
        } finally {
            socket.close();
            buffer.flush();
        }

        final byte[] reply = buffer.toByteArray();
        if ((reply.length > request.length) && (reply.length == reply[1] + 2)) { // Add 2 bytes, see request doc above
            reply[1] = request[1]; // Hack, that we can find request packet in reply
            if (Arrays.equals(request, 0, request.length, reply, 0, request.length)) {
                password = new String(Arrays.copyOfRange(reply, request.length, reply.length), StandardCharsets.UTF_8);
            }
        }

        return password;
    }
}
