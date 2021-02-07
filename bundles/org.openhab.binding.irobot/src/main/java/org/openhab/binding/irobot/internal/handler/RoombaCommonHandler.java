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
package org.openhab.binding.irobot.internal.handler;

import static org.openhab.binding.irobot.internal.IRobotBindingConstants.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.irobot.internal.IRobotChannelContentProvider;
import org.openhab.binding.irobot.internal.RawMQTT;
import org.openhab.binding.irobot.internal.config.IRobotConfiguration;
import org.openhab.binding.irobot.internal.dto.IdentProtocol;
import org.openhab.binding.irobot.internal.utils.JSONUtils;
import org.openhab.binding.irobot.internal.utils.Requests;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttConnectionObserver;
import org.openhab.core.io.transport.mqtt.MqttConnectionState;
import org.openhab.core.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.core.io.transport.mqtt.reconnect.PeriodicReconnectStrategy;
import org.openhab.core.library.types.*;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.*;

/**
 * The {@link RoombaCommonHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author hkuhn42 - Initial contribution
 * @author Pavel Fedin - Rewrite for 900 series
 * @author Alexander Falkenstern - Add support for I7 series
 */
@NonNullByDefault
public class RoombaCommonHandler extends BaseThingHandler implements MqttConnectionObserver, MqttMessageSubscriber {
    private final Logger logger = LoggerFactory.getLogger(RoombaCommonHandler.class);

    private static final int RECONNECT_DELAY = 10000; // In milliseconds
    private @Nullable Future<?> reconnectReq;
    private @Nullable MqttBrokerConnection connection;

    private IRobotChannelContentProvider channelContentProvider;
    private AtomicReference<IRobotConfiguration> config = new AtomicReference<>();
    private ConcurrentHashMap<ChannelUID, State> lastState = new ConcurrentHashMap<>();

    public RoombaCommonHandler(Thing thing, IRobotChannelContentProvider channelContentProvider) {
        super(thing);
        this.channelContentProvider = channelContentProvider;
    }

    @Override
    public void initialize() {
        config.set(getConfigAs(IRobotConfiguration.class));
        for (Channel channel : thing.getChannels()) {
            lastState.put(channel.getUID(), UnDefType.UNDEF);
        }
        scheduler.execute(this::connect);
    }

    @Override
    public void dispose() {
        scheduler.execute(this::disconnect);
        for (Channel channel : thing.getChannels()) {
            lastState.put(channel.getUID(), UnDefType.UNDEF);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            final State value = lastState.get(channelUID);
            updateState(channelUID, Objects.requireNonNullElse(value, UnDefType.UNDEF));
        } else if (command instanceof OnOffType) {
            final JsonObject request = new JsonObject();
            final String channelId = channelUID.getIdWithoutGroup();
            if (CHANNEL_CONTROL_AUDIO.equals(channelId)) {
                request.addProperty("audio", command.equals(OnOffType.ON));
                sendRequest(new Requests.DeltaRequest(request));
            } else if (CHANNEL_CONTROL_ALWAYS_FINISH.equals(channelId)) {
                // Binding operate with inverse of "binPause"
                request.addProperty("binPause", command.equals(OnOffType.OFF));
                sendRequest(new Requests.DeltaRequest(request));
            } else if (CHANNEL_CONTROL_MAP_UPLOAD.equals(channelId)) {
                request.addProperty("mapUploadAllowed", command.equals(OnOffType.ON));
                sendRequest(new Requests.DeltaRequest(request));
            }
        } else if (command instanceof StringType) {
            final JsonObject request = new JsonObject();
            final String channelId = channelUID.getIdWithoutGroup();
            if (CHANNEL_CONTROL_CLEAN_PASSES.equals(channelId)) {
                request.addProperty("noAutoPasses", !command.equals(PASSES_AUTO));
                request.addProperty("twoPasses", command.equals(PASSES_2));
                sendRequest(new Requests.DeltaRequest(request));
            } else if (CHANNEL_CONTROL_LANGUAGE.equals(channelId)) {
                request.addProperty("language", command.toString());
                sendRequest(new Requests.DeltaRequest(request));
            }
        }
    }

    protected void updateState(ChannelUID channelUID, @Nullable BigDecimal value) {
        if (value != null) {
            updateState(channelUID, new DecimalType(value));
        }
    }

    protected void updateState(ChannelUID channelUID, @Nullable Boolean value) {
        if (value != null) {
            updateState(channelUID, OnOffType.from(value.booleanValue()));
        }
    }

    protected void updateState(ChannelUID channelUID, @Nullable String value) {
        if (value != null) {
            updateState(channelUID, new StringType(value));
        }
    }

    protected void updateState(ChannelUID channelUID, @Nullable ZonedDateTime value) {
        if (value != null) {
            updateState(channelUID, new DateTimeType(value));
        }
    }

    @Override
    protected void updateState(ChannelUID channelUID, State state) {
        lastState.put(channelUID, state);
        super.updateState(channelUID, state);
    }

    @Override
    protected void updateConfiguration(Configuration configuration) {
        super.updateConfiguration(configuration);
        config.set(getConfigAs(IRobotConfiguration.class));
    }

    @Override
    protected void updateProperty(String name, @Nullable String value) {
        if (value != null) {
            super.updateProperty(name, value);
        }
    }

    protected void setCacheEntry(final ChannelUID channelUID, final State value) {
        lastState.put(channelUID, value);
    }

    protected @Nullable State getCacheEntry(final ChannelUID channelUID) {
        return lastState.get(channelUID);
    }

    protected void sendRequest(Requests.Request request) {
        MqttBrokerConnection connection = this.connection;
        if (connection != null) {
            if (logger.isTraceEnabled()) {
                logger.trace("Sending {}: {}", request.getTopic(), request.getPayload());
            }
            // 1 here actually corresponds to MQTT qos 0 (AT_MOST_ONCE). Only this value is accepted
            // by Roomba, others just cause it to reject the command and drop the connection.
            connection.publish(request.getTopic(), request.getPayload(), 1, false);
        }
    }

    // In order not to mess up our connection state we need to make sure that connect()
    // and disconnect() are never running concurrently, so they are synchronized
    private synchronized void connect() {
        final String address = config.get().getAddress();
        logger.debug("Connecting to {}", address);

        InetAddress host = null;
        try {
            host = InetAddress.getByName(address);
        } catch (UnknownHostException exception) {
            final String message = "Error connecting to host " + exception.toString();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
            return;
        }

        String blid = config.get().getBlid();
        if (UNKNOWN.equals(blid) || blid.isBlank()) {
            IdentProtocol.IdentData ident = null;
            try {
                final DatagramSocket socket = IdentProtocol.sendRequest(host);
                final DatagramPacket packet = IdentProtocol.receiveResponse(socket);
                ident = IdentProtocol.decodeResponse(packet);
            } catch (IOException exception) {
                final String message = "Error sending broadcast " + exception.toString();
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
            } catch (JsonParseException exception) {
                final String message = "Malformed IDENT reply from " + address;
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
            }

            final ThingStatusInfo status = thing.getStatusInfo();
            if ((status.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR) || (ident == null)) {
                reconnectReq = scheduler.schedule(this::connect, RECONNECT_DELAY, TimeUnit.MILLISECONDS);
                return;
            }

            if (ident.version < IdentProtocol.IdentData.MIN_SUPPORTED_VERSION) {
                final String message = "Unsupported version " + String.valueOf(ident.version);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
                return;
            }

            if (UNKNOWN.equals(ident.product)) {
                final String message = "Not a Roomba: " + ident.product;
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
                return;
            }

            Configuration configuration = editConfiguration();
            configuration.put("blid", ident.blid);
            updateConfiguration(configuration);
        }

        String password = config.get().getPassword();
        if (UNKNOWN.equals(password) || password.isBlank()) {
            RawMQTT.Packet response = null;
            try {
                RawMQTT mqtt = null;
                try {
                    mqtt = new RawMQTT(host, 8883);
                } catch (KeyManagementException | NoSuchAlgorithmException exception) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, exception.toString());
                    return; // This is internal system error, no retry
                }
                mqtt.requestPassword();
                response = mqtt.readPacket();
                mqtt.close();
            } catch (IOException exception) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, exception.toString());
                return; // This is internal system error, no retry
            }

            if (response != null && response.isValidPasswdPacket()) {
                RawMQTT.PasswdPacket passwdPacket = new RawMQTT.PasswdPacket(response);
                password = passwdPacket.getPassword();
                if (password != null) {
                    Configuration configuration = editConfiguration();
                    configuration.put("password", password.trim());
                    updateConfiguration(configuration);
                }
            }
        }

        blid = config.get().getBlid();
        password = config.get().getPassword();
        if (UNKNOWN.equals(blid) || blid.isBlank() || UNKNOWN.equals(password) || password.isBlank()) {
            final String message = "Authentication on the robot is required";
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING, message);
            reconnectReq = scheduler.schedule(this::connect, RECONNECT_DELAY, TimeUnit.MILLISECONDS);
            return;
        }

        // BLID is used as both client ID and username. The name of BLID also came from Roomba980-python
        MqttBrokerConnection connection = new MqttBrokerConnection(address, RawMQTT.ROOMBA_MQTT_PORT, true, blid);
        this.connection = connection;

        // Disable sending UNSUBSCRIBE request before disconnecting becuase Roomba doesn't like it.
        // It just swallows the request and never sends any response, so stop() method never completes.
        connection.setUnsubscribeOnStop(false);
        connection.setCredentials(blid, password);
        connection.setTrustManagers(RawMQTT.getTrustManagers());
        // 1 here actually corresponds to MQTT qos 0 (AT_MOST_ONCE). Only this value is accepted
        // by Roomba, others just cause it to reject the command and drop the connection.
        connection.setQos(1);
        // MQTT connection reconnects itself, so we don't have to reconnect, when it breaks
        connection.setReconnectStrategy(new PeriodicReconnectStrategy(RECONNECT_DELAY, RECONNECT_DELAY));

        connection.start().exceptionally(exception -> {
            connectionStateChanged(MqttConnectionState.DISCONNECTED, exception);
            return false;
        }).thenAccept(action -> {
            MqttConnectionState state = action ? MqttConnectionState.CONNECTED : MqttConnectionState.DISCONNECTED;
            connectionStateChanged(state, action ? null : new TimeoutException("Timeout"));
        });
    }

    private synchronized void disconnect() {
        Future<?> reconnectReq = this.reconnectReq;
        if (reconnectReq != null) {
            reconnectReq.cancel(false);
            this.reconnectReq = null;
        }

        MqttBrokerConnection connection = this.connection;
        if (connection != null) {
            connection.stop();
            this.connection = null;
        }
    }

    @Override
    public void processMessage(String topic, byte[] payload) {
        final ThingUID thingUID = thing.getUID();

        // Report raw JSON reply
        final String json = new String(payload, StandardCharsets.UTF_8);
        updateState(new ChannelUID(thingUID, STATE_GROUP_ID, CHANNEL_JSON), json);

        if (logger.isTraceEnabled()) {
            logger.trace("Got topic {} data {}", topic, json);
        }

        final JsonParser jsonParser = new JsonParser();
        final JsonElement tree = jsonParser.parse(json);

        final Boolean audio = JSONUtils.getAsBoolean("audio", tree);
        updateState(new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_AUDIO), audio);

        final Boolean binPause = JSONUtils.getAsBoolean("binPause", tree);
        final Boolean continueVacuum = (binPause != null) ? !binPause : null;
        updateState(new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_ALWAYS_FINISH), continueVacuum);

        final Boolean mapUpload = JSONUtils.getAsBoolean("mapUploadAllowed", tree);
        updateState(new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_MAP_UPLOAD), mapUpload);

        final BigDecimal charge = JSONUtils.getAsDecimal("batPct", tree);
        updateState(new ChannelUID(thingUID, STATE_GROUP_ID, CHANNEL_STATE_CHARGE), charge);

        final String type = JSONUtils.getAsString("batteryType", tree);
        updateState(new ChannelUID(thingUID, STATE_GROUP_ID, CHANNEL_STATE_TYPE), type);

        reportBinState(tree);
        reportCleanPasses(tree);
        reportLanguage(tree);
        reportMissionState(tree);
        reportNetworkState(tree);
        reportPositionState(tree);
    }

    private void reportBinState(final JsonElement tree) {
        final JsonElement bin = JSONUtils.find("bin", tree);
        if (bin != null) {
            // The bin cannot be both full and removed simultaneously, so let's encode it as a single value
            String status = null;
            if (Boolean.FALSE.equals(JSONUtils.getAsBoolean("present", bin))) {
                status = STATE_BIN_REMOVED;
            } else {
                final Boolean full = JSONUtils.getAsBoolean("full", bin);
                status = Boolean.TRUE.equals(full) ? STATE_BIN_FULL : STATE_BIN_OK;
            }

            final ThingUID thingUID = thing.getUID();
            updateState(new ChannelUID(thingUID, STATE_GROUP_ID, CHANNEL_STATE_BIN), status);
        }
    }

    private void reportCleanPasses(final JsonElement tree) {
        final ThingUID thingUID = thing.getUID();
        final ChannelUID channelUID = new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_CLEAN_PASSES);

        final Boolean noAutoPasses = JSONUtils.getAsBoolean("noAutoPasses", tree);
        final Boolean twoPasses = JSONUtils.getAsBoolean("twoPass", tree);

        // To make the life more interesting, paired values may not appear together in the
        // same message, so we have to keep track of current values.
        String state = null;
        if (noAutoPasses != null) {
            state = Boolean.FALSE.equals(noAutoPasses) ? PASSES_AUTO : PASSES_1;
            setCacheEntry(channelUID, new StringType(state));
        }

        if (twoPasses != null) {
            // Can be overridden by "noAutoPasses":false
            final State cache = getCacheEntry(channelUID);
            if ((cache != null) && !PASSES_AUTO.equals(cache.toString())) {
                state = Boolean.TRUE.equals(twoPasses) ? PASSES_2 : PASSES_1;
            }
        }

        updateState(new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_CLEAN_PASSES), state);
    }

    private void reportLanguage(final JsonElement tree) {
        final ThingUID thingUID = thing.getUID();
        final ChannelUID channelUID = new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_LANGUAGE);

        final JsonElement languages = JSONUtils.find("langs", tree);
        if (languages != null) {
            if (!channelContentProvider.isChannelPopulated(channelUID)) {
                channelContentProvider.setLanguages(channelUID, languages);
            }
            final State state = getCacheEntry(channelUID);
            updateState(channelUID, state != null ? state : new StringType(UNKNOWN));
        }

        final String language = JSONUtils.getAsString("language", tree);
        if (channelContentProvider.isChannelPopulated(channelUID)) {
            updateState(channelUID, language);
        } else if (language != null) {
            setCacheEntry(channelUID, new StringType(language));
        }
    }

    private void reportMissionState(final JsonElement tree) {
        // @formatter:off
        // "cleanMissionStatus": {
        //   "cycle": "none", "phase": "charge", "expireM": 0, "rechrgM": 0, "error": 0,
        //   "notReady": 0, "mssnM": 46, "sqft": 308, "initiator": "manual", "nMssn":  386
        // }
        // @formatter:on
        final JsonElement status = JSONUtils.find("cleanMissionStatus", tree);
        if (status != null) {
            final ThingUID thingUID = thing.getUID();
            final ChannelGroupUID missionGroupUID = new ChannelGroupUID(thingUID, MISSION_GROUP_ID);

            final String cycle = JSONUtils.getAsString("cycle", status);
            updateState(new ChannelUID(missionGroupUID, CHANNEL_MISSION_CYCLE), cycle);

            final BigDecimal error = JSONUtils.getAsDecimal("error", status);
            updateState(new ChannelUID(missionGroupUID, CHANNEL_MISSION_ERROR), String.valueOf(error));

            final String phase = JSONUtils.getAsString("phase", status);
            updateState(new ChannelUID(missionGroupUID, CHANNEL_MISSION_PHASE), phase);

            final BigDecimal missions = JSONUtils.getAsDecimal("nMssn", status);
            updateState(new ChannelUID(thingUID, STATE_GROUP_ID, CHANNEL_STATE_MISSIONS), missions);
        }
    }

    private void reportNetworkState(final JsonElement tree) {
        final ThingUID thingUID = thing.getUID();
        final ChannelGroupUID networkGroupUID = new ChannelGroupUID(thingUID, NETWORK_GROUP_ID);

        final JsonElement signal = JSONUtils.find("signal", tree);
        if (signal != null) {
            final BigDecimal rssi = JSONUtils.getAsDecimal("rssi", signal);
            updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_RSSI), rssi);

            final BigDecimal snr = JSONUtils.getAsDecimal("snr", signal);
            updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_SNR), snr);

            final BigDecimal noise = JSONUtils.getAsDecimal("noise", signal);
            updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_NOISE), noise);
        }

        final String bssid = JSONUtils.getAsString("bssid", tree);
        if (bssid != null) {
            updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_BSSID), bssid.toUpperCase());
        }

        final Boolean dhcp = JSONUtils.getAsBoolean("dhcp", tree);
        updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_DHCP), dhcp);

        final String security = JSONUtils.getAsString("sec", tree);
        updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_SECURITY), security);

        final String ssid = JSONUtils.getAsString("ssid", tree);
        if (ssid != null) {
            String buffer = new String();
            for (int i = 0; i < ssid.length() / 2; i++) {
                int hi = Character.digit(ssid.charAt(2 * i + 0), 16);
                int lo = Character.digit(ssid.charAt(2 * i + 1), 16);
                buffer = buffer + Character.toString(16 * hi + lo);
            }
            updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_SSID), buffer);
        }
    }

    private void reportPositionState(final JsonElement tree) {
        final JsonElement position = JSONUtils.find("pose", tree);
        if (position != null) {
            final ThingUID thingUID = thing.getUID();
            final ChannelGroupUID positionGroupUID = new ChannelGroupUID(thingUID, POSITION_GROUP_ID);

            updateState(new ChannelUID(positionGroupUID, CHANNEL_JSON), position.toString());

            final BigDecimal xPos = JSONUtils.getAsDecimal("x", position);
            updateState(new ChannelUID(positionGroupUID, CHANNEL_POSITION_X), xPos);

            final BigDecimal yPos = JSONUtils.getAsDecimal("y", position);
            updateState(new ChannelUID(positionGroupUID, CHANNEL_POSITION_Y), yPos);

            final BigDecimal theta = JSONUtils.getAsDecimal("theta", position);
            updateState(new ChannelUID(positionGroupUID, CHANNEL_POSITION_THETA), theta);
        }
    }

    @Override
    public void connectionStateChanged(MqttConnectionState state, @Nullable Throwable error) {
        if (state == MqttConnectionState.CONNECTED) {
            MqttBrokerConnection connection = this.connection;

            if (connection == null) {
                // This would be very strange, but Eclipse forces us to do the check
                logger.warn("Established connection without broker pointer");
                return;
            }

            updateStatus(ThingStatus.ONLINE);
            reconnectReq = null;

            // Roomba sends us two topics:
            // "wifistat" - reports signal strength and current robot position
            // "$aws/things/<BLID>/shadow/update" - the rest of messages
            // Subscribe to everything since we're interested in both
            connection.subscribe("#", this).exceptionally(exception -> {
                logger.warn("MQTT subscription failed: {}", exception.getMessage());
                return false;
            }).thenAccept(v -> {
                if (!v) {
                    logger.warn("Subscription timeout");
                } else {
                    logger.trace("Subscription done");
                }
            });
        } else {
            String message = (error != null) ? error.getMessage() : "Unknown reason";
            logger.warn("MQTT connection failed: {}", message);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
        }
    }
}
