/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hideki.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class HidekiDecoder {

    final @Nullable HidekiReceiver receiver;

    // forbid object construction
    private HidekiDecoder() {
        receiver = null;
    }

    public HidekiDecoder(final HidekiReceiver receiver) {
        this.receiver = receiver;
        create(this.receiver.getId());
    }

    public int getId() {
        return System.identityHashCode(this);
    }

    public native boolean start();

    public native boolean stop();

    public native int @Nullable [] getDecodedData();

    @Override
    protected void finalize() throws Throwable {
        destroy();
        super.finalize();
    }

    private native void create(int receiver);

    private native void destroy();
}
