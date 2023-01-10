/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.mainDashboard;

import static io.harness.generator.EnvironmentGenerator.Environments.GENERIC_TEST;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.EnvironmentType;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.WorkflowUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.SettingGenerator;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.MainDashboardRestUtils;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.stats.DeploymentStatistics;
import software.wings.beans.stats.DeploymentStatistics.AggregatedDayStats;
import software.wings.beans.stats.ServiceInstanceStatistics;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MainDashboardFunctionalTests extends AbstractFunctionalTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private SettingGenerator settingGenerator;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private WorkflowUtils workflowUtils;

  private Application application;

  private final Seed seed = new Seed(0);
  private Owners owners;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST);
    assertThat(application).isNotNull();
  }

  @Test
  @Owner(developers = SHUBHANSHU, intermittent = true)
  @Category(FunctionalTests.class)
  public void dashboardUpdateTest() {
    // REST API.
    DeploymentStatistics dashBoardResponse =
        MainDashboardRestUtils.checkDeployments(bearerToken, application.getAccountId());
    // Main Dashboard response check
    assertThat(dashBoardResponse).isNotNull();

    EnvironmentType all = EnvironmentType.ALL;
    AggregatedDayStats dashboardStats = dashBoardResponse.getStatsMap().get(all);
    int totalCount = dashboardStats.getTotalCount();
    int failedCount = dashboardStats.getFailedCount();
    int instancesCount = dashboardStats.getInstancesCount();
    // Checking that dashboard fields are non negative
    assertThat(totalCount).isGreaterThanOrEqualTo(0);
    assertThat(failedCount).isGreaterThanOrEqualTo(0);
    assertThat(instancesCount).isGreaterThanOrEqualTo(0);

    // Executing a workflow and inspecting dashboard changes
    Environment environment = environmentGenerator.ensurePredefined(seed, owners, GENERIC_TEST);
    assertThat(environment).isNotNull();

    Workflow sampleWorkflow = workflowUtils.buildCanaryWorkflowPostDeploymentStep(
        "Test Workflow mainDashboard - " + System.currentTimeMillis(), environment.getUuid());

    // REST API.
    Workflow savedWorkflow = WorkflowRestUtils.createWorkflow(
        bearerToken, application.getAccountId(), application.getUuid(), sampleWorkflow);
    assertThat(savedWorkflow).isNotNull();

    // Test running the workflow
    WorkflowExecution workflowExecution = runWorkflow(bearerToken, application.getUuid(), environment.getUuid(),
        savedWorkflow.getUuid(), Collections.<Artifact>emptyList());
    assertThat(workflowExecution).isNotNull();

    // Checking dashboard after executing workflow
    dashBoardResponse = MainDashboardRestUtils.checkDeployments(bearerToken, application.getAccountId());
    assertThat(dashBoardResponse).isNotNull();

    AggregatedDayStats updatedDashboardStats = dashBoardResponse.getStatsMap().get(all);
    int updatedTotalCount = updatedDashboardStats.getTotalCount();
    // Main Dashboard update check (after executing a workflow)
    // Handled the case of parallel workflow executions
    assertThat(updatedTotalCount - totalCount).isGreaterThan(0);
    // Deleting the workflow
    WorkflowRestUtils.deleteWorkflow(bearerToken, savedWorkflow.getUuid(), application.getAppId());
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(FunctionalTests.class)
  public void mostActiveServicesTest() {
    // REST API.
    ServiceInstanceStatistics mostActiveServicesResponse =
        MainDashboardRestUtils.checkMostActiveServices(bearerToken, application.getAccountId());
    // Most Active Services response check
    assertThat(mostActiveServicesResponse).isNotNull();
  }
}
