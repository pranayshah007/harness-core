/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.auditevent.streaming.services.StreamingDestinationsService;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class AuditEventPublisherTasklet implements Tasklet {
  @Autowired private StreamingDestinationsService streamingDestinationsService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    JobParameters jobParameters = chunkContext.getStepContext().getStepExecution().getJobParameters();
    String accountIdentifier = jobParameters.getString("accountIdentifier");
    List<StreamingDestination> streamingDestinations = streamingDestinationsService.list(accountIdentifier,
        StreamingDestinationFilterProperties.builder().status(StreamingDestinationDTO.StatusEnum.ACTIVE).build());
    streamingDestinations.forEach((StreamingDestination streamingDestination) -> {
      log.info("Streaming destination: {}", streamingDestination.getIdentifier());
    });
    return null;
  }
}
