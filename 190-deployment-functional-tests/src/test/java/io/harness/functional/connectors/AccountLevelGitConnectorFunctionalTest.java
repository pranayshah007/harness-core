/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.connectors;

import static io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions.GCP_HELM;
import static io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions.K8S_ROLLING_TEST;
import static io.harness.generator.SettingGenerator.Settings.ACCOUNT_LEVEL_GIT_CONNECTOR;
import static io.harness.rule.OwnerRule.ABOSII;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.InfrastructureType.PCF_INFRASTRUCTURE;
import static software.wings.beans.Service.builder;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.WorkflowType;
import io.harness.category.element.CDFunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.utils.HelmHelper;
import io.harness.functional.utils.K8SUtils;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.EnvironmentGenerator.Environments;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.SettingGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.generator.artifactstream.ArtifactStreamManager;
import io.harness.k8s.model.HelmVersion;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.ArtifactStreamRestUtils;
import io.harness.testframework.restutils.ServiceVariablesUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.BasicOrchestrationWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GraphNode;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.WorkflowService;
import software.wings.utils.ArtifactType;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AccountLevelGitConnectorFunctionalTest extends AbstractFunctionalTest {
  private static final String RESOURCE_NAME_FORMAT = "git-account-level-%s-%s";
  private static final String PCF_FUNCTIONAL_TEST_REPO_NAME = "pcf-functional-test";
  private static final String K8S_FUNCTIONAL_TEST_REPO_NAME = "k8s-functional-test";
  private static final String REPO_NAME_SERVICE_VARIABLE = "repoName";

  @Inject private OwnerManager ownerManager;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private SettingGenerator settingGenerator;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private EnvironmentGenerator environmentGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowService workflowService;
  @Inject private ArtifactStreamManager artifactStreamManager;
  @Inject private HelmHelper helmHelper;

  private final Randomizer.Seed seed = new Randomizer.Seed(0);
  private OwnerManager.Owners owners;
  private Application application;
  private SettingAttribute accountGitConnector;

  @Before
  public void setUp() {
    owners = ownerManager.create();
    application = owners.obtainApplication(
        () -> applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST));
    assertThat(application).isNotNull();
    accountGitConnector = settingGenerator.ensurePredefined(seed, owners, ACCOUNT_LEVEL_GIT_CONNECTOR);
    logManagerFeatureFlags(application.getAccountId());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(CDFunctionalTests.class)
  public void testPcfUsingAccountLevelGitConnector() {
    Service service = createPCFService(getName("pcf", "service"));
    updateApplicationManifest(createServiceManifest(service, "pcf-app1", StoreType.Remote));
    setRepoNameServiceVariable(service, PCF_FUNCTIONAL_TEST_REPO_NAME);

    Environment environment = environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST);
    InfrastructureDefinition infraDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, PCF_INFRASTRUCTURE, bearerToken);

    Workflow workflow = createBasicWorkflow(getName("pcf", "wf"), service, infraDefinition);
    workflow = workflowGenerator.ensureWorkflow(seed, owners, workflow);
    setupPcfWorkflow(workflow);

    resetCache(getAccount().getUuid());
    WorkflowExecution workflowExecution = runWorkflow(
        bearerToken, application.getUuid(), environment.getUuid(), getExecutionArgs(workflow, environment, service));
    logStateExecutionInstanceErrors(workflowExecution);
    getFailedWorkflowExecutionLogs(workflowExecution);
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(CDFunctionalTests.class)
  public void testK8sUsingAccountLevelGitConnector() {
    Service service = createK8sService(getName("k8s", "service"));
    updateApplicationManifest(createServiceManifest(service, "manifests/basic", StoreType.Remote));
    setRepoNameServiceVariable(service, K8S_FUNCTIONAL_TEST_REPO_NAME);

    Environment environment = environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST);
    InfrastructureDefinition infraDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, K8S_ROLLING_TEST);

    Workflow workflow = K8SUtils.createWorkflow(application.getUuid(), environment.getUuid(), service.getUuid(),
        infraDefinition.getUuid(), getName("k8s", "wf"), OrchestrationWorkflowType.ROLLING, bearerToken,
        getAccount().getUuid());

    WorkflowExecution workflowExecution = runWorkflow(
        bearerToken, application.getUuid(), environment.getUuid(), getExecutionArgs(workflow, environment, service));
    logStateExecutionInstanceErrors(workflowExecution);
    getFailedWorkflowExecutionLogs(workflowExecution);
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(CDFunctionalTests.class)
  public void testHelmUsingAccountLevelGitConnector() {
    String releaseName = helmHelper.getReleaseName("helm-account-level-git");
    Service service = createHelmService("helm-account-level-git");
    updateApplicationManifest(createServiceManifest(service, "charts/basic", StoreType.HelmSourceRepo));
    setRepoNameServiceVariable(service, K8S_FUNCTIONAL_TEST_REPO_NAME);

    Environment environment = environmentGenerator.ensurePredefined(seed, owners, Environments.GENERIC_TEST);
    InfrastructureDefinition infraDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, GCP_HELM);

    Workflow workflow =
        helmHelper.createHelmWorkflow(seed, owners, getName("helm", "wf"), releaseName, service, infraDefinition);
    Workflow cleanupWorkflow =
        helmHelper.createCleanupWorkflow(seed, owners, getName("helm", "wf"), releaseName, service, infraDefinition);

    WorkflowExecution workflowExecution = runWorkflow(
        bearerToken, application.getUuid(), environment.getUuid(), getExecutionArgs(workflow, environment, service));
    logStateExecutionInstanceErrors(workflowExecution);
    getFailedWorkflowExecutionLogs(workflowExecution);

    // Cleanup
    logStateExecutionInstanceErrors(runWorkflow(bearerToken, application.getUuid(), environment.getUuid(),
        getExecutionArgs(cleanupWorkflow, environment, service)));
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  private void setRepoNameServiceVariable(Service service, String value) {
    ServiceVariable repoNameVariable = ServiceVariable.builder()
                                           .accountId(getAccount().getUuid())
                                           .value(value.toCharArray())
                                           .name(REPO_NAME_SERVICE_VARIABLE)
                                           .type(ServiceVariableType.TEXT)
                                           .entityType(EntityType.SERVICE)
                                           .entityId(service.getUuid())
                                           .build();
    repoNameVariable.setAppId(service.getAppId());
    ServiceVariablesUtils.addOrGetServiceVariable(bearerToken, repoNameVariable);
  }

  private Service createPCFService(String name) {
    owners.add(serviceGenerator.ensureService(seed, owners,
        builder().name(name).artifactType(ArtifactType.PCF).deploymentType(DeploymentType.PCF).isPcfV2(true).build()));
    ArtifactStream artifactStream = artifactStreamManager.ensurePredefined(
        seed, owners, ArtifactStreamManager.ArtifactStreams.HARNESS_SAMPLE_DOCKER);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  private Service createK8sService(String name) {
    owners.add(serviceGenerator.ensureService(seed, owners,
        builder()
            .name(name)
            .artifactType(ArtifactType.DOCKER)
            .deploymentType(DeploymentType.KUBERNETES)
            .isK8sV2(true)
            .build()));
    ArtifactStream artifactStream = artifactStreamManager.ensurePredefined(
        seed, owners, ArtifactStreamManager.ArtifactStreams.HARNESS_SAMPLE_DOCKER);
    Service service = owners.obtainService();
    service.setArtifactStreamIds(new ArrayList<>(Arrays.asList(artifactStream.getUuid())));
    return service;
  }

  private Service createHelmService(String name) {
    owners.add(serviceGenerator.ensureService(seed, owners,
        builder()
            .name(name)
            .artifactType(ArtifactType.DOCKER)
            .helmVersion(HelmVersion.V3)
            .deploymentType(DeploymentType.HELM)
            .appId(application.getUuid())
            .build()));

    return owners.obtainService();
  }

  private ApplicationManifest createServiceManifest(Service service, String filePath, StoreType storeType) {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .serviceId(service.getUuid())
            .kind(AppManifestKind.K8S_MANIFEST)
            .accountId(getAccount().getUuid())
            .storeType(storeType)
            .gitFileConfig(GitFileConfig.builder()
                               .connectorId(accountGitConnector.getUuid())
                               .branch("master")
                               .useBranch(true)
                               .filePath(filePath)
                               .repoName(format("${serviceVariable.%s}", REPO_NAME_SERVICE_VARIABLE))
                               .build())
            .build();
    applicationManifest.setAppId(service.getAppId());
    return applicationManifest;
  }

  private void updateApplicationManifest(ApplicationManifest applicationManifest) {
    final ApplicationManifest manifestByServiceId = applicationManifestService.getManifestByServiceId(
        applicationManifest.getAppId(), applicationManifest.getServiceId());
    if (manifestByServiceId != null) {
      applicationManifest.setUuid(manifestByServiceId.getUuid());
      applicationManifestService.update(applicationManifest);
    } else {
      applicationManifestService.create(applicationManifest);
    }
  }

  private Workflow createBasicWorkflow(String name, Service service, InfrastructureDefinition infraDefinition) {
    Workflow workflow = aWorkflow()
                            .name(name)
                            .appId(service.getAppId())
                            .envId(infraDefinition.getEnvId())
                            .infraDefinitionId(infraDefinition.getUuid())
                            .serviceId(service.getUuid())
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .orchestrationWorkflow(aBasicOrchestrationWorkflow().build())
                            .build();
    return workflow;
  }

  private ExecutionArgs getExecutionArgs(Workflow workflow, Environment environment, Service service) {
    String artifactId = ArtifactStreamRestUtils.getArtifactStreamId(
        bearerToken, application.getUuid(), environment.getUuid(), service.getUuid());
    Artifact artifact = new Artifact();
    artifact.setUuid(artifactId);

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(workflow.getWorkflowType());
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setArtifacts(Collections.singletonList(artifact));

    return executionArgs;
  }

  private void setupPcfWorkflow(Workflow workflow) {
    BasicOrchestrationWorkflow orchestrationWorkflow = (BasicOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    GraphNode pcfAppResize = workflowPhase.getPhaseSteps().get(1).getSteps().get(0);
    Map<String, Object> properties = pcfAppResize.getProperties();
    properties.put("instanceCount", "1");
    GraphNode cleanupOrphanedRoutes =
        GraphNode.builder()
            .name("Cleanup Orphaned Routes")
            .type("PCF_PLUGIN")
            .properties(ImmutableMap.of("scriptString", "cf delete-orphaned-routes -f", "timeoutIntervalInMinutes", 3))
            .build();
    if (workflowPhase.getPhaseSteps().get(0).getSteps().size() > 1) {
      workflowPhase.getPhaseSteps().get(0).getSteps().set(0, cleanupOrphanedRoutes);
    } else {
      workflowPhase.getPhaseSteps().get(0).getSteps().add(0, cleanupOrphanedRoutes);
    }

    workflowService.updateWorkflow(workflow, false);
  }

  private String getName(String resource, String type) {
    return format(RESOURCE_NAME_FORMAT, resource, type);
  }
}
