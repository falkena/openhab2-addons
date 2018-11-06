/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hideki.internal.handler;

import static org.openhab.binding.hideki.internal.HidekiBindingConstants.*;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hideki.internal.HidekiDecoder;
import org.openhab.binding.hideki.internal.HidekiReceiver;
import org.openhab.binding.hideki.internal.config.HidekiReceiverConfiguration;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HidekiWeatherstationHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
@NonNullByDefault
public class HidekiWeatherstationHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(HidekiWeatherstationHandler.class);

    Map<Integer, int[]> data = new ConcurrentHashMap<>();

    @Nullable
    private HidekiDecoder decoder;
    private HidekiReceiverConfiguration config = getConfigAs(HidekiReceiverConfiguration.class);

    @Nullable
    private ScheduledFuture<?> readerJob;
    private final Runnable dataReader = new Runnable() {
        private final Thing thing = getThing();

        @Override
        public void run() {
            if ((decoder != null) && (ThingStatus.ONLINE == thing.getStatus())) {
                final int @Nullable [] buffer = decoder.getDecodedData();
                if ((buffer != null) && (buffer[0] == 0x9F)) {
                    @Nullable
                    Integer type = getDecodedType(buffer);
                    if ((type != null) && data.containsKey(type)) {
                        if (buffer.length == getDecodedLength(buffer)) {
                            data.put(type, buffer);
                            final String group = SENSOR_GROUPS.get(type);
                            for (final Channel channel : thing.getChannels()) {
                                final ChannelUID channelUID = channel.getUID();
                                if ((group == null) || !group.equalsIgnoreCase(channelUID.getGroupId())) {
                                    continue;
                                }
                                if (RECEIVED_UPDATE.equalsIgnoreCase(channelUID.getIdWithoutGroup())) {
                                    updateState(channelUID, new DateTimeType(ZonedDateTime.now()));
                                } else {
                                    handleCommand(channelUID, RefreshType.REFRESH);
                                }
                            }
                        } else {
                            logger.warn("Got wrong sensor data length {}.", buffer.length);
                        }
                    } else {
                        logger.warn("Got unknown sensor type {} from data {}.", type, toHexString(buffer));
                    }
                    if (logger.isTraceEnabled()) {
                        logger.trace("Fetched new data: {}.", toHexString(buffer));
                    }
                }
            }
        }
    };

    private static final char[] HEX_LUT = "0123456789ABCDEF".toCharArray();

    private static String toHexString(final int[] data) {
        String result = "[";
        for (int i = 0; i < data.length - 1; i++) {
            if (data[i] == (data[i] & 0xFF)) {
                result += String.format("0x%c%c", HEX_LUT[data[i] >>> 4], HEX_LUT[data[i] & 0x0F]);
            } else {
                result += String.format("%d", data[i]);
            }
            result += ", ";
        }
        if (data.length > 1) {
            result += String.format("%d, ", data[data.length - 1]);
        }
        if (result.length() > 2) {
            result = result.substring(0, result.length() - 2);
        }
        result += "]";
        return result;
    }

    public HidekiWeatherstationHandler(Thing thing) {
        super(thing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        Integer type = null;
        final String group = channelUID.getGroupId();
        for (final Entry<Integer, @Nullable String> entry : SENSOR_GROUPS.entrySet()) {
            if ((group != null) && group.equalsIgnoreCase(entry.getValue())) {
                type = entry.getKey();
                break;
            }
        }

        final int[] buffer = data.get(type);
        if ((command instanceof RefreshType) && (buffer != null) && (buffer.length > 1)) {
            final String channelId = channelUID.getIdWithoutGroup();
            if (SENSOR_ID.equals(channelId)) {
                int id = buffer.length < 2 ? -1 : buffer[1];
                updateState(channelUID, new DecimalType(id));
            } else if (MESSAGE_NUMBER.equals(channelId)) {
                int message = buffer.length < 4 ? -1 : buffer[3] >> 6;
                updateState(channelUID, new DecimalType(message));
            } else if (RSSI_VALUE.equals(channelId)) {
                int rssi = buffer[buffer.length - 1];
                updateState(channelUID, new DecimalType(rssi));
            } else if (BATTERY.equals(channelId)) {
                boolean state = (buffer[2] >> 6) == 3;
                updateState(channelUID, state ? OnOffType.ON : OnOffType.OFF);
            } else if (TEMPERATURE.equals(channelId)) {
                if ((type == ANEMOMETER_TYPE_ID) || (type == THERMOMETER_TYPE_ID)) {
                    double temperature = (buffer[5] & 0x0F) * 10.0 + (buffer[4] >> 4) + (buffer[4] & 0x0F) * 0.1;
                    if ((buffer[5] >> 4) != 0x0C) {
                        temperature = (buffer[5] >> 4) == 0x04 ? -temperature : Double.MAX_VALUE;
                    }
                    updateState(channelUID, new QuantityType<>(temperature, SIUnits.CELSIUS));
                } else if (type == UVMETER_TYPE_ID) {
                    double temperature = (buffer[4] >> 4) + (buffer[4] & 0x0F) / 10.0 + (buffer[5] & 0x0F) * 10.0;
                    updateState(channelUID, new QuantityType<>(temperature, SIUnits.CELSIUS));
                } else {
                    logger.warn("Temperature channel is not supported on {}.", type);
                }
            } else if (CHILL.equals(channelId)) {
                double chill = (buffer[7] & 0x0F) * 10 + (buffer[6] >> 4) + (buffer[6] & 0x0F) * 0.1;
                if ((buffer[7] >> 4) != 0x0C) {
                    chill = (buffer[7] >> 4) == 0x04 ? -chill : Double.MAX_VALUE;
                }
                updateState(channelUID, new QuantityType<>(chill, SIUnits.CELSIUS));
            } else if (SPEED.equals(channelId)) {
                double speed = (buffer[8] >> 4) + (buffer[8] & 0x0F) / 10.0 + (buffer[9] & 0x0F) * 10.0;
                updateState(channelUID, new QuantityType<>(speed, ImperialUnits.MILES_PER_HOUR));
            } else if (GUST.equals(channelId)) {
                double gust = (buffer[9] >> 4) / 10.0 + (buffer[10] & 0x0F) + (buffer[10] >> 4) * 10.0;
                updateState(channelUID, new QuantityType<>(gust, ImperialUnits.MILES_PER_HOUR));
            } else if (DIRECTION.equals(channelId)) {
                int segment = (buffer[11] >> 4);
                segment = segment ^ (segment & 8) >> 1;
                segment = segment ^ (segment & 4) >> 1;
                segment = segment ^ (segment & 2) >> 1;
                updateState(channelUID, new QuantityType<>(22.5 * (-segment & 0xF), Units.DEGREE_ANGLE));
            } else if (SENSOR_CHANNEL.equals(channelId)) {
                int channel = buffer.length < 2 ? -1 : buffer[1] >> 5;
                if ((channel == 5) || (channel == 6)) {
                    channel = channel - 1;
                } else if (channel > 3) {
                    channel = -1;
                }
                updateState(channelUID, new DecimalType(channel));
            } else if (HUMIDITY.equals(channelId)) {
                double humidity = (buffer[6] >> 4) * 10.0 + (buffer[6] & 0x0F);
                updateState(channelUID, new QuantityType<>(humidity, Units.PERCENT));
            } else if (RAIN_LEVEL.equals(channelId)) {
                double level = 0.7 * ((buffer[5] << 8) + buffer[4]);
                updateState(channelUID, new DecimalType(level));
            } else if (MED.equals(channelId)) {
                // MED stay for "minimal erythema dose". Some definitions
                // says: 1 MED/h = 2.778 UV-Index, another 1 MED/h = 2.33 UV-Index
                double med = (buffer[5] >> 4) / 10.0 + (buffer[6] & 0x0F) + (buffer[6] >> 4) * 10.0;
                updateState(channelUID, new DecimalType(med));
            } else if (UV_INDEX.equals(channelId)) {
                double index = (buffer[7] >> 4) + (buffer[7] & 0x0F) / 10.0 + (buffer[8] & 0x0F) * 10.0;
                updateState(channelUID, new DecimalType(index));
            } else if (RECEIVED_UPDATE.equals(channelId)) {
                // No update of received time
            } else {
                logger.warn("Received command {} on unknown channel {}.", command, channelUID);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        synchronized (config) {
            config = getConfigAs(HidekiReceiverConfiguration.class);
        }

        data.clear();
        SENSOR_GROUPS.forEach((key, value) -> data.put(key, new int[0]));

        scheduler.execute(new Runnable() {
            @Override
            public void run() {
                if (decoder == null) {
                    final String device = config.getDevice();
                    final Integer pin = config.getGpioPin();
                    final Integer interrupt = config.getInterrupt();
                    HidekiReceiver receiver = new HidekiReceiver(HidekiReceiver.Kind.CC1101, pin, device, interrupt);
                    receiver.setTimeOut(config.getTimeout().intValue());
                    decoder = new HidekiDecoder(receiver);
                    if (decoder.start()) {
                        if (readerJob == null) {
                            final Integer interval = config.getRefreshRate();
                            logger.info("Creating new reader job on pin {} with interval {} sec.", pin, interval);
                            readerJob = scheduler.scheduleWithFixedDelay(dataReader, 1, interval, TimeUnit.SECONDS);
                        }
                        updateStatus(ThingStatus.ONLINE);
                    } else {
                        final String message = "Can not start decoder on pin: " + pin.toString() + ".";
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
                        decoder = null;
                    }
                } else {
                    final String message = "Can not initialize decoder. Please, check parameter.";
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        super.dispose();

        if (readerJob != null) {
            readerJob.cancel(false);
            logger.info("Destroy hideki reader job.");
        }
        readerJob = null;

        if (decoder != null) {
            decoder.stop();
        }
        decoder = null;

        data.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateConfiguration(Configuration configuration) {
        super.updateConfiguration(configuration);
        synchronized (config) {
            config = getConfigAs(HidekiReceiverConfiguration.class);
        }
    }

    /**
     * Returns decoded sensor type. Is negative, if decoder failed.
     *
     * @return Decoded sensor type
     */
    private @Nullable Integer getDecodedType(final int @Nullable [] data) {
        return (data == null) || (data.length < 4) ? null : Integer.valueOf(data[3] & 0x1F);
    }

    /**
     * Returns decoded data length. Is negative, if decoder failed.
     *
     * @return Decoded sensor type
     */
    private int getDecodedLength(final int @Nullable [] data) {
        final int length = (data == null) || (data.length < 3) ? -1 : (data[2] >> 1) & 0x1F;
        return length < 0 ? length : length + 2; // First byte is 0x9F preamble and last RSSI value
    }
}
