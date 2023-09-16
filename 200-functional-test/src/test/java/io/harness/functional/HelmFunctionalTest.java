/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;

import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.WorkflowType;
import io.harness.category.element.FunctionalTests;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.SettingGenerator;
import io.harness.k8s.model.HelmVersion;
import io.harness.rule.Owner;
import io.harness.testframework.restutils.WorkflowRestUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.BasicOrchestrationWorkflow;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphNode;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.helm.HelmChartConfig;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowService;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class HelmFunctionalTest extends AbstractFunctionalTest {
  private static final String WORKFLOW_NAME = "Helm S3 Functional Test";
  private static final String RELEASE_NAME = "functional-test";
  private static final String HELM_RELEASE_NAME_PREFIX = "helmReleaseNamePrefix";
  private static final String HELM_S3_SERVICE_NAME = "Helm S3 Functional Test";
  private static final String CHART_NAME = "aks-helloworld";
  private static final String BASE_PATH = "helm/charts";

  @Inject private OwnerManager ownerManager;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private SettingGenerator settingGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private WorkflowUtils workflowUtils;
  @Inject private WorkflowService workflowService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ApplicationManifestService applicationManifestService;

  private Owners owners;
  private InfrastructureDefinition infrastructureDefinition;
  private Workflow workflow;

  private final Seed seed = new Seed(0);

  @Before
  public void setUp() throws Exception {
    owners = ownerManager.create();
    infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.AZURE_HELM);
    log.info("Ensured Infra def");
    resetCache(owners.obtainAccount().getUuid());
  }

  @Test
  @Owner(developers = {VAIBHAV_SI, YOGESH, ANSHUL})
  @Category(FunctionalTests.class)
  @Ignore("Working locally, need to install helm on Jenkins box")
  public void testHelmS3WorkflowExecution() {
    Service helmS3Service = createHelm3S3Service();
    log.info("Created Service");
    addValuesYamlToService(helmS3Service);
    log.info("Added values.yaml to service");
    workflow = ensureWorkflow(helmS3Service, infrastructureDefinition);
    log.info("Ensured workflow");
    workflow = updateReleaseNameInWorkflow(workflow);
    log.info("Updated release name in workflow");

    resetCache(owners.obtainAccount().getUuid());
    ExecutionArgs executionArgs = getExecutionArgs();

    WorkflowExecution workflowExecution = WorkflowRestUtils.startWorkflow(
        bearerToken, helmS3Service.getAppId(), infrastructureDefinition.getEnvId(), executionArgs);
    log.info("Started workflow execution");
    resetCache(owners.obtainAccount().getUuid());

    workflowUtils.checkForWorkflowSuccess(workflowExecution);
  }

  private Service createHelm3S3Service() {
    Service service = Service.builder()
                          .name(HELM_S3_SERVICE_NAME)
                          .deploymentType(DeploymentType.HELM)
                          .appId(infrastructureDefinition.getAppId())
                          .artifactType(ArtifactType.DOCKER)
                          .helmVersion(HelmVersion.V3)
                          .build();

    Service savedService = serviceResourceService.getServiceByName(service.getAppId(), service.getName());
    if (savedService == null) {
      savedService = serviceResourceService.save(service);
    } else {
      service.setUuid(savedService.getUuid());
      savedService = serviceResourceService.update(service);
    }
    owners.add(savedService);

    addApplicationManifestToService(seed, owners, savedService);
    return service;
  }

  private void addApplicationManifestToService(Seed seed, Owners owners, Service service) {
    SettingAttribute helmS3Connector =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.HELM_S3_CONNECTOR);

    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .serviceId(service.getUuid())
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .helmChartConfig(HelmChartConfig.builder()
                                                                       .connectorId(helmS3Connector.getUuid())
                                                                       .chartName(CHART_NAME)
                                                                       .basePath(BASE_PATH)
                                                                       .build())
                                                  .kind(AppManifestKind.K8S_MANIFEST)
                                                  .build();
    applicationManifest.setAppId(service.getAppId());

    List<ApplicationManifest> applicationManifests =
        applicationManifestService.listAppManifests(service.getAppId(), service.getUuid());

    if (isEmpty(applicationManifests)) {
      applicationManifestService.create(applicationManifest);
    } else {
      boolean found = false;
      for (ApplicationManifest savedApplicationManifest : applicationManifests) {
        if (savedApplicationManifest.getKind() == AppManifestKind.K8S_MANIFEST
            && savedApplicationManifest.getStoreType() == StoreType.HelmChartRepo) {
          applicationManifest.setUuid(savedApplicationManifest.getUuid());
          applicationManifestService.update(applicationManifest);
          found = true;
          break;
        }
      }
      if (!found) {
        applicationManifestService.create(applicationManifest);
      }
    }
  }

  private void addValuesYamlToService(Service helmS3Service) {
    ApplicationManifest existingAppManifest = applicationManifestService.getAppManifest(
        helmS3Service.getAppId(), null, helmS3Service.getUuid(), AppManifestKind.VALUES);
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .kind(AppManifestKind.VALUES)
                                                  .serviceId(helmS3Service.getUuid())
                                                  .storeType(StoreType.Local)
                                                  .build();
    applicationManifest.setAppId(helmS3Service.getAppId());
    if (existingAppManifest == null) {
      applicationManifest = applicationManifestService.create(applicationManifest);
    } else {
      applicationManifest.setUuid(existingAppManifest.getUuid());
      applicationManifest = applicationManifestService.update(applicationManifest);
    }
    ManifestFile manifestFile = ManifestFile.builder()
                                    .fileContent("serviceName: functional-test")
                                    .applicationManifestId(applicationManifest.getUuid())
                                    .fileName("values.yaml")
                                    .build();
    manifestFile.setAppId(helmS3Service.getAppId());
    applicationManifestService.deleteAllManifestFilesByAppManifestId(
        applicationManifest.getAppId(), applicationManifest.getUuid());
    applicationManifestService.upsertApplicationManifestFile(manifestFile, applicationManifest, true);
  }

  private Workflow ensureWorkflow(Service helmS3Service, InfrastructureDefinition infrastructureDefinition) {
    Workflow workflow =
        aWorkflow()
            .name(WORKFLOW_NAME)
            .appId(helmS3Service.getAppId())
            .envId(infrastructureDefinition.getEnvId())
            .serviceId(helmS3Service.getUuid())
            .infraDefinitionId(infrastructureDefinition.getUuid())
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow().build())
            .build();

    Workflow savedWorkflow = workflowService.readWorkflowByName(workflow.getAppId(), workflow.getName());
    if (savedWorkflow != null) {
      workflowService.deleteWorkflow(savedWorkflow.getAppId(), savedWorkflow.getUuid());
    }
    savedWorkflow = workflowService.createWorkflow(workflow);
    return savedWorkflow;
  }

  @NotNull
  private ExecutionArgs getExecutionArgs() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setWorkflowType(workflow.getWorkflowType());
    return executionArgs;
  }

  private Workflow updateReleaseNameInWorkflow(Workflow workflow) {
    BasicOrchestrationWorkflow orchestrationWorkflow = (BasicOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    GraphNode helmDeployStep = workflowPhase.getPhaseSteps().get(0).getSteps().get(0);
    Map<String, Object> properties = helmDeployStep.getProperties();
    properties.put(HELM_RELEASE_NAME_PREFIX, RELEASE_NAME);
    helmDeployStep.setProperties(properties);

    return workflowService.updateWorkflow(workflow, false);
  }

  private Workflow createWorkflow(Service helmS3Service, InfrastructureDefinition infrastructureDefinition) {
    Workflow workflow = aWorkflow()
                            .name(WORKFLOW_NAME)
                            .appId(helmS3Service.getAppId())
                            .envId(infrastructureDefinition.getEnvId())
                            .serviceId(helmS3Service.getUuid())
                            .infraDefinitionId(infrastructureDefinition.getUuid())
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().build())
                            .build();
    workflow.getOrchestrationWorkflow().setOrchestrationWorkflowType(OrchestrationWorkflowType.BASIC);
    return WorkflowRestUtils.createWorkflow(
        bearerToken, owners.obtainAccount().getUuid(), helmS3Service.getAppId(), workflow);
  }
}
