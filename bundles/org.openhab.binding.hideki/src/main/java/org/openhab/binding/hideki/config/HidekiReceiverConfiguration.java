/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hideki.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link PLCLogoBridgeConfiguration} hold configuration of Siemens LOGO! PLCs.
 *
 * @author Alexander Falkenstern - Initial contribution
 */
@NonNullByDefault
public class HidekiReceiverConfiguration {

    private String receiver;
    private Integer pin;

    private Integer refresh = 1;
    private Integer timeout = -1;
    private String device = "/dev/spidev0.0";
    private Integer interrupt = 0;

    public HidekiReceiverConfiguration() {
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(final String receiver) {
        this.receiver = receiver.trim();
    }

    /**
     * Get configured GPIO pin receiver connected to.
     *
     * @return Configured GPIO pin
     */
    public Integer getGpioPin() {
        return pin;
    }

    /**
     * Set GPIO pin receiver connected to.
     *
     * @param pin GPIO pin receiver connected to
     */
    public void setGpioPin(final Integer pin) {
        this.pin = pin;
    }

    /**
     * Get configured refresh rate for new decoder data check in seconds.
     *
     * @return Configured refresh rate for new decoder data check
     */
    public Integer getRefreshRate() {
        return refresh;
    }

    /**
     * Set refresh rate for new decoder data check in seconds.
     *
     * @param rate Refresh rate for new decoder data check
     */
    public void setRefreshRate(final Integer rate) {
        this.refresh = rate;
    }

    /**
     * Get configured wait period for edge on GPIO pin in milliseconds.
     *
     * @return Configured wait period for edge on GPIO pin
     */
    public Integer getTimeout() {
        return timeout;
    }

    /**
     * Set wait period for edge detection on GPIO pin in milliseconds.
     * If 0, decoder will return immediately, even if no edge detected.
     * If smaller 0, decoder will wait infinite.
     *
     * @param timeout Wait period for edge on GPIO pin
     */
    public void setTimeout(final Integer timeout) {
        this.timeout = timeout;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(final String device) {
        this.device = device.trim();
    }

    public Integer getInterrupt() {
        return interrupt;
    }

    public void setInterrupt(final Integer interrupt) {
        this.interrupt = interrupt;
    }

}
