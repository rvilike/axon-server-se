/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.localstorage;

import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.GetEventsRequest;
import io.grpc.stub.StreamObserver;
import org.junit.*;
import org.springframework.data.util.CloseableIterator;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.Assert.*;

/**
 * @author Marc Gathier
 */
public class TrackingEventProcessorManagerTest {

    private TrackingEventProcessorManager testSubject;
    private AtomicInteger eventsLeft = new AtomicInteger(10);
    private AtomicBoolean iteratorClosed = new AtomicBoolean();

    public static void assertWithin(int time, TimeUnit unit, Runnable assertion) throws InterruptedException {
        long now = System.currentTimeMillis();
        long deadline = now + unit.toMillis(time);
        do {
            try {
                assertion.run();
                break;
            } catch (AssertionError e) {
                if (now >= deadline) {
                    throw e;
                }
            }
            Thread.sleep(10);
            now = System.currentTimeMillis();
        } while (true);
    }

    @Before
    public void setup() {
        Function<Long, CloseableIterator<SerializedEventWithToken>> iteratorBuilder = (token) -> {
            AtomicLong nextToken = new AtomicLong(token);

            return new CloseableIterator<SerializedEventWithToken>() {
                @Override
                public void close() {
                    iteratorClosed.set(true);
                }

                @Override
                public boolean hasNext() {
                    return eventsLeft.get() > 0;
                }

                @Override
                public SerializedEventWithToken next() {
                    eventsLeft.decrementAndGet();
                    return new SerializedEventWithToken(nextToken.getAndIncrement(), Event.newBuilder().build());
                }
            };
        };
        testSubject = new TrackingEventProcessorManager("demo", iteratorBuilder);
    }

    @Test
    public void createEventTracker() throws InterruptedException {
        GetEventsRequest request = GetEventsRequest.newBuilder()
                                                   .setTrackingToken(100)
                                                   .setNumberOfPermits(5)
                                                   .build();
        AtomicInteger messagesReceived = new AtomicInteger();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean failed = new AtomicBoolean();
        TrackingEventProcessorManager.EventTracker tracker =
                testSubject.createEventTracker(request,
                                               new StreamObserver<InputStream>() {
                                                   @Override
                                                   public void onNext(
                                                           InputStream value) {
                                                       messagesReceived.incrementAndGet();
                                                   }

                                                   @Override
                                                   public void onError(Throwable t) {
                                                       failed.set(true);
                                                   }

                                                   @Override
                                                   public void onCompleted() {
                                                       completed.set(true);
                                                   }
                                               });

        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(5, messagesReceived.get()));
        tracker.addPermits(10);
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(10, messagesReceived.get()));

        eventsLeft.set(10);
        testSubject.reschedule();
        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(15, messagesReceived.get()));

        tracker.stop();
        assertTrue(failed.get());
        assertWithin(10, TimeUnit.MILLISECONDS, () -> assertTrue(iteratorClosed.get()));
    }
}