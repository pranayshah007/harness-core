/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditEventPublisherJobConfiguration {
  @Bean
  @Qualifier(value = "auditEventPublisherJob")
  public Job auditEventPublisherJob(JobBuilderFactory jobBuilderFactory, Step auditEventPublisherStep) {
    return jobBuilderFactory.get("auditEventPublisherJob")
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
    return new AuditEventPublisherTasklet();
  }
}
