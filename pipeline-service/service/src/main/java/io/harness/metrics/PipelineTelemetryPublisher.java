/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics;

import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.account.AccountClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.remote.client.CGRestUtils;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
/*
 * Called only for community edition
 */
public class PipelineTelemetryPublisher {
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  @Inject PMSPipelineService pmsPipelineService;
  @Inject PMSExecutionService pmsExecutionService;
  @Inject TelemetryReporter telemetryReporter;
  @Inject AccountClient accountClient;
  String PIPELINES_CREATED_IN_A_DAY = "pipelines_create_in_a_day";
  String TOTAL_NUMBER_OF_PIPELINES = "total_number_of_pipelines";
  String EXECUTIONS_IN_A_DAY = "pipelines_executed_in_a_day";
  String TOTAL_EXECUTIONS = "total_pipeline_executions";

  public void recordTelemetry() {
    log.info("PipelineTelemetryPublisher recordTelemetry execute started.");
    try {
      Long MILLISECONDS_IN_A_DAY = 86400000L;
      Long pipelinesCreatedInADay = 0L;
      Long totalNumberOfPipelines = 0L;
      Long pipelinesExecutedInADay = 0L;
      Long totalPipelinesExecuted = 0L;

      String accountId = getAccountId();
      if (EmptyPredicate.isNotEmpty(accountId) || !accountId.equals(GLOBAL_ACCOUNT_ID)) {
        Criteria criteria =
            Criteria.where(PipelineEntityKeys.createdAt).gt(System.currentTimeMillis() - MILLISECONDS_IN_A_DAY);
        pipelinesCreatedInADay = pmsPipelineService.countAllPipelines(criteria);

        Criteria noCriteria = new Criteria();
        totalNumberOfPipelines = pmsPipelineService.countAllPipelines(noCriteria);

        // Community devs only
        Criteria criteriaExecutions =
            Criteria.where(PlanExecutionSummaryKeys.createdAt).gt(System.currentTimeMillis() - MILLISECONDS_IN_A_DAY);
        pipelinesExecutedInADay = pmsExecutionService.getCountOfExecutions(criteriaExecutions);

        Criteria noCriteriaExecutions = new Criteria();
        totalPipelinesExecuted = pmsExecutionService.getCountOfExecutions(noCriteriaExecutions);

        HashMap<String, Object> map = new HashMap<>();
        map.put("group_type", "Account");
        map.put("group_id", accountId);
        map.put(PIPELINES_CREATED_IN_A_DAY, pipelinesCreatedInADay);
        map.put(TOTAL_NUMBER_OF_PIPELINES, totalNumberOfPipelines);
        map.put(EXECUTIONS_IN_A_DAY, pipelinesExecutedInADay);
        map.put(TOTAL_EXECUTIONS, totalPipelinesExecuted);
        telemetryReporter.sendGroupEvent(accountId, null, map, Collections.singletonMap(AMPLITUDE, true),
            TelemetryOption.builder().sendForCommunity(true).build());
        log.info("Scheduled PipelineTelemetryPublisher event sent!");
      } else {
        log.info("There is no Account found!. Can not send scheduled PipelineTelemetryPublisher event.");
      }
    } catch (Exception e) {
      log.error("PipelineTelemetryPublisher recordTelemetry execute failed.", e);
    } finally {
      log.info("PipelineTelemetryPublisher recordTelemetry execute finished.");
    }
  }

  @VisibleForTesting
  String getAccountId() {
    List<AccountDTO> accountDTOList = CGRestUtils.getResponse(accountClient.listAccounts(0, 2)).getResponse();
    String accountId = accountDTOList.get(0).getIdentifier();
    if (accountDTOList.size() > 1 && accountId.equals(GLOBAL_ACCOUNT_ID)) {
      accountId = accountDTOList.get(1).getIdentifier();
    }
    return accountId;
  }
}
