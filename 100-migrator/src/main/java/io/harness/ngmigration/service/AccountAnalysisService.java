/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder.OrderType;
import io.harness.beans.WorkflowType;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.EntityExecutionSummary;
import io.harness.ngmigration.beans.summary.ExecutionSummary;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class AccountAnalysisService {
  @Inject DiscoveryService discoveryService;
  @Inject private WorkflowExecutionService workflowExecutionService;

  public Map<NGMigrationEntityType, BaseSummary> getSummary(String accountId) {
    return getSummary(accountId, null);
  }

  public Map<NGMigrationEntityType, BaseSummary> getSummary(String accountId, String appId) {
    NGMigrationEntityType migrationEntityType =
        StringUtils.isBlank(appId) ? NGMigrationEntityType.ACCOUNT : NGMigrationEntityType.APPLICATION;
    String entityId = StringUtils.isBlank(appId) ? accountId : appId;
    Map<NGMigrationEntityType, BaseSummary> summary =
        discoveryService.getSummary(accountId, appId, entityId, migrationEntityType);

    PageRequest<WorkflowExecution> pageRequest = new PageRequest<>();
    pageRequest.addFilter(WorkflowExecution.ACCOUNT_ID_KEY, Operator.EQ, accountId);
    if (StringUtils.isNotBlank(appId)) {
      pageRequest.addFilter(WorkflowExecutionKeys.appId, Operator.EQ, appId);
    }
    pageRequest.addFilter(WorkflowExecutionKeys.cdPageCandidate, Operator.EQ, true);
    pageRequest.addOrder(WorkflowExecutionKeys.createdAt, OrderType.DESC);
    pageRequest.setLimit(PageRequest.UNLIMITED);
    List<WorkflowExecution> executions = workflowExecutionService.listExecutions(pageRequest, false).getResponse();

    Map<String, EntityExecutionSummary> popular = new HashMap<>();
    Map<String, Long> popularApps = new HashMap<>();
    Map<String, Long> typeSummary = new HashMap<>();

    executions.forEach(execution -> {
      WorkflowType type = execution.getWorkflowType();
      popularApps.put(execution.getAppId(), popularApps.getOrDefault(execution.getAppId(), 0L) + 1);
      typeSummary.put(type.name(), typeSummary.getOrDefault(type.name(), 0L) + 1);
      popular.putIfAbsent(execution.getWorkflowId(),
          EntityExecutionSummary.builder()
              .appId(execution.getAppId())
              .type(type)
              .uuid(execution.getWorkflowId())
              .count(0)
              .build());
      popular.get(execution.getWorkflowId()).incrementCount();
    });

    summary.put(NGMigrationEntityType.WORKFLOW_EXECUTION,
        ExecutionSummary.builder()
            .count(executions.size())
            .typeSummary(typeSummary)
            .popularApps(popularApps)
            .popular(popular.values()
                         .stream()
                         .sorted((p1, p2) -> (int) (p1.getCount() - p2.getCount()))
                         .collect(Collectors.toList()))
            .build());

    return summary;
  }
}
