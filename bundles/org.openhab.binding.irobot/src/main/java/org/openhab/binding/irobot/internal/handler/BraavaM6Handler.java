/**
 * Copyright (c) 2021- Alexander Falkenstern
 *
 * License: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.openhab.binding.irobot.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.irobot.internal.IRobotChannelContentProvider;
import org.openhab.binding.irobot.internal.utils.IRobotMap;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.thing.Thing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.*;

/**
 * The {@link BraavaM6Handler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
@NonNullByDefault
public class BraavaM6Handler extends RoombaCommonHandler {
    private final Logger logger = LoggerFactory.getLogger(BraavaM6Handler.class);
    private final JsonParser jsonParser = new JsonParser();

    private IRobotMap lastCleanMap = new IRobotMap();

    public BraavaM6Handler(Thing thing, IRobotChannelContentProvider channelContentProvider,
            LocaleProvider localeProvider) {
        super(thing, channelContentProvider, localeProvider);
    }
}
