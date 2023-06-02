/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.schedule;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.pubsub.consumer.BigQueryUpdateTopicSubscriber;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Configuration
public class PubSubScheduler {
  @Autowired private BigQueryUpdateTopicSubscriber bigQueryUpdateTopicSubscriber;
  @Autowired private BatchMainConfig config;

  // This infinite fixed delay is to start the subscriber only once when the Application starts
  @Scheduled(fixedDelay = Long.MAX_VALUE)
  public void startBigQueryUpdateTopicSubscriber() {
    log.info("Inside startBigQueryUpdateTopicSubscriber");
    if (config.getGcpConfig().getBigQueryUpdatePubSubTopic().isEnabled()) {
      try {
        bigQueryUpdateTopicSubscriber.subscribeAsync();
        log.info("Started bigQueryUpdateTopicSubscriber");
      } catch (Exception e) {
        log.error("PubSubScheduler: Failed to start BigQueryUpdateTopicSubscriber", e);
      }
    }
  }
}
