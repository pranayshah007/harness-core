/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import io.harness.batch.processing.billing.timeseries.data.NodePodId;
import io.harness.batch.processing.billing.timeseries.service.impl.PodCountComputationServiceImpl;
import io.harness.batch.processing.ccm.CCMJobConstants;

import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class NodePodCountDataTasklet implements Tasklet {
  @Autowired private PodCountComputationServiceImpl podCountComputationService;

  private JobParameters parameters;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
    parameters = chunkContext.getStepContext().getStepExecution().getJobParameters();
    String accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
    Instant startTime = CCMJobConstants.getFieldValueFromJobParams(parameters, CCMJobConstants.JOB_START_DATE);
    Instant endTime = CCMJobConstants.getFieldValueFromJobParams(parameters, CCMJobConstants.JOB_END_DATE);

    List<NodePodId> nodes = podCountComputationService.getNodes(accountId, startTime, endTime);
    nodes.forEach(nodePodId -> {
      NodePodId pods = podCountComputationService.getPods(
          accountId, nodePodId.getClusterId(), nodePodId.getNodeId(), startTime, endTime);
      podCountComputationService.computePodCountForNodes(
          accountId, startTime.toEpochMilli(), endTime.toEpochMilli(), pods);
    });

    return null;
  }
}
