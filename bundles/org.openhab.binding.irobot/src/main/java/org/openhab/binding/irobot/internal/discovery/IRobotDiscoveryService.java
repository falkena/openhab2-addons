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
package org.openhab.binding.irobot.internal.discovery;

import static org.openhab.binding.irobot.internal.IRobotBindingConstants.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.irobot.internal.utils.JSONUtils;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.net.NetUtil;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Discovery service for iRobots
 *
 * @author Pavel Fedin - Initial contribution
 *
 */
@NonNullByDefault
@Component(service = DiscoveryService.class, configurationPid = "discovery.irobot")
public class IRobotDiscoveryService extends AbstractDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(IRobotDiscoveryService.class);

    private final Runnable scanner;
    private @Nullable ScheduledFuture<?> backgroundFuture;

    public IRobotDiscoveryService() {
        super(Collections.singleton(THING_TYPE_ROOMBA), 30, true);
        scanner = createScanner();
    }

    @Override
    protected void startBackgroundDiscovery() {
        stopBackgroundScan();
        backgroundFuture = scheduler.scheduleWithFixedDelay(scanner, 0, 60, TimeUnit.SECONDS);
    }

    @Override
    protected void stopBackgroundDiscovery() {
        stopBackgroundScan();
        super.stopBackgroundDiscovery();
    }

    private void stopBackgroundScan() {
        ScheduledFuture<?> scan = backgroundFuture;

        if (scan != null) {
            scan.cancel(true);
            backgroundFuture = null;
        }
    }

    @Override
    protected void startScan() {
        scheduler.execute(scanner);
    }

    private Runnable createScanner() {
        return () -> {
            Set<String> robots = new HashSet<>();
            long timestampOfLastScan = getTimestampOfLastScan();
            for (InetAddress broadcastAddress : getBroadcastAddresses()) {
                logger.debug("Starting broadcast for {}", broadcastAddress.toString());

                final byte[] bRequest = "irobotmcs".getBytes(StandardCharsets.UTF_8);
                DatagramPacket request = new DatagramPacket(bRequest, bRequest.length, broadcastAddress, UDP_PORT);
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setSoTimeout(1000); // One second
                    socket.setReuseAddress(true);
                    socket.setBroadcast(true);
                    socket.send(request);

                    byte @Nullable [] reply = null;
                    while ((reply = receive(socket)) != null) {
                        robots.add(new String(reply, StandardCharsets.UTF_8));
                    }
                } catch (IOException exception) {
                    logger.debug("Error sending broadcast: {}", exception.toString());
                }
            }

            final JsonParser jsonParser = new JsonParser();
            for (final String json : robots) {
                final JsonElement tree = jsonParser.parse(json);

                // Only firmware version 2 and above are supported via MQTT, therefore check it
                final BigDecimal version = JSONUtils.getAsDecimal("ver", tree);
                final String protocol = JSONUtils.getAsString("proto", tree);
                if ((version.intValue() > 1) && "mqtt".equalsIgnoreCase(protocol)) {
                    String address = JSONUtils.getAsString("ip", tree);
                    String mac = JSONUtils.getAsString("mac", tree);
                    String sku = JSONUtils.getAsString("sku", tree);
                    if ((address != null) && (sku != null) && (mac != null)) {
                        ThingUID thingUID = new ThingUID(THING_TYPE_ROOMBA, mac.replace(":", ""));
                        DiscoveryResultBuilder builder = DiscoveryResultBuilder.create(thingUID);
                        builder = builder.withProperty("mac", mac).withRepresentationProperty("mac");

                        sku = sku.toUpperCase();
                        String family = UNKNOWN;
                        if (sku.startsWith("R980")) {
                            family = ROOMBA_980;
                        } else if (sku.startsWith("I7")) {
                            family = ROOMBA_I7;
                        } else if (sku.startsWith("M6")) {
                            family = BRAAVA_M6;
                        }
                        builder = builder.withProperty("family", family);
                        builder = builder.withProperty("address", address);

                        String name = JSONUtils.getAsString("robotname", tree);
                        builder = builder.withLabel("iRobot " + (name != null ? name : UNKNOWN));
                        thingDiscovered(builder.build());
                    }
                }
            }

            removeOlderResults(timestampOfLastScan);
        };
    }

    private byte @Nullable [] receive(DatagramSocket socket) {
        try {
            final byte[] bReply = new byte[1024];
            DatagramPacket reply = new DatagramPacket(bReply, bReply.length);
            socket.receive(reply);
            return Arrays.copyOfRange(reply.getData(), reply.getOffset(), reply.getLength());
        } catch (IOException exception) {
            // This is not really an error, eventually we get a timeout due to a loop in the caller
            return null;
        }
    }

    private List<InetAddress> getBroadcastAddresses() {
        ArrayList<InetAddress> addresses = new ArrayList<>();

        for (String broadcastAddress : NetUtil.getAllBroadcastAddresses()) {
            try {
                addresses.add(InetAddress.getByName(broadcastAddress));
            } catch (UnknownHostException exception) {
                // The broadcastAddress is supposed to be raw IP, not a hostname, like 192.168.0.255.
                // Getting UnknownHost on it would be totally strange, some internal system error.
                logger.warn("Error broadcasting to {}: {}", broadcastAddress, exception.getMessage());
            }
        }

        return addresses;
    }
}
