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
package org.openhab.binding.irobot;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Alexander Falkenstern - Initial contribution
 */
@NonNullByDefault
public class DrawMapTest {
    private List<Point2D> points = new ArrayList<>();

    @BeforeEach
    public void setupTest() {
        InputStream stream = DrawMapTest.class.getResourceAsStream("trajectory.txt");
        assertNotNull(stream, "Couldn't find trajectory data for test");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                line = line.substring(line.indexOf('[') + 1, line.indexOf(']'));
                String[] parts = line.split(",");
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                points.add(new Point2D.Double(x, y));
            }
        } catch (IOException exception) {
            fail("Couldn't read trajectory data for test", exception);
        }
    }

    @AfterEach
    public void cleanupTest() {
        points.clear();
    }

    @Test
    public void getConcaveHull() {
        // ConcaveHull hull = new ConcaveHull();
        // List<Point2D> result = hull.calculate(points, 3);
        // assertFalse(result.isEmpty(), "Got empty concave hull");
    }
}
