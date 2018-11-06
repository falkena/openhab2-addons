/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hideki.internal;

import static org.openhab.binding.hideki.internal.HidekiBindingConstants.THING_TYPE_WEATHERSTATION;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.hideki.internal.handler.HidekiWeatherstationHandler;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link HidekiHandlerFactory} is responsible for creating things and
 * thing handlers supported by hideki binding.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
@NonNullByDefault
@Component(service = ThingHandlerFactory.class, configurationPid = "binding.hideki")
public class HidekiHandlerFactory extends BaseThingHandlerFactory {
    static {
        NativeLibraryLoader.load("libhideki.so");
    }

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS;
    static {
        Set<ThingTypeUID> buffer = new HashSet<>();
        buffer.add(THING_TYPE_WEATHERSTATION);
        SUPPORTED_THING_TYPES_UIDS = Collections.unmodifiableSet(buffer);
    }

    /**
     * Constructor.
     */
    public HidekiHandlerFactory() {
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        if (THING_TYPE_WEATHERSTATION.equals(thing.getThingTypeUID())) {
            return new HidekiWeatherstationHandler(thing);
        }

        return null;
    }
}
