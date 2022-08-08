/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.KUBERNETES_EXPORT_MANIFESTS;
import static io.harness.beans.FeatureName.PRUNE_KUBERNETES_RESOURCES;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.ListUtils.trimStrings;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;
import static software.wings.sm.states.k8s.K8sResourcesSweepingOutput.K8S_RESOURCES_SWEEPING_OUTPUT;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.K8sPodSyncException;
import io.harness.exception.UnexpectedException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesResource;
import io.harness.logging.CommandExecutionStatus;
import io.harness.serializer.KryoSerializer;

import software.wings.api.k8s.K8sElement;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.RancherKubernetesInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.K8sDummyCommandUnit;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sInstanceSyncTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sInstanceSyncResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.settings.SettingValue;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class K8sStateHelper {
  @Inject private transient ApplicationManifestService applicationManifestService;

  @Inject private transient DelegateService delegateService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private transient AwsCommandHelper awsCommandHelper;
  @Inject private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private EnvironmentService environmentService;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  public static List<String> fetchDelegateSelectorsFromK8sCloudProvider(SettingValue settingValue) {
    if (!(settingValue instanceof KubernetesClusterConfig)) {
      return emptyList();
    }
    KubernetesClusterConfig config = (KubernetesClusterConfig) settingValue;
    if (config.isUseKubernetesDelegate() && !config.getDelegateSelectors().isEmpty()) {
      return new ArrayList<>(config.getDelegateSelectors());
    }

    return emptyList();
  }

  public static List<String> fetchDelegateNameAsTagFromK8sCloudProvider(SettingValue settingValue) {
    if (!(settingValue instanceof KubernetesClusterConfig)) {
      return emptyList();
    }

    KubernetesClusterConfig config = (KubernetesClusterConfig) settingValue;
    if (config.isUseKubernetesDelegate() && isNotBlank(config.getDelegateName())) {
      return Arrays.asList(config.getDelegateName());
    }

    return emptyList();
  }

  public static List<String> fetchTagsFromK8sCloudProvider(ContainerServiceParams containerServiceParams) {
    if (containerServiceParams == null || containerServiceParams.getSettingAttribute() == null) {
      return emptyList();
    }
    SettingAttribute settingAttribute = containerServiceParams.getSettingAttribute();
    return fetchDelegateNameAsTagFromK8sCloudProvider(settingAttribute.getValue());
  }

  public static List<String> fetchTagsFromK8sTaskParams(K8sTaskParameters request) {
    if (request == null || request.getK8sClusterConfig() == null) {
      return emptyList();
    }

    return fetchDelegateNameAsTagFromK8sCloudProvider(request.getK8sClusterConfig().getCloudProvider());
  }

  static boolean isValueToFindPresent(String fileContent, String valueToFind) {
    if (isBlank(fileContent)) {
      return false;
    }

    try (LineIterator lineIterator = new LineIterator(new StringReader(fileContent))) {
      while (lineIterator.hasNext()) {
        String line = lineIterator.nextLine();
        if (isBlank(line) || line.trim().charAt(0) == '#') {
          continue;
        }
        if (line.contains(valueToFind)) {
          return true;
        }
      }
    } catch (IOException exception) {
      return false;
    }

    return false;
  }

  public Environment fetchEnvFromExecutionContext(ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    if (workflowStandardParams == null) {
      return null;
    }

    return this.workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
  }

  public static long fetchSafeTimeoutInMillis(Integer timeoutInMillis) {
    return timeoutInMillis != null && timeoutInMillis > 0 ? (long) timeoutInMillis
                                                          : Duration.ofMinutes(DEFAULT_STEADY_STATE_TIMEOUT).toMillis();
  }

  boolean doesValuesFileContainArtifact(ApplicationManifest applicationManifest) {
    if (applicationManifest != null && StoreType.Local == applicationManifest.getStoreType()) {
      ManifestFile manifestFile =
          applicationManifestService.getManifestFileByFileName(applicationManifest.getUuid(), values_filename);
      if (manifestFile != null) {
        return isValueToFindPresent(manifestFile.getFileContent(), "${artifact.");
      }
    }
    return false;
  }

  boolean doManifestsUseArtifactInternal(String appId, String serviceId, String envId) {
    ApplicationManifest applicationManifest = applicationManifestService.getManifestByServiceId(appId, serviceId);
    if (doesValuesFileContainArtifact(applicationManifest)) {
      return true;
    }

    applicationManifest = applicationManifestService.getAppManifest(appId, null, serviceId, AppManifestKind.VALUES);
    if (doesValuesFileContainArtifact(applicationManifest)) {
      return true;
    }

    applicationManifest = applicationManifestService.getByEnvId(appId, envId, AppManifestKind.VALUES);
    if (doesValuesFileContainArtifact(applicationManifest)) {
      return true;
    }

    applicationManifest =
        applicationManifestService.getByEnvAndServiceId(appId, envId, serviceId, AppManifestKind.VALUES);

    return doesValuesFileContainArtifact(applicationManifest);
  }

  public boolean doManifestsUseArtifact(String appId, String infraMappingId) {
    InfrastructureMapping infraMapping = infrastructureMappingService.get(appId, infraMappingId);
    if (infraMapping == null) {
      throw new InvalidRequestException(
          format("Infra mapping not found for appId %s infraMappingId %s", appId, infraMappingId));
    }

    return doManifestsUseArtifactInternal(appId, infraMapping.getServiceId(), infraMapping.getEnvId());
  }

  public boolean doManifestsUseArtifact(String appId, String serviceId, String infraDefinitionId) {
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.get(appId, infraDefinitionId);
    if (infrastructureDefinition == null) {
      throw new InvalidRequestException(
          format("Infra definition not found for appId %s infraDefinitionId %s", appId, infraDefinitionId));
    }

    return doManifestsUseArtifactInternal(appId, serviceId, infrastructureDefinition.getEnvId());
  }

  void updateManifestFileVariableNames(
      ApplicationManifest applicationManifest, Set<String> serviceArtifactVariableNames) {
    if (applicationManifest != null && StoreType.Local == applicationManifest.getStoreType()) {
      ManifestFile manifestFile =
          applicationManifestService.getManifestFileByFileName(applicationManifest.getUuid(), values_filename);
      if (manifestFile != null) {
        String content = manifestFile.getFileContent();
        ExpressionEvaluator.updateServiceArtifactVariableNames(content, serviceArtifactVariableNames);
      }
    }
  }

  public void updateManifestsArtifactVariableNames(
      String appId, Set<String> serviceArtifactVariableNames, String serviceId, String envId) {
    ApplicationManifest applicationManifest = applicationManifestService.getManifestByServiceId(appId, serviceId);
    updateManifestFileVariableNames(applicationManifest, serviceArtifactVariableNames);

    applicationManifest = applicationManifestService.getByEnvId(appId, envId, AppManifestKind.VALUES);
    updateManifestFileVariableNames(applicationManifest, serviceArtifactVariableNames);

    applicationManifest =
        applicationManifestService.getByEnvAndServiceId(appId, envId, serviceId, AppManifestKind.VALUES);
    updateManifestFileVariableNames(applicationManifest, serviceArtifactVariableNames);
  }

  public void updateManifestsArtifactVariableNamesInfraDefinition(
      String appId, String infraDefinitionId, Set<String> serviceArtifactVariableNames, String serviceId) {
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.get(appId, infraDefinitionId);
    if (infrastructureDefinition == null) {
      throw new InvalidRequestException(
          format("Infra Definition not found for appId %s infraDefinitionId %s", appId, infraDefinitionId));
    }

    updateManifestsArtifactVariableNames(
        appId, serviceArtifactVariableNames, serviceId, infrastructureDefinition.getEnvId());
  }

  public List<K8sPod> fetchPodListForCluster(ContainerInfrastructureMapping containerInfrastructureMapping,
      String namespace, String releaseName, String clusterName) throws K8sPodSyncException, InterruptedException {
    K8sInstanceSyncTaskParameters k8sInstanceSyncTaskParameters =
        K8sInstanceSyncTaskParameters.builder()
            .accountId(containerInfrastructureMapping.getAccountId())
            .appId(containerInfrastructureMapping.getAppId())
            .k8sClusterConfig(
                containerDeploymentManagerHelper.getK8sClusterConfig(containerInfrastructureMapping, null))
            .namespace(namespace)
            .releaseName(releaseName)
            .build();

    if (StringUtils.isNotBlank(clusterName)) {
      k8sInstanceSyncTaskParameters.getK8sClusterConfig().setClusterName(clusterName);
    }

    return fetchPodListInternal(containerInfrastructureMapping, releaseName, k8sInstanceSyncTaskParameters);
  }

  public List<K8sPod> fetchPodList(ContainerInfrastructureMapping containerInfrastructureMapping, String namespace,
      String releaseName) throws K8sPodSyncException, InterruptedException {
    K8sInstanceSyncTaskParameters k8sInstanceSyncTaskParameters =
        K8sInstanceSyncTaskParameters.builder()
            .accountId(containerInfrastructureMapping.getAccountId())
            .appId(containerInfrastructureMapping.getAppId())
            .k8sClusterConfig(
                containerDeploymentManagerHelper.getK8sClusterConfig(containerInfrastructureMapping, null))
            .namespace(namespace)
            .releaseName(releaseName)
            .build();

    return fetchPodListInternal(containerInfrastructureMapping, releaseName, k8sInstanceSyncTaskParameters);
  }

  private List<K8sPod> fetchPodListInternal(ContainerInfrastructureMapping containerInfrastructureMapping,
      String releaseName, K8sTaskParameters k8sInstanceSyncTaskParameters)
      throws K8sPodSyncException, InterruptedException {
    List tags = new ArrayList();
    tags.addAll(awsCommandHelper.getAwsConfigTagsFromK8sConfig(k8sInstanceSyncTaskParameters));

    String waitId = generateUuid();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(containerInfrastructureMapping.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, containerInfrastructureMapping.getAppId())
            .waitId(waitId)
            .tags(tags)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.K8S_COMMAND_TASK.name())
                      .parameters(new Object[] {k8sInstanceSyncTaskParameters})
                      .timeout(Duration.ofMinutes(2).toMillis())
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, containerInfrastructureMapping.getEnvId())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD,
                environmentService
                    .get(containerInfrastructureMapping.getAppId(), containerInfrastructureMapping.getEnvId())
                    .getEnvironmentType()
                    .name())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, containerInfrastructureMapping.getUuid())
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, containerInfrastructureMapping.getServiceId())
            .build();

    DelegateResponseData notifyResponseData = delegateService.executeTask(delegateTask);
    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      throw new K8sPodSyncException(format("Failed to fetch PodList for release %s. Error: %s", releaseName,
          ((ErrorNotifyResponseData) notifyResponseData).getErrorMessage()));
    } else if (notifyResponseData instanceof RemoteMethodReturnValueData
        && ((RemoteMethodReturnValueData) notifyResponseData).getException() != null) {
      throw new K8sPodSyncException(
          format("Failed to fetch PodList for release %s. Exception: %s", releaseName, notifyResponseData));
    } else if (!(notifyResponseData instanceof K8sTaskExecutionResponse)) {
      throw new UnexpectedException(format("Failed to fetch PodList for release %s. Unknown return type %s",
          releaseName, notifyResponseData.getClass().getName()));
    }

    K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) notifyResponseData;
    if (k8sTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      K8sInstanceSyncResponse k8sInstanceSyncResponse =
          (K8sInstanceSyncResponse) k8sTaskExecutionResponse.getK8sTaskResponse();
      return k8sInstanceSyncResponse.getK8sPodInfoList();
    }

    throw new K8sPodSyncException(format("Failed to fetch PodList for release %s. Msg: %s. Status: %s", releaseName,
        k8sTaskExecutionResponse.getErrorMessage(), k8sTaskExecutionResponse.getCommandExecutionStatus()));
  }

  public ContainerInfrastructureMapping fetchContainerInfrastructureMapping(ExecutionContext context) {
    return (ContainerInfrastructureMapping) infrastructureMappingService.get(
        context.getAppId(), context.fetchInfraMappingId());
  }

  public boolean isRancherInfraMapping(ExecutionContext context) {
    return fetchContainerInfrastructureMapping(context) instanceof RancherKubernetesInfrastructureMapping;
  }

  public ExecutionResponse getInvalidInfraDefFailedResponse() {
    return ExecutionResponse.builder()
        .executionStatus(ExecutionStatus.FAILED)
        .stateExecutionData(
            aStateExecutionData()
                .withErrorMsg("Step type unsupported with Rancher cloud provider based Infrastructure Definition")
                .build())
        .build();
  }

  public String fetchContainerInfrastructureMappingId(ExecutionContext context) {
    ContainerInfrastructureMapping infrastructureMapping = fetchContainerInfrastructureMapping(context);
    if (infrastructureMapping == null) {
      return null;
    }

    return infrastructureMapping.getUuid();
  }

  public K8sElement fetchK8sElement(ExecutionContext context) {
    SweepingOutputInquiry sweepingOutputInquiry = context.prepareSweepingOutputInquiryBuilder().name("k8s").build();
    SweepingOutputInstance result = sweepingOutputService.find(sweepingOutputInquiry);
    if (result == null) {
      return null;
    }
    return (K8sElement) kryoSerializer.asInflatedObject(result.getOutput());
  }

  public Set<String> getRenderedAndTrimmedSelectors(ExecutionContext context, List<String> delegateSelectors) {
    if (isEmpty(delegateSelectors)) {
      return emptySet();
    }
    List<String> renderedSelectors = delegateSelectors.stream().map(context::renderExpression).collect(toList());
    List<String> trimmedSelectors = trimStrings(renderedSelectors);
    return new HashSet<>(trimmedSelectors);
  }

  @NotNull
  public List<CommandUnit> getCommandUnits(boolean remoteStoreType, String accountId, boolean inheritManifests,
      boolean exportManifests, boolean isPruneSupported) {
    List<CommandUnit> commandUnits = new ArrayList<>();

    if (!(isExportManifestsEnabled(accountId) && inheritManifests)) {
      if (remoteStoreType) {
        commandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.FetchFiles));
      }
    }
    commandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.Init));

    if (!(isExportManifestsEnabled(accountId) && exportManifests)) {
      commandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.Prepare));
      commandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.Apply));
      commandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.WaitForSteadyState));
      commandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.WrapUp));
      if (isPruneSupported && featureFlagService.isEnabled(PRUNE_KUBERNETES_RESOURCES, accountId)) {
        commandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.Prune));
      }
    }
    return commandUnits;
  }

  public boolean isExportManifestsEnabled(String accountId) {
    return featureFlagService.isEnabled(KUBERNETES_EXPORT_MANIFESTS, accountId);
  }

  public void saveResourcesToSweepingOutput(
      ExecutionContext context, List<KubernetesResource> resources, String stateType) {
    SweepingOutputInstance sweepingOutputInstance = getK8sResourcesSweepingOutputInstance(context);
    if (sweepingOutputInstance != null) {
      sweepingOutputService.deleteById(context.getAppId(), sweepingOutputInstance.getUuid());
    }

    sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.PHASE)
                                   .name(K8S_RESOURCES_SWEEPING_OUTPUT)
                                   .value(K8sResourcesSweepingOutput.builder()
                                              .resources(resources)
                                              .manifests(ManifestHelper.toYaml(resources))
                                              .stateType(stateType)
                                              .build())
                                   .build());
  }

  public List<KubernetesResource> getResourcesFromSweepingOutput(ExecutionContext context, String stateType) {
    SweepingOutputInstance sweepingOutputInstance = getK8sResourcesSweepingOutputInstance(context);
    K8sResourcesSweepingOutput k8sResourcesSweepingOutput = null;
    if (sweepingOutputInstance != null) {
      k8sResourcesSweepingOutput = (K8sResourcesSweepingOutput) sweepingOutputInstance.getValue();
    }

    return (k8sResourcesSweepingOutput != null && k8sResourcesSweepingOutput.getStateType().equals(stateType))
        ? k8sResourcesSweepingOutput.getResources()
        : null;
  }

  private SweepingOutputInstance getK8sResourcesSweepingOutputInstance(ExecutionContext context) {
    return sweepingOutputService.find(
        context.prepareSweepingOutputInquiryBuilder().name(K8S_RESOURCES_SWEEPING_OUTPUT).build());
  }
}
