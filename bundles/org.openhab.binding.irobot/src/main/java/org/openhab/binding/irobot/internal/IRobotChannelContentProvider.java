/**
 * Copyright (c) 2021- Alexander Falkenstern
 *
 * License: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.openhab.binding.irobot.internal;

import static org.openhab.binding.irobot.internal.IRobotBindingConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseDynamicStateDescriptionProvider;
import org.openhab.core.thing.type.DynamicStateDescriptionProvider;
import org.openhab.core.types.StateOption;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@NonNullByDefault
@Component(service = { DynamicStateDescriptionProvider.class, IRobotChannelContentProvider.class }, immediate = true)
public class IRobotChannelContentProvider extends BaseDynamicStateDescriptionProvider {

    @Activate
    public IRobotChannelContentProvider() {
    }

    public void setLanguages(ChannelUID channelUID, JsonElement options) {
        final ThingUID thingUID = channelUID.getThingUID();
        if (BINDING_ID.equals(thingUID.getBindingId()) && options.isJsonArray()) {
            if (CHANNEL_CONTROL_LANGUAGE.equals(channelUID.getIdWithoutGroup())) {
                List<StateOption> buffer = new ArrayList<>();
                for (final JsonElement entry : options.getAsJsonArray()) {
                    if (entry.isJsonObject()) {
                        final JsonObject object = entry.getAsJsonObject();
                        for (final Map.Entry<String, JsonElement> element : object.entrySet()) {
                            final JsonElement value = element.getValue();
                            if (value.isJsonPrimitive()) {
                                final String index = value.getAsJsonPrimitive().getAsString();
                                final Locale locale = Locale.forLanguageTag(element.getKey());
                                buffer.add(new StateOption(index, locale.getDisplayName()));
                            }
                        }
                    }
                }
                buffer.add(new StateOption(UNKNOWN, "Unknown"));
                setStateOptions(channelUID, buffer);
            }
        }
    }

    public boolean isChannelPopulated(ChannelUID channelUID) {
        return channelOptionsMap.containsKey(channelUID);
    }
}
