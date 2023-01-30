/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import static io.harness.auditevent.streaming.AuditEventStreamingConstants.AUDIT_EVENT_PUBLISHER_JOB;

import io.harness.auditevent.streaming.services.AuditEventStreamingService;
import io.harness.auditevent.streaming.services.StreamingDestinationService;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditEventPublisherJobConfiguration {
  private final StreamingDestinationService streamingDestinationService;
  private final AuditEventStreamingService auditEventStreamingService;

  @Autowired
  public AuditEventPublisherJobConfiguration(
      StreamingDestinationService streamingDestinationService, AuditEventStreamingService auditEventStreamingService) {
    this.streamingDestinationService = streamingDestinationService;
    this.auditEventStreamingService = auditEventStreamingService;
  }

  @Bean
  @Qualifier(value = AUDIT_EVENT_PUBLISHER_JOB)
  public Job auditEventPublisherJob(JobBuilderFactory jobBuilderFactory, Step auditEventPublisherStep) {
    return jobBuilderFactory.get(AUDIT_EVENT_PUBLISHER_JOB)
        .incrementer(new RunIdIncrementer())
        .start(auditEventPublisherStep)
        .build();
  }
  @Bean
  public Step auditEventPublisherStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("auditEventPublisherStep").tasklet(auditEventPublisherTasklet()).build();
  }

  @Bean
  public AuditEventPublisherTasklet auditEventPublisherTasklet() {
    return new AuditEventPublisherTasklet(streamingDestinationService, auditEventStreamingService);
  }
}
