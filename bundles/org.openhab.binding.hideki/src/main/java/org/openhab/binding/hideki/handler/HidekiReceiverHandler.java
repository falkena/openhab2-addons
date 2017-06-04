/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hideki.handler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.hideki.config.HidekiReceiverConfiguration;
import org.openhab.binding.hideki.internal.HidekiDecoder;
import org.openhab.binding.hideki.internal.HidekiReceiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HidekiReceiverHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
@NonNullByDefault
public class HidekiReceiverHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(HidekiReceiverHandler.class);

    @Nullable
    private HidekiDecoder decoder;
    private Set<HidekiBaseHandler> handlers = new HashSet<>();
    private HidekiReceiverConfiguration config = getConfigAs(HidekiReceiverConfiguration.class);

    @Nullable
    private ScheduledFuture<?> readerJob = null;
    private final Runnable dataReader = new Runnable() {
        @Override
        public void run() {
            if (decoder != null) {
                final int[] data = decoder.getDecodedData();
                if ((data != null) && (data[0] == 0x9F)) {
                    synchronized (handlers) {
                        for (HidekiBaseHandler handler : handlers) {
                            handler.setData(data);
                        }
                    }
                    if (logger.isTraceEnabled()) {
                        logger.trace("Fetched new data: {}.", Arrays.toString(data));
                    }
                }
            }

        }
    };

    public HidekiReceiverHandler(Bridge bridge) {
        super(bridge);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Receiver will process no commands
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        final Thing thing = getThing();
        Objects.requireNonNull(thing, "HidekiReceiverHandler: Thing may not be null.");

        synchronized (config) {
            config = getConfigAs(HidekiReceiverConfiguration.class);
        }

        logger.debug("Initialize Hideki receiver handler.");

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
        logger.debug("Dispose Hideki receiver handler.");
        super.dispose();

        if (readerJob != null) {
            readerJob.cancel(false);
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                logger.debug("Dispose Hideki receiver handler throw an error: {}.", exception.getMessage());
            }
            readerJob = null;
        }
        if (decoder != null) {
            decoder.stop();
            decoder = null;
        }
        logger.info("Destroy hideki reader job.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        super.childHandlerInitialized(childHandler, childThing);
        if (childHandler instanceof HidekiBaseHandler) {
            final HidekiBaseHandler handler = (HidekiBaseHandler) childHandler;
            synchronized (handlers) {
                final String type = String.format("%02X", handler.getSensorType());
                if (!handlers.contains(handler)) {
                    handlers.add(handler);
                    logger.debug("Insert handler for sensor 0x{}.", type);
                } else {
                    logger.info("Handler {} for sensor 0x{} already registered.", childThing.getUID(), type);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof HidekiBaseHandler) {
            final HidekiBaseHandler handler = (HidekiBaseHandler) childHandler;
            synchronized (handlers) {
                final String type = String.format("%02X", handler.getSensorType());
                if (handlers.contains(handler)) {
                    handlers.remove(handler);
                    logger.debug("Remove handler for sensor 0x{}.", type);
                } else {
                    logger.info("Handler {} for sensor 0x{} already disposed.", childThing.getUID(), type);
                }
            }
        }
        super.childHandlerDisposed(childHandler, childThing);
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

}
