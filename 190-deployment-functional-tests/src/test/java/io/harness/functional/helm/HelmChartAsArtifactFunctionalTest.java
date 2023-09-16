/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.helm;

import static io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions.K8S_ROLLING_TEST;
import static io.harness.k8s.model.HelmVersion.V3;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.PageRequest;
import io.harness.category.element.CDFunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.utils.HelmHelper;
import io.harness.functional.utils.K8SUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.SettingGenerator;
import io.harness.rule.Owner;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.helm.HelmChartConfig;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HelmChartAsArtifactFunctionalTest extends AbstractFunctionalTest {
  private static final String HELM_GCS_BASE_PATH = "helmv3/charts";
  private static final String CHART_NAME = "harness-todolist";
  private static final String HELM_SERVICE_NAME = "Helm Chart As Artifact";
  private static final String HELM_WORKFLOW_NAME = "Helm Chart As Artifact Deploy";
  private static final String K8S_HELM_SERVICE_NAME = "K8s Helm Chart As Artifact";
  private static final String K8S_HELM_WORKFLOW_NAME = "K8s Helm Chart As Artifact Deploy";
  private static final String DEPLOY_HELM_CHART_VERSION = "1.0.0";
  private static final List<HelmChart> AVAILABLE_HELM_CHARTS =
      Arrays.asList(HelmChart.builder().name(CHART_NAME).version("1.0.0").build(),
          HelmChart.builder().name(CHART_NAME).version("1.1.0").build());

  @Inject private OwnerManager ownerManager;
  @Inject private SettingGenerator settingGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private HelmHelper helmHelper;
  @Inject private HelmChartService helmChartService;
  @Inject private ServiceGenerator serviceGenerator;

  private OwnerManager.Owners owners;
  private Application application;

  private final Randomizer.Seed seed = new Randomizer.Seed(0);

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = owners.obtainApplication(
        () -> applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST));

    resetCache(owners.obtainAccount().getUuid());
    logManagerFeatureFlags(application.getAccountId());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(CDFunctionalTests.class)
  public void helmWithHelmChart() {
    String releaseName = helmHelper.getReleaseName(HELM_SERVICE_NAME);
    Service service = createHelmService();
    // Will simulate background job to not be dependent external environment
    HelmChart helmChartToDeploy = createHelmChartsAndGetWithVersion(service, DEPLOY_HELM_CHART_VERSION);
    InfrastructureDefinition infraDefinition = infrastructureDefinitionGenerator.ensurePredefined(
        seed, owners, InfrastructureDefinitionGenerator.InfrastructureDefinitions.GCP_HELM);
    Workflow workflow =
        helmHelper.createHelmWorkflow(seed, owners, HELM_WORKFLOW_NAME, releaseName, service, infraDefinition);
    Workflow cleanupWorkflow =
        helmHelper.createCleanupWorkflow(seed, owners, HELM_WORKFLOW_NAME, releaseName, service, infraDefinition);

    ExecutionArgs executionArgs = getExecutionArgs(workflow, helmChartToDeploy);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, service.getAppId(), infraDefinition.getUuid(), executionArgs);

    try {
      logStateExecutionInstanceErrors(workflowExecution);
      assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
      assertHelmChartVersion(workflowExecution, service);
    } finally {
      // Cleanup
      logStateExecutionInstanceErrors(runWorkflow(bearerToken, service.getAppId(), infraDefinition.getUuid(),
          getExecutionArgs(cleanupWorkflow, helmChartToDeploy)));
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(CDFunctionalTests.class)
  public void k8sHelmWithHelmChart() {
    Service service = createK8sHelmService();
    // Will simulate background job to not be dependent external environment
    HelmChart helmChartToDeploy = createHelmChartsAndGetWithVersion(service, DEPLOY_HELM_CHART_VERSION);
    InfrastructureDefinition infraDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, K8S_ROLLING_TEST);
    Workflow workflow = K8SUtils.createWorkflow(application.getUuid(), infraDefinition.getEnvId(), service.getUuid(),
        infraDefinition.getUuid(), K8S_HELM_WORKFLOW_NAME, OrchestrationWorkflowType.ROLLING, bearerToken,
        getAccount().getUuid());
    Workflow cleanupWorkflow = K8SUtils.createK8sCleanupWorkflow(application.getUuid(), infraDefinition.getEnvId(),
        service.getUuid(), infraDefinition.getUuid(), K8S_HELM_WORKFLOW_NAME, bearerToken, getAccount().getUuid());

    ExecutionArgs executionArgs = getExecutionArgs(workflow, helmChartToDeploy);
    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, service.getAppId(), infraDefinition.getUuid(), executionArgs);

    try {
      logStateExecutionInstanceErrors(workflowExecution);
      assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
      assertHelmChartVersion(workflowExecution, service);
    } finally {
      // Cleanup
      logStateExecutionInstanceErrors(runWorkflow(bearerToken, service.getAppId(), infraDefinition.getUuid(),
          getExecutionArgs(cleanupWorkflow, helmChartToDeploy)));
    }
  }

  private Service createHelmService() {
    SettingAttribute helmGCSConnector =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.HELM_GCS_CONNECTOR);

    HelmChartConfig helmChartConfig = HelmChartConfig.builder()
                                          .connectorId(helmGCSConnector.getUuid())
                                          .chartName(CHART_NAME)
                                          .basePath(HELM_GCS_BASE_PATH)
                                          .build();

    return helmHelper.createHelmService(seed, owners, HELM_SERVICE_NAME, V3, helmChartConfig, true);
  }

  private Service createK8sHelmService() {
    SettingAttribute helmGCSConnector =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.HELM_GCS_CONNECTOR);

    HelmChartConfig helmChartConfig = HelmChartConfig.builder()
                                          .connectorId(helmGCSConnector.getUuid())
                                          .chartName(CHART_NAME)
                                          .basePath(HELM_GCS_BASE_PATH)
                                          .build();

    Service service = serviceGenerator.ensureService(seed, owners,
        Service.builder()
            .name(K8S_HELM_SERVICE_NAME)
            .artifactType(ArtifactType.DOCKER)
            .deploymentType(DeploymentType.KUBERNETES)
            .isK8sV2(true)
            .build());

    helmHelper.applyHelmChartConfigToService(service, helmChartConfig, true);

    return service;
  }

  private HelmChart createHelmChartsAndGetWithVersion(Service service, String version) {
    Map<String, List<HelmChart>> helmChartMap = helmChartService.listHelmChartsForService(
        application.getUuid(), service.getUuid(), null, new PageRequest<>(), true);
    List<HelmChart> existing = helmChartMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());

    Set<String> existingVersion = new HashSet<>();
    // Expect to not have different names, only versions
    existing.stream().map(HelmChart::getVersion).forEach(existingVersion::add);

    List<HelmChart> allCharts = new ArrayList<>(existing);
    for (HelmChart availableHelmChart : AVAILABLE_HELM_CHARTS) {
      if (!existingVersion.contains(availableHelmChart.getVersion())) {
        HelmChart created = helmChartService.create(HelmChart.builder()
                                                        .accountId(service.getAccountId())
                                                        .appId(application.getUuid())
                                                        .name(availableHelmChart.getName())
                                                        .version(availableHelmChart.getVersion())
                                                        .serviceId(service.getUuid())
                                                        .build());
        allCharts.add(created);
      }
    }

    return allCharts.stream()
        .filter(chart -> version.equals(chart.getVersion()))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Missing required version"));
  }

  private ExecutionArgs getExecutionArgs(Workflow workflow, HelmChart helmChart) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setWorkflowType(workflow.getWorkflowType());
    executionArgs.setHelmCharts(singletonList(helmChart));
    return executionArgs;
  }

  private void assertHelmChartVersion(WorkflowExecution workflowExecution, Service service) {
    List<Instance> instances = getActiveInstancesConditional(
        application.getUuid(), service.getUuid(), workflowExecution.getInfraMappingIds().get(0));
    // Should have at least 1 instance, otherwise previous call would fail
    instances.forEach(instance -> {
      assertThat(instance.getInstanceInfo()).isInstanceOf(ContainerInfo.class);
      if (instance.getInstanceInfo() instanceof K8sPodInfo) {
        K8sPodInfo k8sPodInfo = (K8sPodInfo) instance.getInstanceInfo();
        assertThat(k8sPodInfo.getHelmChartInfo()).isNotNull();
        assertThat(k8sPodInfo.getHelmChartInfo().getVersion()).isEqualTo(DEPLOY_HELM_CHART_VERSION);
      } else if (instance.getInstanceInfo() instanceof KubernetesContainerInfo) {
        KubernetesContainerInfo kubernetesContainerInfo = (KubernetesContainerInfo) instance.getInstanceInfo();
        assertThat(kubernetesContainerInfo.getHelmChartInfo()).isNotNull();
        assertThat(kubernetesContainerInfo.getHelmChartInfo().getVersion()).isEqualTo(DEPLOY_HELM_CHART_VERSION);
      } else {
        throw new AssertionError("Unexpected instance type: " + instance.getInstanceInfo().getClass().getSimpleName());
      }
    });
  }
}
