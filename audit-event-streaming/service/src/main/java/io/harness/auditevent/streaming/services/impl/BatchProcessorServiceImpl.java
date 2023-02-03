/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.entities.AuditEvent;
import io.harness.audit.streaming.outgoing.OutgoingAuditMessage;
import io.harness.auditevent.streaming.entities.StreamingBatch;
import io.harness.auditevent.streaming.mappers.AuditEventMapper;
import io.harness.auditevent.streaming.services.BatchProcessorService;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Service
public class BatchProcessorServiceImpl implements BatchProcessorService {
  private final AuditEventMapper auditEventMapper;

  @Autowired
  public BatchProcessorServiceImpl(AuditEventMapper auditEventMapper) {
    this.auditEventMapper = auditEventMapper;
  }

  @Override
  public List<OutgoingAuditMessage> processAuditEvent(StreamingBatch streamingBatch, List<AuditEvent> auditEvents) {
    return auditEvents.stream()
        .map(auditEvent -> auditEventMapper.toOutgoingAuditMessage(auditEvent, streamingBatch))
        .collect(Collectors.toList());
  }
}
