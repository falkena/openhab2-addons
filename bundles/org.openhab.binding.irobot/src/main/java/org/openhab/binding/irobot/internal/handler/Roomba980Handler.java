/**
 * Copyright (c) 2021- Alexander Falkenstern
 *
 * License: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.openhab.binding.irobot.internal.handler;

import static org.openhab.binding.irobot.internal.IRobotBindingConstants.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

import javax.imageio.ImageIO;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.irobot.internal.IRobotChannelContentProvider;
import org.openhab.binding.irobot.internal.utils.JSONUtils;
import org.openhab.binding.irobot.internal.utils.Requests;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.*;

/**
 * The {@link Roomba980Handler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
@NonNullByDefault
public class Roomba980Handler extends RoombaCommonHandler {
    private final Logger logger = LoggerFactory.getLogger(Roomba980Handler.class);
    private final JsonParser jsonParser = new JsonParser();

    public Roomba980Handler(Thing thing, IRobotChannelContentProvider channelContentProvider,
            LocaleProvider localeProvider) {
        super(thing, channelContentProvider, localeProvider);
    }

    @Override
    public void initialize() {
        ThingBuilder tBuilder = editThing();
        final ThingUID thingUID = thing.getUID();

        ChannelUID channelUID = new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_EDGE_CLEAN);
        if (thing.getChannel(channelUID) == null) {
            ChannelBuilder cBuilder = ChannelBuilder.create(channelUID, "Switch");
            cBuilder.withType(new ChannelTypeUID(BINDING_ID, CHANNEL_CONTROL_EDGE_CLEAN));
            cBuilder.withLabel("Edge clean");
            cBuilder.withDescription("Seek out and clean along walls and furniture legs");
            tBuilder.withChannel(cBuilder.build());
        }

        channelUID = new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_POWER_BOOST);
        if (thing.getChannel(channelUID) == null) {
            ChannelBuilder cBuilder = ChannelBuilder.create(channelUID, "String");
            cBuilder.withType(new ChannelTypeUID(BINDING_ID, CHANNEL_CONTROL_POWER_BOOST));
            cBuilder.withLabel("Power boost");
            cBuilder.withDescription("Carpet boost mode");
            tBuilder.withChannel(cBuilder.build());
        }

        updateThing(tBuilder.build());
        super.initialize();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof OnOffType) {
            final JsonObject state = new JsonObject();
            if (SCHEDULE_GROUP_ID.equals(channelUID.getGroupId())) {
                final JsonArray cycle = new JsonArray(DAY_OF_WEEK.length);
                final JsonArray hours = new JsonArray(DAY_OF_WEEK.length);
                final JsonArray minutes = new JsonArray(DAY_OF_WEEK.length);
                final ChannelGroupUID groupUID = new ChannelGroupUID(channelUID.getThingUID(), SCHEDULE_GROUP_ID);
                for (int i = 0; i < DAY_OF_WEEK.length; i++) {
                    State cache = getCacheEntry(new ChannelUID(groupUID, DAY_OF_WEEK[i] + "_enabled"));
                    cycle.add(OnOffType.ON.equals(cache) ? "start" : "none");
                    if (channelUID.getIdWithoutGroup().equals(DAY_OF_WEEK[i] + "_enabled")) {
                        cycle.set(i, new JsonPrimitive(OnOffType.ON.equals(command) ? "start" : "none"));
                    }

                    cache = getCacheEntry(new ChannelUID(groupUID, DAY_OF_WEEK[i] + "_time"));
                    final DateTimeType time = (cache != null) ? cache.as(DateTimeType.class) : null;
                    hours.add(time != null ? time.getZonedDateTime().getHour() : DEFAULT_HOUR);
                    minutes.add(time != null ? time.getZonedDateTime().getMinute() : DEFAULT_MINUTE);
                }

                final JsonObject schedule = new JsonObject();
                schedule.add("cycle", cycle);
                schedule.add("h", hours);
                schedule.add("m", minutes);
                state.add("cleanSchedule", schedule);
                connection.send(new Requests.DeltaRequest(state));
            } else if (CHANNEL_CONTROL_EDGE_CLEAN.equals(channelUID.getIdWithoutGroup())) {
                // Binding operate with inverse of "openOnly"
                state.addProperty("openOnly", command.equals(OnOffType.OFF));
                connection.send(new Requests.DeltaRequest(state));
            } else {
                super.handleCommand(channelUID, command);
            }
        } else if (command instanceof DateTimeType) {
            final JsonObject state = new JsonObject();
            if (SCHEDULE_GROUP_ID.equals(channelUID.getGroupId())) {
                final JsonArray cycle = new JsonArray(DAY_OF_WEEK.length);
                final JsonArray hours = new JsonArray(DAY_OF_WEEK.length);
                final JsonArray minutes = new JsonArray(DAY_OF_WEEK.length);
                final ChannelGroupUID groupUID = new ChannelGroupUID(channelUID.getThingUID(), SCHEDULE_GROUP_ID);
                for (int i = 0; i < DAY_OF_WEEK.length; i++) {
                    State cache = getCacheEntry(new ChannelUID(groupUID, DAY_OF_WEEK[i] + "_enabled"));
                    cycle.add((cache != null) && OnOffType.ON.equals(cache) ? "start" : "none");

                    cache = getCacheEntry(new ChannelUID(groupUID, DAY_OF_WEEK[i] + "_time"));
                    DateTimeType time = (cache != null) ? cache.as(DateTimeType.class) : null;
                    if (channelUID.getIdWithoutGroup().equals(DAY_OF_WEEK[i] + "_time")) {
                        time = (DateTimeType) command;
                    }
                    hours.add(time != null ? time.getZonedDateTime().getHour() : DEFAULT_HOUR);
                    minutes.add(time != null ? time.getZonedDateTime().getMinute() : DEFAULT_MINUTE);
                }

                final JsonObject schedule = new JsonObject();
                schedule.add("cycle", cycle);
                schedule.add("h", hours);
                schedule.add("m", minutes);
                state.add("cleanSchedule", schedule);
                connection.send(new Requests.DeltaRequest(state));
            } else {
                super.handleCommand(channelUID, command);
            }
        } else if (command instanceof StringType) {
            final String channelId = channelUID.getIdWithoutGroup();
            if (CHANNEL_CONTROL_POWER_BOOST.equals(channelId)) {
                final JsonObject request = new JsonObject();
                request.addProperty("carpetBoost", command.equals(BOOST_AUTO));
                request.addProperty("vacHigh", command.equals(BOOST_PERFORMANCE));
                connection.send(new Requests.DeltaRequest(request));
            } else if (CHANNEL_CONTROL_COMMAND.equals(channelId)) {
                Boolean isPaused = Boolean.FALSE;
                final ChannelGroupUID groupUID = new ChannelGroupUID(channelUID.getThingUID(), INTERNAL_GROUP_ID);
                final State lastCommand = getCacheEntry(new ChannelUID(groupUID, CHANNEL_INTERNAL_LAST_COMMAND));
                if (lastCommand != null) {
                    final JsonElement tree = jsonParser.parse(lastCommand.toString());
                    isPaused = JSONUtils.getAsBoolean("command", tree);
                }

                String request = command.toString();
                if (COMMAND_CLEAN.equals(request)) {
                    request = (isPaused != null) && isPaused ? COMMAND_RESUME : COMMAND_START;
                }
                connection.send(new Requests.CommandRequest(new JsonPrimitive(request)));
            } else {
                super.handleCommand(channelUID, command);
            }
        } else {
            super.handleCommand(channelUID, command);
        }
    }

    @Override
    public void receive(final String topic, final String json) {
        final ThingUID thingUID = thing.getUID();
        final JsonElement tree = jsonParser.parse(new StringReader(json));

        // Skip desired messages, since AWS-related stuff
        if (JSONUtils.getAsJSONString("desired", tree) != null) {
            return;
        }

        State cache = getCacheEntry(new ChannelUID(thingUID, MISSION_GROUP_ID, CHANNEL_MISSION_PHASE));
        StringType phase = (cache != null) ? cache.as(StringType.class) : null;
        final JsonElement status = JSONUtils.find("cleanMissionStatus", tree);
        if (status != null) {
            // I7: cycle = "clean", 980: cycle = "quick"
            final String currentPhase = JSONUtils.getAsString("phase", status);
            if ("run".equals(currentPhase) && ((phase == null) || !phase.equals(currentPhase))) {
                lastCleanMap.clear();
                updateState(new ChannelUID(thingUID, MISSION_GROUP_ID, CHANNEL_MISSION_MAP), UnDefType.UNDEF);
            } else if ("hmPostMsn".equals(currentPhase) || "hmUsrDock".equals(currentPhase)) {
                lastCleanMap.generate();
                try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                    ImageIO.write(lastCleanMap, "png", stream);
                    final RawType data = new RawType(stream.toByteArray(), "image/png");
                    updateState(new ChannelUID(thingUID, MISSION_GROUP_ID, CHANNEL_MISSION_MAP), data);
                } catch (IOException exception) {
                    updateState(new ChannelUID(thingUID, MISSION_GROUP_ID, CHANNEL_MISSION_MAP), UnDefType.UNDEF);
                    logger.debug("Can not convert image: {}", exception.getMessage());
                }
            }
            phase = new StringType(currentPhase);
        }

        final JsonElement position = JSONUtils.find("pose", tree);
        if ((position != null) && (phase != null) && phase.equals("run")) {
            final BigDecimal xPos = JSONUtils.getAsDecimal("x", position);
            final BigDecimal yPos = JSONUtils.getAsDecimal("y", position);
            if ((xPos != null) && (yPos != null)) {
                // I7: cycle = "clean", 980: cycle = "quick"
                lastCleanMap.add(xPos.doubleValue(), yPos.doubleValue());
            }
        }

        // @formatter:off
        // "cleanSchedule":{
        //   "cycle":["none","start","start","start","start","start","none"],
        //   "h":[9,10,10,10,10,10,10],
        //   "m":[0,0,0,0,0,0,0]
        // }
        // @formatter:on
        final JsonElement schedule = JSONUtils.find("cleanSchedule", tree);
        if (schedule != null) {
            final JsonElement cycles = JSONUtils.find("cycle", schedule);
            final JsonElement hours = JSONUtils.find("h", schedule);
            final JsonElement minutes = JSONUtils.find("m", schedule);

            final ChannelGroupUID groupUID = new ChannelGroupUID(thingUID, SCHEDULE_GROUP_ID);
            for (int i = 0; i < DAY_OF_WEEK.length; i++) {
                final JsonElement cycle = (cycles != null) ? cycles.getAsJsonArray().get(i) : null;
                final Boolean enable = "start".equals(cycle != null ? cycle.getAsJsonPrimitive().getAsString() : "");
                updateState(new ChannelUID(groupUID, DAY_OF_WEEK[i] + "_enabled"), enable);

                ZonedDateTime time = ZonedDateTime.now();
                final JsonElement hour = (hours != null) ? hours.getAsJsonArray().get(i) : null;
                time = time.withHour(hour != null ? hour.getAsJsonPrimitive().getAsInt() : DEFAULT_HOUR);
                final JsonElement minute = (minutes != null) ? minutes.getAsJsonArray().get(i) : null;
                time = time.withMinute(minute != null ? minute.getAsJsonPrimitive().getAsInt() : DEFAULT_MINUTE);
                updateState(new ChannelUID(groupUID, DAY_OF_WEEK[i] + "_time"), time);
            }
        }

        reportPowerBoost(tree);

        final Boolean openOnly = JSONUtils.getAsBoolean("openOnly", tree);
        final Boolean edgeClean = (openOnly != null) ? !openOnly : null;
        updateState(new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_EDGE_CLEAN), edgeClean);

        final ChannelGroupUID networkGroupUID = new ChannelGroupUID(thingUID, NETWORK_GROUP_ID);
        final String mac = JSONUtils.getAsString("mac", tree);
        if (mac != null) {
            updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_MAC), mac.toUpperCase());
        }

        final String address = convertNumber2IP(JSONUtils.getAsDecimal("addr", tree));
        updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_ADDRESS), address);

        final String mask = convertNumber2IP(JSONUtils.getAsDecimal("mask", tree));
        updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_MASK), mask);

        final String gateway = convertNumber2IP(JSONUtils.getAsDecimal("gw", tree));
        updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_GATEWAY), gateway);

        final String dns1 = convertNumber2IP(JSONUtils.getAsDecimal("dns1", tree));
        updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_DNS1), dns1);

        final String dns2 = convertNumber2IP(JSONUtils.getAsDecimal("dns2", tree));
        updateState(new ChannelUID(networkGroupUID, CHANNEL_NETWORK_DNS2), dns2);

        final JsonElement lastCommand = JSONUtils.find("lastCommand", tree);
        if (lastCommand != null) {
            // @formatter:off
            // "lastCommand":{
            //   "command":"dock/start/pause/quick/resume",
            //   "time": 1609332069,
            //   "initiator":"rmtApp/schedule/manual"
            // }
            // @formatter:on
            final String commandJSON = lastCommand.toString();
            updateState(new ChannelUID(thingUID, INTERNAL_GROUP_ID, CHANNEL_INTERNAL_LAST_COMMAND), commandJSON);

            String command = JSONUtils.getAsString("command", lastCommand);
            if (COMMAND_RESUME.equals(command) || COMMAND_START.equals(command)) {
                command = COMMAND_CLEAN;
            }
            updateState(new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_COMMAND), command);
        }

        updateProperty(Thing.PROPERTY_FIRMWARE_VERSION, JSONUtils.getAsString("softwareVer", tree));
        updateProperty("navSwVer", JSONUtils.getAsString("navSwVer", tree));
        updateProperty("wifiSwVer", JSONUtils.getAsString("wifiSwVer", tree));
        updateProperty("mobilityVer", JSONUtils.getAsString("mobilityVer", tree));
        updateProperty("bootloaderVer", JSONUtils.getAsString("bootloaderVer", tree));
        updateProperty("umiVer", JSONUtils.getAsString("umiVer", tree));

        super.receive(topic, json);
    }

    private void reportPowerBoost(final JsonElement tree) {
        final ThingUID thingUID = thing.getUID();
        final ChannelUID channelUID = new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_POWER_BOOST);

        final Boolean boost = JSONUtils.getAsBoolean("carpetBoost", tree);
        final Boolean vacHigh = JSONUtils.getAsBoolean("vacHigh", tree);

        // To make the life more interesting, paired values may not appear together in the
        // same message, so we have to keep track of current values.
        String state = null;
        if (boost != null) {
            state = Boolean.TRUE.equals(boost) ? BOOST_AUTO : BOOST_ECO;
            setCacheEntry(channelUID, new StringType(state));
        }

        if (vacHigh != null) {
            // Can be overridden by "carpetBoost":true
            final State cache = getCacheEntry(channelUID);
            if ((cache != null) && !cache.equals(BOOST_AUTO)) {
                state = Boolean.TRUE.equals(vacHigh) ? BOOST_PERFORMANCE : BOOST_ECO;
            }
        }

        updateState(new ChannelUID(thingUID, CONTROL_GROUP_ID, CHANNEL_CONTROL_POWER_BOOST), state);
    }

    private @Nullable String convertNumber2IP(@Nullable BigDecimal number) {
        if (number != null) {
            String result = (number.longValue() > 0) ? "" : "0.0.0.0.";
            while (number.longValue() > 0) {
                result = number.longValue() % 256 + "." + result;
                number = BigDecimal.valueOf(number.longValue() / 256);
            }
            return result.substring(0, result.length() - 1);
        } else {
            return null;
        }
    };
}
