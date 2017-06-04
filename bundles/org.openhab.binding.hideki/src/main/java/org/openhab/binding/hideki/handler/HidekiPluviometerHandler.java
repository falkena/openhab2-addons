/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hideki.handler;

import static org.openhab.binding.hideki.HidekiBindingConstants.RAIN_LEVEL;

import java.util.Arrays;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HidekiPluviometerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
public class HidekiPluviometerHandler extends HidekiBaseHandler {

    private final Logger logger = LoggerFactory.getLogger(HidekiPluviometerHandler.class);

    private static final int TYPE = 0x0E;
    private int[] data = null;

    public HidekiPluviometerHandler(Thing thing) {
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
            if (RAIN_LEVEL.equals(channelId)) {
                double level = 0.7 * ((data[5] << 8) + data[4]);
                updateState(channelUID, new DecimalType(level));
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
        Objects.requireNonNull(thing, "HidekiPluviometerHandler: Thing may not be null.");

        logger.debug("Initialize Hideki pluviometer handler.");

        super.initialize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        logger.debug("Dispose pluviometer handler.");
        super.dispose();
        data = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setData(final int[] data) {
        final Thing thing = getThing();
        Objects.requireNonNull(thing, "HidekiPluviometerHandler: Thing may not be null.");
        if (ThingStatus.ONLINE != thing.getStatus()) {
            return;
        }

        if (TYPE == getDecodedType(data)) {
            if (data.length == getDecodedLength(data)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Got new pluviometer data: {}.", Arrays.toString(data));
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
                logger.warn("Got wrong pluviometer data length {}.", data.length);
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
