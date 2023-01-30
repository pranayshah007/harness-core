/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.auditevent.streaming.services.AuditEventStreamingService;
import io.harness.auditevent.streaming.services.StreamingDestinationService;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

@Slf4j
public class AuditEventPublisherTasklet implements Tasklet {
  private final StreamingDestinationService streamingDestinationService;
  private final AuditEventStreamingService auditEventStreamingService;

  public AuditEventPublisherTasklet(
      StreamingDestinationService streamingDestinationService, AuditEventStreamingService auditEventStreamingService) {
    this.streamingDestinationService = streamingDestinationService;
    this.auditEventStreamingService = auditEventStreamingService;
  }

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    JobParameters jobParameters = chunkContext.getStepContext().getStepExecution().getJobParameters();
    String accountIdentifier = jobParameters.getString("accountIdentifier");
    List<StreamingDestination> streamingDestinations = streamingDestinationService.list(accountIdentifier,
        StreamingDestinationFilterProperties.builder().status(StreamingDestinationDTO.StatusEnum.ACTIVE).build());
    streamingDestinations.forEach((StreamingDestination streamingDestination) -> {
      log.info(getFullLogMessage("Started for", streamingDestination));
      auditEventStreamingService.stream(streamingDestination, jobParameters);
      log.info(getFullLogMessage("Completed for", streamingDestination));
    });
    return RepeatStatus.FINISHED;
  }

  private String getFullLogMessage(String message, StreamingDestination streamingDestination) {
    return String.format("%s [streamingDestination=%s] [accountIdentifier=%s]", message,
        streamingDestination.getIdentifier(), streamingDestination.getAccountIdentifier());
  }
}
