/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.config;

import static io.harness.authorization.AuthorizationServiceHeader.AUDIT_EVENT_STREAMING;

import io.harness.audit.client.remote.streaming.StreamingDestinationClientModule;

import com.google.inject.AbstractModule;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuditEventStreamingModule extends AbstractModule {
  AuditEventStreamingConfig auditEventStreamingConfig;

  public AuditEventStreamingModule(AuditEventStreamingConfig auditEventStreamingConfig) {
    this.auditEventStreamingConfig = auditEventStreamingConfig;
  }

  @Override
  protected void configure() {
    install(new StreamingDestinationClientModule(auditEventStreamingConfig.getAuditClientConfig(),
        auditEventStreamingConfig.getServiceSecrets().getPlatformServiceSecret(),
        AUDIT_EVENT_STREAMING.getServiceId()));
  }
}
