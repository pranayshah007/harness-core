/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.guice.annotation.EnableGuiceModules;

@Slf4j
@Configuration
@EnableGuiceModules
public class AuditEventStreamingModuleConfiguration {
  @Bean
  public AuditEventStreamingModule auditEventStreamingModule(AuditEventStreamingConfig auditEventStreamingConfig) {
    return new AuditEventStreamingModule(auditEventStreamingConfig);
  }
}
