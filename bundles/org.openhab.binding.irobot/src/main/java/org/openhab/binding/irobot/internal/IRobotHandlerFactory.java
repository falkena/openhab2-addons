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
package org.openhab.binding.irobot.internal;

import static org.openhab.binding.irobot.internal.IRobotBindingConstants.*;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.irobot.internal.config.IRobotConfiguration;
import org.openhab.binding.irobot.internal.handler.BraavaMModelsHandler;
import org.openhab.binding.irobot.internal.handler.Roomba9ModelsHandler;
import org.openhab.binding.irobot.internal.handler.RoombaEModelsHandler;
import org.openhab.binding.irobot.internal.handler.RoombaIModelsHandler;
import org.openhab.binding.irobot.internal.handler.RoombaSModelsHandler;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import nu.pattern.OpenCV;

/**
 * The {@link IRobotHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author hkuhn42 - Initial contribution
 * @author Pavel Fedin - rename and update
 */
@NonNullByDefault
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.irobot")
public class IRobotHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections.singleton(THING_TYPE_ROOMBA);

    private IRobotChannelContentProvider channelContentProvider;
    private LocaleProvider localeProvider;

    static {
        OpenCV.loadShared();
    }

    @Activate
    public IRobotHandlerFactory(@Reference IRobotChannelContentProvider channelContentProvider,
            @Reference LocaleProvider localeProvider) {
        this.channelContentProvider = channelContentProvider;
        this.localeProvider = localeProvider;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (thingTypeUID.equals(THING_TYPE_ROOMBA)) {
            final Configuration config = thing.getConfiguration();
            final Models family = config.as(IRobotConfiguration.class).getFamily();
            if (family == Models.BRAAVA_M_SERIES) {
                return new BraavaMModelsHandler(thing, channelContentProvider, localeProvider);
            } else if (family == Models.ROOMBA_9_SERIES) {
                return new Roomba9ModelsHandler(thing, channelContentProvider, localeProvider);
            } else if (family == Models.ROOMBA_E_SERIES) {
                return new RoombaEModelsHandler(thing, channelContentProvider, localeProvider);
            } else if (family == Models.ROOMBA_I_SERIES) {
                return new RoombaIModelsHandler(thing, channelContentProvider, localeProvider);
            } else if (family == Models.ROOMBA_S_SERIES) {
                return new RoombaSModelsHandler(thing, channelContentProvider, localeProvider);
            }
        }

        return null;
    }
}
