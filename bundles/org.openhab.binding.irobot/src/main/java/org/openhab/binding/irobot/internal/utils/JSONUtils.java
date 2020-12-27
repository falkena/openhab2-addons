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
package org.openhab.binding.irobot.internal.utils;

import java.math.BigDecimal;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The {@link JSONUtils} are some utilities to handle JSON. Recursive "search" is adapted from
 * https://stackoverflow.com/questions/63763474/recursive-key-search-in-jsonelement-does-not-return-value
 * 
 * @author Alexander Falkenstern - Initial contribution
 */
@NonNullByDefault
public class JSONUtils {
    public static @Nullable String getAsJSONString(final String key, final JsonElement tree) {
        final JsonElement element = find(key, tree);
        return (element != null) ? element.toString() : null;
    }

    public static @Nullable BigDecimal getAsDecimal(final String key, final JsonElement tree) {
        BigDecimal value = null;

        final JsonElement element = find(key, tree);
        if ((element != null) && element.isJsonPrimitive()) {
            try {
                value = element.getAsJsonPrimitive().getAsBigDecimal();
            } catch (NumberFormatException exception) {
                value = null;
            }
        }

        return value;
    }

    public static @Nullable Boolean getAsBoolean(final String key, final JsonElement tree) {
        Boolean value = null;

        final JsonElement element = find(key, tree);
        if ((element != null) && element.isJsonPrimitive()) {
            value = element.getAsJsonPrimitive().getAsBoolean();
        }

        return value;
    }

    public static @Nullable String getAsString(final String key, final JsonElement tree) {
        String value = null;

        final JsonElement element = find(key, tree);
        if ((element != null) && element.isJsonPrimitive()) {
            value = element.getAsJsonPrimitive().getAsString();
        }

        return value;
    }

    public static @Nullable JsonElement find(final String key, final JsonElement tree) {
        JsonElement value = null;
        if (tree.isJsonArray()) {
            for (final JsonElement entry : tree.getAsJsonArray()) {
                value = find(key, entry);
                if (value != null) {
                    break;
                }
            }
        } else if (tree.isJsonObject()) {
            final JsonObject jsonObject = tree.getAsJsonObject();
            for (final Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                if (key.equals(entry.getKey())) {
                    value = entry.getValue();
                    break;
                } else {
                    value = find(key, entry.getValue());
                    if (value != null) {
                        break;
                    }
                }
            }
        } else {
            if (key.equals(tree.toString())) {
                value = tree;
            }
        }
        return value;
    }
}
