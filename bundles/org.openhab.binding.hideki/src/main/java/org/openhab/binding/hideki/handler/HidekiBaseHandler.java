/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hideki.handler;

import static org.openhab.binding.hideki.HidekiBindingConstants.*;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HidekiBaseHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
public abstract class HidekiBaseHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(HidekiBaseHandler.class);

    private int[] data = null;

    public HidekiBaseHandler(Thing thing) {
        super(thing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleCommand(@NonNull ChannelUID channelUID, Command command) {
        logger.debug("Handle command {} on channel {}", command, channelUID);

        if (command instanceof RefreshType && (data != null)) {
            final String channelId = channelUID.getId();
            if (SENSOR_ID.equals(channelId)) {
                int id = data.length < 2 ? -1 : data[1];
                updateState(channelUID, new DecimalType(id));
            } else if (MESSAGE_NUMBER.equals(channelId)) {
                int message = data.length < 4 ? -1 : data[3] >> 6;
                updateState(channelUID, new DecimalType(message));
            } else if (RSSI_VALUE.equals(channelId)) {
                int rssi = data[data.length - 1];
                updateState(channelUID, new DecimalType(rssi));
            } else if (BATTERY.equals(channelId)) {
                boolean state = (data[2] >> 6) == 3;
                updateState(channelUID, state ? OnOffType.ON : OnOffType.OFF);
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
        final Thing thing = getThing();
        Objects.requireNonNull(thing, "HidekiBaseHandler: Thing may not be null.");

        logger.debug("Initialize Hideki base handler.");
        updateStatus(ThingStatus.ONLINE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        logger.debug("Dispose Hideki base handler.");
        super.dispose();
        data = null;
    }

    /**
     * Update sensor values of current thing with new data.
     *
     * @param data Data value to update with
     */
    public void setData(final int[] data) {
        final Thing thing = getThing();
        Objects.requireNonNull(thing, "HidekiBaseHandler: Thing may not be null.");
        if (ThingStatus.ONLINE != thing.getStatus()) {
            return;
        }

        if (getSensorType() == getDecodedType(data)) {
            if (data.length == getDecodedLength(data)) {
                synchronized (this) {
                    if (this.data == null) {
                        this.data = new int[data.length];
                    }
                    System.arraycopy(data, 0, this.data, 0, data.length);
                }
                for (final Channel channel : thing.getChannels()) {
                    final @NonNull ChannelUID channelUID = channel.getUID();
                    if (RECEIVED_UPDATE.equalsIgnoreCase(channelUID.getId())) {
                        updateState(channelUID, new DateTimeType(ZonedDateTime.now()));
                    } else {
                        handleCommand(channelUID, RefreshType.REFRESH);
                    }
                }
            } else {
                this.data = null;
                logger.warn("Got wrong sensor data length {}.", data.length);
            }
        }
    }

    /**
     * Returns sensor type supported by handler.
     *
     * @return Supported sensor type
     */
    protected abstract int getSensorType();

    /**
     * Returns decoded sensor type. Is negative, if decoder failed.
     *
     * @return Decoded sensor type
     */
    protected static int getDecodedType(final int[] data) {
        return (data == null) || (data.length < 4) ? -1 : data[3] & 0x1F;
    }

    /**
     * Returns decoded data length. Is negative, if decoder failed.
     *
     * @return Decoded sensor type
     */
    protected int getDecodedLength(final int[] data) {
        final int length = (data == null) || (data.length < 3) ? -1 : (data[2] >> 1) & 0x1F;
        return length < 0 ? length : length + 2; // First byte is 0x9F preamble and last RSSI value
    }

}
