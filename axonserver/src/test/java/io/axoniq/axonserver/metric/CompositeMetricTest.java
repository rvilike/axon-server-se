/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.metric;

import org.junit.*;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

/**
 * Created by Sara Pellegrini on 18/04/2018.
 * sara.pellegrini@gmail.com
 */
public class CompositeMetricTest {

    CompositeMetric compositeMetric = new CompositeMetric(asList(new FakeClusterMetric(1, 4,8,9),
                                                                 new FakeClusterMetric(2, 3,5,3)));

    @Test
    public void size() {
        assertEquals(3L, compositeMetric.size());
    }

    @Test
    public void min() {
        assertEquals(3L, compositeMetric.min());
    }

    @Test
    public void max() {
        assertEquals(8L, compositeMetric.max());
    }

    @Test
    public void mean() {
        assertEquals((double) 5, compositeMetric.mean(), 0);
    }
}