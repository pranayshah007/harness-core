/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.WorkflowType;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.k8s.model.HelmVersion;

import software.wings.api.DeploymentType;
import software.wings.beans.BasicOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.helm.HelmChartConfig;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.WorkflowService;
import software.wings.utils.ArtifactType;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class HelmHelper {
  private static final int RELEASE_NAME_LENGTH_WITHOUT_SHORT_ID = 46;
  private static final String RELEASE_NAME_FORMAT = "%s-${infra.helm.shortId}";
  private static final String HELM_RELEASE_NAME_PREFIX = "helmReleaseNamePrefix";
  private static final String CREATE_NAMESPACE_STEP_NAME = "Create Namespace";
  private static final String CREATE_NAMESPACE_SCRIPT = "export KUBECONFIG=${HARNESS_KUBE_CONFIG_PATH}\n"
      + "kubectl get namespace ${infra.kubernetes.namespace} || kubectl create namespace ${infra.kubernetes.namespace}";
  private static final String CLEANUP_STEP_NAME = "Cleanup release";
  private static final String CLEANUP_SCRIPT = "export KUBECONFIG=${HARNESS_KUBE_CONFIG_PATH}\n"
      + "${HELM_CLI} ${PURGE_ACTION} ${RELEASE_NAME} ${OPTS}";
  private static final String HELM3_CLIENT_TOOLS_PATH = "client-tools/helm/v3.1.2/helm";
  private static final String CLEANUP_WORKFLOW_PREFIX = "Cleanup ";
  private static final String JENKINS_WORKSPACE = "cd-deployment-functional-tests";

  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowService workflowService;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private ApplicationManifestService applicationManifestService;

  public Workflow createHelmWorkflow(Seed seed, Owners owners, String name, String releaseName, Service service,
      InfrastructureDefinition infraDefinition) {
    Workflow workflow = workflowGenerator.ensureWorkflow(seed, owners,
        aWorkflow()
            .name(name)
            .appId(service.getAppId())
            .envId(infraDefinition.getEnvId())
            .infraDefinitionId(infraDefinition.getUuid())
            .serviceId(service.getUuid())
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(
                aBasicOrchestrationWorkflow()
                    // these variables can be used for validation of expression rendering and can be provided
                    // with ExecutionArgs and shouldn't be mandatory
                    .withUserVariables(Arrays.asList(aVariable().name("serviceName").mandatory(false).build()))
                    .build())
            .build());

    return setupWorkflow(workflow, releaseName, service.getHelmVersion());
  }

  public Workflow setupWorkflow(Workflow workflow, String releaseName, HelmVersion helmVersion) {
    BasicOrchestrationWorkflow orchestrationWorkflow = (BasicOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    GraphNode helmDeployStep = workflowPhase.getPhaseSteps().get(0).getSteps().get(0);
    Map<String, Object> properties = helmDeployStep.getProperties();
    properties.put(HELM_RELEASE_NAME_PREFIX, releaseName);
    helmDeployStep.setProperties(properties);

    if (HelmVersion.isHelmV3(helmVersion)) {
      // Starting with v3, helm doesn't manage anymore namespace. This script will create namespace if it's missing
      // (create-namespace alternative is available starting with 3.2)
      GraphNode createNamespaceStep = createShellScriptNode(CREATE_NAMESPACE_STEP_NAME, CREATE_NAMESPACE_SCRIPT);
      if (workflowPhase.getPhaseSteps().get(0).getSteps().size() > 1) {
        workflowPhase.getPhaseSteps().get(0).getSteps().set(0, createNamespaceStep);
      } else {
        workflowPhase.getPhaseSteps().get(0).getSteps().add(0, createNamespaceStep);
      }
    }

    return workflowService.updateWorkflow(workflow, false);
  }

  public Service createHelmService(Seed seed, Owners owners, String serviceName, HelmVersion version,
      HelmChartConfig helmChartConfig, Boolean pollForChanges) {
    Service service = Service.builder()
                          .name(serviceName)
                          .deploymentType(DeploymentType.HELM)
                          .appId(owners.obtainApplication().getAppId())
                          .artifactType(ArtifactType.DOCKER)
                          .helmVersion(version)
                          .build();

    service = serviceGenerator.ensureService(seed, owners, service);
    applyHelmChartConfigToService(service, helmChartConfig, pollForChanges);
    return service;
  }

  public void applyHelmChartConfigToService(Service service, HelmChartConfig helmChartConfig, Boolean pollForChanges) {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .serviceId(service.getUuid())
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .helmChartConfig(helmChartConfig)
                                                  .kind(AppManifestKind.K8S_MANIFEST)
                                                  .pollForChanges(pollForChanges)
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
            && (savedApplicationManifest.getStoreType() == StoreType.HelmChartRepo
                || savedApplicationManifest.getStoreType() == StoreType.Local)) {
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

  public Workflow createCleanupWorkflow(Seed seed, Owners owners, String name, String releaseName, Service service,
      InfrastructureDefinition infraDefinition) {
    Workflow workflow = workflowGenerator.ensureWorkflow(seed, owners,
        aWorkflow()
            .name(CLEANUP_WORKFLOW_PREFIX + name)
            .appId(service.getAppId())
            .envId(infraDefinition.getEnvId())
            .infraDefinitionId(infraDefinition.getUuid())
            .serviceId(service.getUuid())
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(aBasicOrchestrationWorkflow().build())
            .build());

    String cleanupScript = getCleanupScript(service.getHelmVersion(), releaseName);
    GraphNode cleanupStep = createShellScriptNode(CLEANUP_STEP_NAME, cleanupScript);

    BasicOrchestrationWorkflow orchestrationWorkflow = (BasicOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    workflowPhase.getPhaseSteps().get(0).setSteps(singletonList(cleanupStep));

    return workflowService.updateWorkflow(workflow, false);
  }

  public String getReleaseName(String baseName) {
    if (baseName.length() > RELEASE_NAME_LENGTH_WITHOUT_SHORT_ID) {
      baseName = baseName.substring(0, RELEASE_NAME_LENGTH_WITHOUT_SHORT_ID);
    }

    baseName = baseName.toLowerCase().replace(" ", "-").replaceFirst("-$", "");

    return format(RELEASE_NAME_FORMAT, baseName);
  }

  public GraphNode createShellScriptNode(String name, String script) {
    return GraphNode.builder()
        .name(name)
        .type("SHELL_SCRIPT")
        .properties(ImmutableMap.of(
            "scriptType", "BASH", "scriptString", script, "executeOnDelegate", true, "timeoutMillis", 60000))
        .build();
  }

  public String getCleanupScript(HelmVersion helmVersion, String releaseName) {
    String helmCliPath = "helm";
    String helmPurgeAction = "delete";
    String opts = "--purge";
    if (HelmVersion.isHelmV3(helmVersion)) {
      helmCliPath = getHelm3ClientToolsPath();
      helmPurgeAction = "uninstall";
      opts = "--namespace ${infra.kubernetes.namespace}";
    }

    return CLEANUP_SCRIPT.replace("${HELM_CLI}", helmCliPath)
        .replace("${PURGE_ACTION}", helmPurgeAction)
        .replace("${RELEASE_NAME}", releaseName)
        .replace("${OPTS}", opts);
  }

  private String getHelm3ClientToolsPath() {
    String home = System.getProperty("user.home");
    if (home.contains("root")) {
      home = "/home/jenkins";
    }

    Path path = Paths.get(home, "/.bazel-dirs/bin/260-delegate/" + HELM3_CLIENT_TOOLS_PATH);
    File delegateModuleLocation = new File(path.toString());
    if (delegateModuleLocation.exists()) {
      return path.toString();
    }

    throw new IllegalStateException("Unable to get Helm v3 client tools path");
  }
}
