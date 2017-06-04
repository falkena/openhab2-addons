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

import java.util.Arrays;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.unit.ImperialUnits;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HidekiAnemometerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
public class HidekiAnemometerHandler extends HidekiBaseHandler {

    private final Logger logger = LoggerFactory.getLogger(HidekiAnemometerHandler.class);

    private static final int TYPE = 0x0C;
    private int[] data = null;

    public HidekiAnemometerHandler(Thing thing) {
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
            if (TEMPERATURE.equals(channelId)) {
                double temperature = (data[5] & 0x0F) * 10 + (data[4] >> 4) + (data[4] & 0x0F) * 0.1;
                if ((data[5] >> 4) != 0x0C) {
                    temperature = (data[5] >> 4) == 0x04 ? -temperature : Double.MAX_VALUE;
                }
                updateState(channelUID, new QuantityType<>(temperature, SIUnits.CELSIUS));
            } else if (CHILL.equals(channelId)) {
                double chill = (data[7] & 0x0F) * 10 + (data[6] >> 4) + (data[6] & 0x0F) * 0.1;
                if ((data[7] >> 4) != 0x0C) {
                    chill = (data[7] >> 4) == 0x04 ? -chill : Double.MAX_VALUE;
                }
                updateState(channelUID, new QuantityType<>(chill, SIUnits.CELSIUS));
            } else if (SPEED.equals(channelId)) {
                double speed = (data[8] >> 4) + (data[8] & 0x0F) / 10.0 + (data[9] & 0x0F) * 10.0;
                updateState(channelUID, new QuantityType<>(speed, ImperialUnits.MILES_PER_HOUR));
            } else if (GUST.equals(channelId)) {
                double gust = (data[9] >> 4) / 10.0 + (data[10] & 0x0F) + (data[10] >> 4) * 10.0;
                updateState(channelUID, new QuantityType<>(gust, ImperialUnits.MILES_PER_HOUR));
            } else if (DIRECTION.equals(channelId)) {
                int segment = (data[11] >> 4);
                segment = segment ^ (segment & 8) >> 1;
                segment = segment ^ (segment & 4) >> 1;
                segment = segment ^ (segment & 2) >> 1;
                updateState(channelUID, new QuantityType<>(22.5 * (-segment & 0xF), SmartHomeUnits.DEGREE_ANGLE));
            } else {
                super.handleCommand(channelUID, command);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        final Thing thing = getThing();
        Objects.requireNonNull(thing, "HidekiAnemometerHandler: Thing may not be null.");

        logger.debug("Initialize Hideki anemometer handler.");

        super.initialize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        logger.debug("Dispose anemometer handler.");
        super.dispose();
        data = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setData(final int[] data) {
        final Thing thing = getThing();
        Objects.requireNonNull(thing, "HidekiAnemometerHandler: Thing may not be null.");
        if (ThingStatus.ONLINE != thing.getStatus()) {
            return;
        }

        if (TYPE == getDecodedType(data)) {
            if (data.length == getDecodedLength(data)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Got new anemometer data: {}.", Arrays.toString(data));
                }
                synchronized (this) {
                    if (this.data == null) {
                        this.data = new int[data.length];
                    }
                    System.arraycopy(data, 0, this.data, 0, data.length);
                }
                super.setData(data);
            } else {
                this.data = null;
                logger.warn("Got wrong anemometer data length {}.", data.length);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getSensorType() {
        return TYPE;
    }

}
