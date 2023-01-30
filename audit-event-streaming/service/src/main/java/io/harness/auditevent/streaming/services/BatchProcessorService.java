/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services;

import io.harness.audit.entities.AuditEvent;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.auditevent.streaming.entities.outgoing.OutgoingAuditMessage;

import java.util.List;
import org.springframework.batch.core.JobParameters;

public interface BatchProcessorService {
  List<OutgoingAuditMessage> processAuditEvent(List<AuditEvent> auditEvents);
}
