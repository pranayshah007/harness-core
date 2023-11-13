/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.client.impl.tailer;

import io.harness.event.client.impl.EventPublisherConstants;
import io.harness.flow.BackoffScheduler;
import io.harness.govern.ProviderModule;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.RollingChronicleQueue;

@RequiredArgsConstructor
@Slf4j
public class DelegateTailerModule extends ProviderModule {
  private final Config config;

  @Provides
  @Singleton
  @Named("tailer")
  RollingChronicleQueue chronicleQueue() {
    return ChronicleQueue.singleBuilder(config.queueFilePath)
        .rollCycle(EventPublisherConstants.QUEUE_ROLL_CYCLE)
        .timeoutMS(EventPublisherConstants.QUEUE_TIMEOUT_MS)
        .build();
  }

  @Provides
  @Singleton
  @Named("tailer")
  BackoffScheduler backoffScheduler() {
    return new BackoffScheduler(ChronicleEventTailer.class.getSimpleName(), config.getMinDelay(), config.getMaxDelay());
  }

  @Value
  @Builder
  public static class Config {
    String publishTarget;
    String publishAuthority;
    String queueFilePath;
    @Builder.Default Duration minDelay = Duration.ofSeconds(1);
    @Builder.Default Duration maxDelay = Duration.ofMinutes(5);
    String clientCertificateFilePath;
    String clientCertificateKeyFilePath;

    // As of now ignored (always trusts all certs)
    boolean trustAllCertificates;
  }
}
