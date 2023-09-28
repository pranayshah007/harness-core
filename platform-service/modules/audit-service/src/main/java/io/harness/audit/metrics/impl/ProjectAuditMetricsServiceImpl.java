/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.metrics.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.metrics.beans.ProjectAuditMetricContext;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class ProjectAuditMetricsServiceImpl {
  public static final String ACTIVE_PROJECT = "project_activity_metrics";

  @Inject private MetricService metricService;

  private void recordAuditMetricForActiveProject(
      String accountIdentifier, int totalProjects, int activeProjects, String metricName) {
    try (ProjectAuditMetricContext ignore =
             new ProjectAuditMetricContext(accountIdentifier, totalProjects, activeProjects)) {
      metricService.incCounter(metricName);
    }
  }
  public void recordAuditMetricForActiveProject(String accountIdentifier, int totalProjects, int activeProjects) {
    recordAuditMetricForActiveProject(accountIdentifier, totalProjects, activeProjects, ACTIVE_PROJECT);
  }
}