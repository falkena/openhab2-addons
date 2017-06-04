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
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.QuantityType;
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
 * The {@link HidekiThermometerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
public class HidekiThermometerHandler extends HidekiBaseHandler {

    private final Logger logger = LoggerFactory.getLogger(HidekiThermometerHandler.class);

    private static final int TYPE = 0x1E;
    private int[] data = null;

    public HidekiThermometerHandler(Thing thing) {
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
            if (SENSOR_CHANNEL.equals(channelId)) {
                int channel = data.length < 2 ? -1 : data[1] >> 5;
                if ((channel == 5) || (channel == 6)) {
                    channel = channel - 1;
                } else if (channel > 3) {
                    channel = -1;
                }
                updateState(channelUID, new DecimalType(channel));
            } else if (TEMPERATURE.equals(channelId)) {
                double temperature = (data[5] & 0x0F) * 10.0 + (data[4] >> 4) + (data[4] & 0x0F) * 0.1;
                if ((data[5] >> 4) != 0x0C) {
                    temperature = (data[5] >> 4) == 0x04 ? -temperature : Double.MAX_VALUE;
                }
                updateState(channelUID, new QuantityType<>(temperature, SIUnits.CELSIUS));
            } else if (HUMIDITY.equals(channelId)) {
                double humidity = (data[6] >> 4) * 10.0 + (data[6] & 0x0F);
                updateState(channelUID, new QuantityType<>(humidity, SmartHomeUnits.PERCENT));
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
        Objects.requireNonNull(thing, "HidekiThermometerHandler: Thing may not be null.");

        logger.debug("Initialize Hideki thermometer handler.");

        super.initialize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        logger.debug("Dispose thermometer handler.");
        super.dispose();
        data = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setData(final int[] data) {
        final Thing thing = getThing();
        Objects.requireNonNull(thing, "HidekiThermometerHandler: Thing may not be null.");
        if (ThingStatus.ONLINE != thing.getStatus()) {
            return;
        }

        if (TYPE == getDecodedType(data)) {
            if (data.length == getDecodedLength(data)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Got new thermometer data: {}.", Arrays.toString(data));
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
                logger.warn("Got wrong thermometer data length {}.", data.length);
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
