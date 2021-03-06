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
import static org.openhab.binding.irobot.internal.IRobotBindingConstants.UNKNOWN;
import static org.openhab.core.thing.ThingStatus.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.irobot.internal.IRobotChannelContentProvider;
import org.openhab.binding.irobot.internal.config.IRobotConfiguration;
import org.openhab.binding.irobot.internal.utils.IRobotMap;
import org.openhab.binding.irobot.internal.utils.JSONUtils;
import org.openhab.binding.irobot.internal.utils.LoginRequester;
import org.openhab.binding.irobot.internal.utils.Requests;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.io.transport.mqtt.MqttConnectionState;
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
public class RoombaCommonHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(RoombaCommonHandler.class);

    private IRobotChannelContentProvider channelContentProvider;
    private LocaleProvider localeProvider;

    private AtomicReference<IRobotConfiguration> config = new AtomicReference<>();
    private ConcurrentHashMap<ChannelUID, State> lastState = new ConcurrentHashMap<>();

    private @Nullable Future<?> credentialRequester;

    protected IRobotMap lastCleanMap = new IRobotMap();
    protected RoombaConnectionHandler connection = new RoombaConnectionHandler() {
        @Override
        public void receive(final String topic, final String json) {
            RoombaCommonHandler.this.receive(topic, json);
        }

        @Override
        public void connectionStateChanged(MqttConnectionState state, @Nullable Throwable error) {
            super.connectionStateChanged(state, error);
            if (state == MqttConnectionState.CONNECTED) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, error.toString());
            }
        }
    };

    public RoombaCommonHandler(Thing thing, IRobotChannelContentProvider channelContentProvider,
            LocaleProvider localeProvider) {
        super(thing);
        this.channelContentProvider = channelContentProvider;
        this.localeProvider = localeProvider;
    }

    @Override
    public void initialize() {
        config.set(getConfigAs(IRobotConfiguration.class));
        for (Channel channel : thing.getChannels()) {
            lastState.put(channel.getUID(), UnDefType.UNDEF);
        }

        switch (config.get().getFamily()) {
            case ROOMBA_980:
            case ROOMBA_I7: {
                break;
            }
            default: {
                final String message = "Found not supported robot family " + config.get().getFamily();
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
                return;
            }
        }

        try {
            InetAddress.getByName(config.get().getAddress());
        } catch (UnknownHostException exception) {
            final String message = "Error connecting to host " + exception.toString();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
            return;
        }

        if (UNKNOWN.equals(config.get().getPassword()) || UNKNOWN.equals(config.get().getBlid())) {
            final String message = "Robot authentication is required";
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
            scheduler.execute(this::getCredentials);
        } else {
            scheduler.execute(this::connect);
        }
    }

    @Override
    public void dispose() {
        if (credentialRequester != null) {
            credentialRequester.cancel(false);
            credentialRequester = null;
        }

        scheduler.execute(connection::disconnect);
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
                connection.send(new Requests.DeltaRequest(request));
            } else if (CHANNEL_CONTROL_ALWAYS_FINISH.equals(channelId)) {
                // Binding operate with inverse of "binPause"
                request.addProperty("binPause", command.equals(OnOffType.OFF));
                connection.send(new Requests.DeltaRequest(request));
            } else if (CHANNEL_CONTROL_MAP_UPLOAD.equals(channelId)) {
                request.addProperty("mapUploadAllowed", command.equals(OnOffType.ON));
                connection.send(new Requests.DeltaRequest(request));
            }
        } else if (command instanceof StringType) {
            final JsonObject request = new JsonObject();
            final String channelId = channelUID.getIdWithoutGroup();
            if (CHANNEL_CONTROL_CLEAN_PASSES.equals(channelId)) {
                request.addProperty("noAutoPasses", !command.equals(PASSES_AUTO));
                request.addProperty("twoPasses", command.equals(PASSES_2));
                connection.send(new Requests.DeltaRequest(request));
            } else if (CHANNEL_CONTROL_LANGUAGE.equals(channelId)) {
                request.addProperty("language", command.toString());
                connection.send(new Requests.DeltaRequest(request));
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

    private synchronized void getCredentials() {
        ThingStatus status = thing.getStatusInfo().getStatus();
        IRobotConfiguration config = getConfigAs(IRobotConfiguration.class);
        if (UNINITIALIZED.equals(status) || INITIALIZING.equals(status) || OFFLINE.equals(status)) {
            if (UNKNOWN.equals(config.getBlid())) {
                @Nullable
                String blid = null;
                try {
                    blid = LoginRequester.getBlid(config.getAddress());
                } catch (IOException exception) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, exception.toString());
                }

                if (blid != null) {
                    Configuration configuration = editConfiguration();
                    configuration.put(ROBOT_BLID, blid);
                    updateConfiguration(configuration);
                }
            }

            if (UNKNOWN.equals(config.getPassword())) {
                @Nullable
                String password = null;
                try {
                    password = LoginRequester.getPassword(config.getAddress());
                } catch (KeyManagementException | NoSuchAlgorithmException exception) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, exception.toString());
                    return; // This is internal system error, no retry
                } catch (IOException exception) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, exception.toString());
                }

                if (password != null) {
                    Configuration configuration = editConfiguration();
                    configuration.put(ROBOT_PASSWORD, password.trim());
                    updateConfiguration(configuration);
                }
            }
        }

        credentialRequester = null;
        if (UNKNOWN.equals(config.getBlid()) || UNKNOWN.equals(config.getPassword())) {
            credentialRequester = scheduler.schedule(this::getCredentials, 10000, TimeUnit.MILLISECONDS);
        } else {
            scheduler.execute(this::connect);
        }
    }

    // In order not to mess up our connection state we need to make sure that connect()
    // and disconnect() are never running concurrently, so they are synchronized
    private synchronized void connect() {
        final String address = config.get().getAddress();
        logger.debug("Connecting to {}", address);

        String blid = config.get().getBlid();
        String password = config.get().getPassword();
        if (UNKNOWN.equals(blid) || UNKNOWN.equals(password)) {
            final String message = "Robot authentication is required";
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
            scheduler.execute(this::getCredentials);
        } else {
            final String message = "Robot authentication is successful";
            updateStatus(ThingStatus.ONLINE, ThingStatusDetail.CONFIGURATION_PENDING, message);
            connection.connect(address, blid, password);
        }
    }

    public void receive(final String topic, final String json) {
        final ThingUID thingUID = thing.getUID();
        updateState(new ChannelUID(thingUID, STATE_GROUP_ID, CHANNEL_JSON), json);

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
        reportStatisticsState(tree);
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

    private void reportStatisticsState(final JsonElement tree) {
        final JsonElement run = JSONUtils.find("bbrun", tree);
        if (run != null) {
            final ThingUID thingUID = thing.getUID();
            final ChannelGroupUID statisticsGroupUID = new ChannelGroupUID(thingUID, STATISTICS_GROUP_ID);

            final BigDecimal hrs = JSONUtils.getAsDecimal("hr", run);
            final BigDecimal min = JSONUtils.getAsDecimal("min", run);
            if ((hrs != null) && (min != null)) {
                Locale locale = localeProvider.getLocale();
                final String hString = ChronoField.HOUR_OF_DAY.getDisplayName(locale);
                final String mString = ChronoField.MINUTE_OF_HOUR.getDisplayName(locale);
                String duration = String.format("%d %s %d %s", hrs.intValue(), hString, min.intValue(), mString);
                updateState(new ChannelUID(statisticsGroupUID, CHANNEL_STATISTICS_DURATION), duration);
            }
        }

        // @formatter:off
        // "bbmssn": { "aCycleM": 0, "nMssnF": 0, "nMssnC": 0, "nMssnOk": 0, "aMssnM": 0, "nMssn": 0 }
        // @formatter:on
        final JsonElement mission = JSONUtils.find("bbmssn", tree);
        if (mission != null) {
            final ThingUID thingUID = thing.getUID();
            final ChannelGroupUID statisticsGroupUID = new ChannelGroupUID(thingUID, STATISTICS_GROUP_ID);

            final BigDecimal mCount = JSONUtils.getAsDecimal("nMssn", mission);
            updateState(new ChannelUID(statisticsGroupUID, CHANNEL_STATISTICS_MISSION_COUNT), mCount);
        }
    }
}
