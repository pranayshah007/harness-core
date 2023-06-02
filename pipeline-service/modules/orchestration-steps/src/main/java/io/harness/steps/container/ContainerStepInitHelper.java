/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container;

import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_SERVICE_LOG_KEY_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_PREFIX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.steps.container.constants.ContainerStepExecutionConstants.CLEANUP_DETAILS;
import static io.harness.steps.container.execution.ContainerDetailsSweepingOutput.INIT_POD;
import static io.harness.steps.plugin.infrastructure.ContainerStepInfra.Type.KUBERNETES_DIRECT;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.buildstate.StepContainerUtils;
import io.harness.ci.remote.CiServiceResourceClient;
import io.harness.ci.utils.ContainerSecretEvaluator;
import io.harness.ci.utils.PortFinder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.task.citasks.cik8handler.params.CIConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.ImageDetails;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.ExpressionResolverUtils;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerDetailsSweepingOutput;
import io.harness.steps.container.execution.plugin.PluginExecutionConfigHelper;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.container.utils.ContainerInfraMapper;
import io.harness.steps.container.utils.ContainerParamsProvider;
import io.harness.steps.container.utils.ContainerStepImageUtils;
import io.harness.steps.container.utils.ContainerStepResolverUtils;
import io.harness.steps.container.utils.ContainerStepV2DefinitionCreator;
import io.harness.steps.container.utils.K8sPodInitUtils;
import io.harness.steps.container.utils.PluginUtils;
import io.harness.steps.plugin.ContainerStepInfo;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.PluginStep;
import io.harness.steps.plugin.infrastructure.ContainerCleanupDetails;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.RetryUtils;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.tuple.Pair;
import retrofit2.Response;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepInitHelper {
  private static final String FAILED_TO_FETCH_CI_EXECUTION_CONFIG_MSG =
      "Failed to fetch execution configuration for container creation";
  private static final RetryPolicy<Object> RETRY_POLICY_EXECUTION_CONFIGS = RetryUtils.getRetryPolicy(
      "Error calling CI Manager for fetching execution configs..retrying", FAILED_TO_FETCH_CI_EXECUTION_CONFIG_MSG,
      Collections.singletonList(IOException.class), Duration.ofMillis(10), 3, log);
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private ContainerStepImageUtils harnessImageUtils;
  @Inject private ContainerParamsProvider containerParamsProvider;
  @Inject K8sPodInitUtils k8sPodInitUtils;
  @Inject SecretUtils secretUtils;
  @Inject PluginExecutionConfigHelper pluginExecutionConfigHelper;
  @Inject PluginUtils pluginUtils;
  @Inject CiServiceResourceClient ciServiceResourceClient;

  public CIK8InitializeTaskParams getK8InitializeTaskParams(
      ContainerStepSpec containerStepInfo, Ambiance ambiance, String logPrefix) {
    return getK8InitializeTaskParams(containerStepInfo, ambiance, logPrefix, containerStepInfo.getIdentifier());
  }

  private CIK8InitializeTaskParams buildK8DirectTaskParams(ContainerStepSpec containerStepInfo,
      ContainerDetailsSweepingOutput k8PodDetails, ContainerK8sInfra infrastructure, Ambiance ambiance,
      String logPrefix) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String connectorRef = infrastructure.getSpec().getConnectorRef().getValue();

    ConnectorDetails k8sConnector = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
    return CIK8InitializeTaskParams.builder()
        .k8sConnector(k8sConnector)
        .cik8PodParams(getK8DirectPodParams(containerStepInfo, k8PodDetails, infrastructure, ambiance, logPrefix))
        .podMaxWaitUntilReadySecs(k8sPodInitUtils.getPodWaitUntilReadTimeout(infrastructure))
        .build();
  }

  private CIK8PodParams<CIK8ContainerParams> getK8DirectPodParams(ContainerStepSpec containerStepInfo,
      ContainerDetailsSweepingOutput k8PodDetails, ContainerK8sInfra k8sDirectInfraYaml, Ambiance ambiance,
      String logPrefix) {
    String podName = getPodName(containerStepInfo.getIdentifier().toLowerCase());
    Map<String, String> buildLabels = k8sPodInitUtils.getLabels(
        ambiance, ContainerUnitStepUtils.getKubernetesStandardPodName(containerStepInfo.getIdentifier()));
    Map<String, String> annotations = ExpressionResolverUtils.resolveMapParameter(
        "annotations", "ContainerStep", "stepSetup", k8sDirectInfraYaml.getSpec().getAnnotations(), false);
    Map<String, String> labels = ExpressionResolverUtils.resolveMapParameter(
        "labels", "ContainerStep", "stepSetup", k8sDirectInfraYaml.getSpec().getLabels(), false);
    Map<String, String> nodeSelector = ExpressionResolverUtils.resolveMapParameter(
        "nodeSelector", "ContainerStep", "stepSetup", k8sDirectInfraYaml.getSpec().getNodeSelector(), false);
    Integer stepAsUser =
        ExpressionResolverUtils.resolveIntegerParameter(k8sDirectInfraYaml.getSpec().getRunAsUser(), null);
    String serviceAccountName = ExpressionResolverUtils.resolveStringParameter("serviceAccountName", "ContainerStep",
        "stageSetup", k8sDirectInfraYaml.getSpec().getServiceAccountName(), false);

    if (isNotEmpty(labels)) {
      buildLabels.putAll(labels);
    }

    List<PodVolume> volumes = k8sPodInitUtils.convertDirectK8Volumes(k8sDirectInfraYaml);
    Pair<CIK8ContainerParams, List<CIK8ContainerParams>> podContainers =
        getStepContainers(containerStepInfo, k8PodDetails, k8sDirectInfraYaml, ambiance, volumes, logPrefix);
    saveSweepingOutput(podName, k8sDirectInfraYaml, podContainers, ambiance);

    return CIK8PodParams.<CIK8ContainerParams>builder()
        .name(podName)
        .namespace(k8sDirectInfraYaml.getSpec().getNamespace().getValue())
        .labels(buildLabels)
        .serviceAccountName(serviceAccountName)
        .annotations(annotations)
        .nodeSelector(nodeSelector)
        .runAsUser(stepAsUser)
        .automountServiceAccountToken(k8sDirectInfraYaml.getSpec().getAutomountServiceAccountToken().getValue())
        .priorityClassName(k8sDirectInfraYaml.getSpec().getPriorityClassName().getValue())
        .containerParamsList(podContainers.getRight())
        .initContainerParamsList(singletonList(podContainers.getLeft()))
        .tolerations(k8sPodInitUtils.getPodTolerations(k8sDirectInfraYaml.getSpec().getTolerations()))
        .activeDeadLineSeconds(CIConstants.POD_MAX_TTL_SECS)
        .volumes(volumes)
        .build();
  }

  private List<String> getSharedPaths(ContainerStepSpec initializeStepInfo) {
    if (initializeStepInfo instanceof InitContainerV2StepInfo) {
      InitContainerV2StepInfo initConfig = (InitContainerV2StepInfo) initializeStepInfo;
      return (List<String>) initConfig.getSharedPaths().fetchFinalValue();
    }
    return Collections.emptyList();
  }

  private Pair<CIK8ContainerParams, List<CIK8ContainerParams>> getStepContainers(ContainerStepSpec containerStepInfo,
      ContainerDetailsSweepingOutput k8PodDetails, ContainerK8sInfra infrastructure, Ambiance ambiance,
      List<PodVolume> volumes, String logPrefix) {
    List<String> sharedPaths = getSharedPaths(containerStepInfo);
    Map<String, String> volumeToMountPath = k8sPodInitUtils.getVolumeToMountPath(sharedPaths, volumes);
    OSType os = k8sPodInitUtils.getOS(infrastructure);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    Map<String, String> logEnvVars = k8sPodInitUtils.getLogServiceEnvVariables(k8PodDetails, accountId);
    Map<String, String> commonEnvVars =
        k8sPodInitUtils.getCommonStepEnvVariables(k8PodDetails, k8sPodInitUtils.getWorkDir(), logPrefix, ambiance);

    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForK8(ngAccess, infrastructure);

    ContainerSecretEvaluator liteEngineSecretEvaluator =
        ContainerSecretEvaluator.builder().secretUtils(secretUtils).build();
    List<SecretVariableDetails> secretVariableDetails =
        liteEngineSecretEvaluator.resolve(containerStepInfo, ngAccess, ambiance.getExpressionFunctorToken());
    k8sPodInitUtils.checkSecretAccess(ambiance, secretVariableDetails, accountId,
        AmbianceUtils.getProjectIdentifier(ambiance), AmbianceUtils.getOrgIdentifier(ambiance));

    Pair<Integer, Integer> wrapperRequests = k8sPodInitUtils.getStepRequest(containerStepInfo, accountId);
    Integer stageCpuRequest = wrapperRequests.getLeft();
    Integer stageMemoryRequest = wrapperRequests.getRight();
    List<CIK8ContainerParams> containerParams = new ArrayList<>();

    final CIExecutionImages overridenExecutionConfig =
        fetchCiExecutionImagesWithRetries(accountId, ContainerInfraMapper.toStageInfraType(infrastructure))
            .orElse(null);

    CIK8ContainerParams setupAddOnContainerParams = getSetupAddOnContainerParams(
        infrastructure, volumeToMountPath, os, ngAccess, harnessInternalImageConnector, overridenExecutionConfig);

    CIK8ContainerParams liteEngineContainerParams =
        getLiteEngineContainerParams(k8PodDetails, infrastructure, ambiance, logPrefix, volumeToMountPath, logEnvVars,
            harnessInternalImageConnector, stageCpuRequest, stageMemoryRequest, overridenExecutionConfig);
    List<ContainerDefinitionInfo> stepCtrDefinitions =
        getContainerDefinitionInfos(containerStepInfo, infrastructure, ambiance, logPrefix, volumeToMountPath, os,
            ngAccess, commonEnvVars, harnessInternalImageConnector, secretVariableDetails, containerParams);

    consumePortDetails(ambiance, stepCtrDefinitions);
    containerParams.add(liteEngineContainerParams);
    return Pair.of(setupAddOnContainerParams, containerParams);
  }

  private List<ContainerDefinitionInfo> getContainerDefinitionInfos(ContainerStepSpec containerStepInfo,
      ContainerK8sInfra infrastructure, Ambiance ambiance, String logPrefix, Map<String, String> volumeToMountPath,
      OSType os, NGAccess ngAccess, Map<String, String> commonEnvVars, ConnectorDetails harnessInternalImageConnector,
      List<SecretVariableDetails> secretVariableDetails, List<CIK8ContainerParams> containerParams) {
    List<ContainerDefinitionInfo> stepCtrDefinitions =
        getStepContainerDefinitions(containerStepInfo, infrastructure, ambiance);
    Map<String, List<ConnectorConversionInfo>> stepConnectorMap =
        getStepConnectorRefsV2(containerStepInfo, AmbianceUtils.obtainStepGroupIdentifier(ambiance));
    for (ContainerDefinitionInfo containerDefinitionInfo : stepCtrDefinitions) {
      CIK8ContainerParams cik8ContainerParams =
          createCIK8ContainerParams(ngAccess, containerDefinitionInfo, harnessInternalImageConnector, commonEnvVars,
              stepConnectorMap, volumeToMountPath, k8sPodInitUtils.getWorkDir(),
              k8sPodInitUtils.getCtrSecurityContext(infrastructure), logPrefix, secretVariableDetails, os);
      containerParams.add(cik8ContainerParams);
    }
    return stepCtrDefinitions;
  }

  private Map<String, List<ConnectorConversionInfo>> getStepConnectorRefsV2(
      ContainerStepSpec containerStepInfo, String stepGroupIdentifier) {
    Map<String, List<ConnectorConversionInfo>> stepConnectorMap = new HashMap<>();
    if (containerStepInfo instanceof PluginStep) {
      PluginStep pluginStep = (PluginStep) containerStepInfo;
      String identifier = containerStepInfo.getIdentifier();
      if (isNotEmpty(stepGroupIdentifier)) {
        identifier = stepGroupIdentifier + "_" + identifier;
      }
      stepConnectorMap.put(identifier, new ArrayList<>());
      // This is required for ContainerStep V1.
      String kubernetesStandardPodName =
          ContainerUnitStepUtils.getKubernetesStandardPodName(containerStepInfo.getIdentifier());
      stepConnectorMap.put(kubernetesStandardPodName, new ArrayList<>());
      String connectorRef = PluginUtils.getConnectorRef(pluginStep);
      if (EmptyPredicate.isEmpty(connectorRef)) {
        return stepConnectorMap;
      }
      Map<EnvVariableEnum, String> envToSecretMap = PluginUtils.getConnectorSecretEnvMap(pluginStep.getType());
      stepConnectorMap.get(identifier)
          .add(ConnectorConversionInfo.builder().connectorRef(connectorRef).envToSecretsMap(envToSecretMap).build());
      stepConnectorMap.get(kubernetesStandardPodName)
          .add(ConnectorConversionInfo.builder().connectorRef(connectorRef).envToSecretsMap(envToSecretMap).build());
    } else if (containerStepInfo instanceof InitContainerV2StepInfo) {
      InitContainerV2StepInfo initContainerV2StepInfo = (InitContainerV2StepInfo) containerStepInfo;
      initContainerV2StepInfo.getPluginsData().values().forEach(PluginCreationResponseWrapper -> {
        for (PluginCreationResponseWrapper responseV2 : PluginCreationResponseWrapper.getResponseList()) {
          List<io.harness.pms.contracts.plan.ConnectorDetails> connectorsForStepList =
              responseV2.getResponse().getPluginDetails().getConnectorsForStepList();
          if (isNotEmpty(connectorsForStepList)) {
            List<ConnectorConversionInfo> connectorConversionInfo =
                connectorsForStepList.stream()
                    .map(detail
                        -> ConnectorConversionInfo.builder()
                               .connectorRef(detail.getConnectorRef())
                               .envToSecretsMap(new HashMap<>(convertDetailMap(detail.getConnectorSecretEnvMapMap())))
                               .build())
                    .collect(toList());
            String identifier = responseV2.getStepInfo().getIdentifier();
            if (isNotEmpty(stepGroupIdentifier)) {
              identifier = stepGroupIdentifier + "_" + identifier;
            }
            stepConnectorMap.put(identifier, connectorConversionInfo);
          }
        }
      });
    }
    return stepConnectorMap;
  }

  private CIK8ContainerParams getLiteEngineContainerParams(ContainerDetailsSweepingOutput k8PodDetails,
      ContainerK8sInfra infrastructure, Ambiance ambiance, String logPrefix, Map<String, String> volumeToMountPath,
      Map<String, String> logEnvVars, ConnectorDetails harnessInternalImageConnector, Integer stageCpuRequest,
      Integer stageMemoryRequest, CIExecutionImages ciExecutionImages) {
    return containerParamsProvider.getLiteEngineContainerParams(harnessInternalImageConnector, k8PodDetails,
        stageCpuRequest, stageMemoryRequest, logEnvVars, volumeToMountPath, k8sPodInitUtils.getWorkDir(),
        k8sPodInitUtils.getCtrSecurityContext(infrastructure), logPrefix, ambiance, ciExecutionImages);
  }

  private CIK8ContainerParams getSetupAddOnContainerParams(ContainerK8sInfra infrastructure,
      Map<String, String> volumeToMountPath, OSType os, NGAccess ngAccess,
      ConnectorDetails harnessInternalImageConnector, CIExecutionImages overridenExecutionImages) {
    return containerParamsProvider.getSetupAddonContainerParams(harnessInternalImageConnector, volumeToMountPath,
        k8sPodInitUtils.getWorkDir(), k8sPodInitUtils.getCtrSecurityContext(infrastructure), os,
        overridenExecutionImages);
  }

  private CIK8ContainerParams createCIK8ContainerParams(NGAccess ngAccess,
      ContainerDefinitionInfo containerDefinitionInfo, ConnectorDetails harnessInternalImageConnector,
      Map<String, String> commonEnvVars, Map<String, List<ConnectorConversionInfo>> connectorRefs,
      Map<String, String> volumeToMountPath, String workDirPath, ContainerSecurityContext ctrSecurityContext,
      String logPrefix, List<SecretVariableDetails> secretVariableDetails, OSType os) {
    Map<String, String> envVars = new HashMap<>();
    if (isNotEmpty(containerDefinitionInfo.getEnvVars())) {
      envVars.putAll(containerDefinitionInfo.getEnvVars()); // Put customer input env variables
    }
    Map<String, ConnectorDetails> stepConnectorDetails = new HashMap<>();
    if (isNotEmpty(containerDefinitionInfo.getStepIdentifier()) && isNotEmpty(connectorRefs)) {
      List<ConnectorConversionInfo> connectorConversionInfos =
          connectorRefs.get(containerDefinitionInfo.getStepIdentifier());
      if (isNotEmpty(connectorConversionInfos)) {
        for (ConnectorConversionInfo connectorConversionInfo : connectorConversionInfos) {
          ConnectorDetails connectorDetails =
              connectorUtils.getConnectorDetailsWithConversionInfo(ngAccess, connectorConversionInfo);
          IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(connectorConversionInfo.getConnectorRef(),
              ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
          stepConnectorDetails.put(identifierRef.getFullyQualifiedName(), connectorDetails);
        }
      }
    }

    ImageDetails imageDetails = containerDefinitionInfo.getContainerImageDetails().getImageDetails();
    ConnectorDetails connectorDetails = null;
    if (containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier() != null) {
      connectorDetails = connectorUtils.getConnectorDetails(
          ngAccess, containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier());
    }

    ConnectorDetails imgConnector = connectorDetails;
    if (containerDefinitionInfo.isHarnessManagedImage()) {
      imgConnector = harnessInternalImageConnector;
    }
    String fullyQualifiedImageName = harnessImageUtils.getFullyQualifiedImageName(imageDetails.getName(), imgConnector);
    imageDetails.setName(fullyQualifiedImageName);
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageConnectorDetails(imgConnector).imageDetails(imageDetails).build();

    List<SecretVariableDetails> containerSecretVariableDetails =
        k8sPodInitUtils.getSecretVariableDetails(ngAccess, containerDefinitionInfo, secretVariableDetails);

    Map<String, String> envVarsWithSecretRef = k8sPodInitUtils.removeEnvVarsWithSecretRef(envVars);
    envVars.putAll(commonEnvVars); //  commonEnvVars needs to be put in end because they overrides webhook parameters
    if (containerDefinitionInfo.getContainerType() == CIContainerType.SERVICE) {
      envVars.put(HARNESS_SERVICE_LOG_KEY_VARIABLE,
          format("%s/serviceId:%s", logPrefix, containerDefinitionInfo.getStepIdentifier()));
    }

    if (containerDefinitionInfo.getPrivileged() != null) {
      ctrSecurityContext.setPrivileged(containerDefinitionInfo.getPrivileged());
    }
    if (containerDefinitionInfo.getRunAsUser() != null) {
      ctrSecurityContext.setRunAsUser(containerDefinitionInfo.getRunAsUser());
    }
    boolean privileged = containerDefinitionInfo.getPrivileged() != null && containerDefinitionInfo.getPrivileged();

    CIK8ContainerParams cik8ContainerParams =
        CIK8ContainerParams.builder()
            .name(containerDefinitionInfo.getName())
            .containerResourceParams(containerDefinitionInfo.getContainerResourceParams())
            .containerType(containerDefinitionInfo.getContainerType())
            .envVars(envVars)
            .envVarsWithSecretRef(envVarsWithSecretRef)
            .containerSecrets(ContainerSecrets.builder()
                                  .secretVariableDetails(containerSecretVariableDetails)
                                  .connectorDetailsMap(stepConnectorDetails)
                                  .plainTextSecretsByName(containerParamsProvider.getLiteEngineSecretVars(emptyMap()))
                                  .build())
            .commands(containerDefinitionInfo.getCommands())
            .ports(containerDefinitionInfo.getPorts())
            .args(containerDefinitionInfo.getArgs())
            .imageDetailsWithConnector(imageDetailsWithConnector)
            .volumeToMountPath(volumeToMountPath)
            .imagePullPolicy(containerDefinitionInfo.getImagePullPolicy())
            .securityContext(ctrSecurityContext)
            .build();

    if (os != OSType.Windows) {
      cik8ContainerParams.setPrivileged(privileged);
      cik8ContainerParams.setRunAsUser(containerDefinitionInfo.getRunAsUser());
    }

    if (containerDefinitionInfo.getContainerType() != CIContainerType.SERVICE) {
      cik8ContainerParams.setWorkingDir(workDirPath);
    }
    return cik8ContainerParams;
  }

  private List<ContainerDefinitionInfo> getStepContainerDefinitions(
      ContainerStepSpec initializeStepInfo, ContainerK8sInfra infrastructure, Ambiance ambiance) {
    OSType os = k8sPodInitUtils.getOS(infrastructure);
    Set<Integer> usedPorts = new HashSet<>();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();

    return createStepContainerDefinitions(
        initializeStepInfo, portFinder, AmbianceUtils.getAccountId(ambiance), os, ambiance);
  }

  private void saveSweepingOutput(String podName, ContainerK8sInfra infrastructure,
      Pair<CIK8ContainerParams, List<CIK8ContainerParams>> podContainers, Ambiance ambiance) {
    List<String> containerNames = podContainers.getRight().stream().map(CIK8ContainerParams::getName).collect(toList());
    containerNames.add(podContainers.getLeft().getName());

    k8sPodInitUtils.consumeSweepingOutput(ambiance,
        ContainerCleanupDetails.builder()
            .infrastructure(infrastructure)
            .podName(podName)
            .cleanUpContainerNames(containerNames)
            .build(),
        CLEANUP_DETAILS);
    k8sPodInitUtils.consumeSweepingOutput(ambiance,
        K8StageInfraDetails.builder()
            .infrastructure(infrastructure.toCIInfra())
            .podName(podName)
            .containerNames(containerNames)
            .build(),
        STAGE_INFRA_DETAILS);
  }

  private String getPodName(String stageId) {
    return k8sPodInitUtils.generatePodName(stageId);
  }

  private void consumePortDetails(Ambiance ambiance, List<ContainerDefinitionInfo> containerDefinitionInfos) {
    Map<String, List<Integer>> portDetails = containerDefinitionInfos.stream().collect(
        Collectors.toMap(ContainerDefinitionInfo::getStepIdentifier, ContainerDefinitionInfo::getPorts));
    k8sPodInitUtils.consumeSweepingOutput(
        ambiance, ContainerPortDetails.builder().portDetails(portDetails).build(), PORT_DETAILS);
  }

  private List<ContainerDefinitionInfo> createStepContainerDefinitions(
      ContainerStepSpec containerStepInfo, PortFinder portFinder, String accountId, OSType os, Ambiance ambiance) {
    switch (containerStepInfo.getType()) {
      case RUN_CONTAINER:
        return Collections.singletonList(
            createStepContainerDefinition((ContainerStepInfo) containerStepInfo, portFinder, accountId, os));
      case CD_SSCA_ORCHESTRATION:
        return Collections.singletonList(
            createPluginStepContainerDefinition((PluginStep) containerStepInfo, portFinder, accountId, os, ambiance));
      case INIT_CONTAINER_V2:
        String stepGroupIdentifier = AmbianceUtils.obtainStepGroupIdentifier(ambiance);

        return ContainerStepV2DefinitionCreator.getContainerDefinitionInfo(
            (InitContainerV2StepInfo) containerStepInfo, stepGroupIdentifier);
      default:
        throw new ContainerStepExecutionException("Container step initialization not handled");
    }
  }

  private ContainerDefinitionInfo createPluginStepContainerDefinition(
      PluginStep pluginStep, PortFinder portFinder, String accountId, OSType os, Ambiance ambiance) {
    Integer port = portFinder.getNextPort();
    String identifier = ContainerUnitStepUtils.getKubernetesStandardPodName(pluginStep.getIdentifier());
    String containerName = format("%s%s", STEP_PREFIX, identifier).toLowerCase();

    Map<String, String> envMap =
        new HashMap<>(pluginUtils.getPluginCompatibleEnvVariables(pluginStep, identifier, ambiance));
    Map<String, SecretNGVariable> secretNGVariableMap =
        new HashMap<>(pluginUtils.getPluginCompatibleSecretVars(pluginStep));

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(envMap)
        .secretVariables(new ArrayList<>(secretNGVariableMap.values()))
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(k8sPodInitUtils.getImageInfo(
                                       pluginExecutionConfigHelper.getPluginImage(pluginStep).getImage()))
                                   .build())
        .isHarnessManagedImage(true)
        .containerResourceParams(getStepContainerResource(pluginStep, accountId))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.PLUGIN)
        .stepIdentifier(identifier)
        .stepName(pluginStep.getName())
        .imagePullPolicy(null)
        .privileged(null)
        .runAsUser(null)
        .build();
  }

  private ContainerDefinitionInfo createStepContainerDefinition(
      ContainerStepInfo runStepInfo, PortFinder portFinder, String accountId, OSType os) {
    if (runStepInfo.getImage() == null) {
      throw new ContainerStepExecutionException("image can't be empty in k8s infrastructure");
    }

    if (runStepInfo.getConnectorRef() == null) {
      throw new ContainerStepExecutionException("connector ref can't be empty in k8s infrastructure");
    }
    String identifier = ContainerUnitStepUtils.getKubernetesStandardPodName(runStepInfo.getIdentifier());
    Integer port = portFinder.getNextPort();
    String containerName = format("%s%s", STEP_PREFIX, identifier).toLowerCase();

    Map<String, String> stepEnvVars = new HashMap<>();
    Map<String, String> envvars = ExpressionResolverUtils.resolveMapParameter(
        "envVariables", "Run", identifier, runStepInfo.getEnvVariables(), false);
    if (!isEmpty(envvars)) {
      stepEnvVars.putAll(envvars);
    }
    Integer runAsUser = ExpressionResolverUtils.resolveIntegerParameter(runStepInfo.getRunAsUser(), null);

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(stepEnvVars)
        .stepIdentifier(identifier)
        .containerImageDetails(
            ContainerImageDetails.builder()
                .imageDetails(k8sPodInitUtils.getImageInfo(ExpressionResolverUtils.resolveStringParameter(
                    "Image", "Run", identifier, runStepInfo.getImage(), true)))
                .connectorIdentifier(ExpressionResolverUtils.resolveStringParameter(
                    "connectorRef", "Run", identifier, runStepInfo.getConnectorRef(), true))
                .build())
        .containerResourceParams(getStepContainerResource(runStepInfo, accountId))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.RUN)
        .stepName(runStepInfo.getName())
        .privileged(runStepInfo.getPrivileged().getValue())
        .runAsUser(runAsUser)
        .imagePullPolicy(ContainerStepResolverUtils.resolveImagePullPolicy(runStepInfo.getImagePullPolicy()))
        .build();
  }

  private ContainerResourceParams getStepContainerResource(ContainerStepSpec resource, String accountId) {
    Integer cpuLimit;
    Integer memoryLimit;
    Pair<Integer, Integer> stepRequest = k8sPodInitUtils.getStepRequest(resource, accountId);
    cpuLimit = stepRequest.getLeft();
    memoryLimit = stepRequest.getRight();

    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
        .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
        .resourceLimitMilliCpu(cpuLimit)
        .resourceLimitMemoryMiB(memoryLimit)
        .build();
  }

  public CIK8InitializeTaskParams getK8InitializeTaskParams(
      ContainerStepSpec containerStepSpec, Ambiance ambiance, String logPrefix, String stepIdentifier) {
    ContainerStepInfra infra = containerStepSpec.getInfrastructure();
    if (infra.getType() != KUBERNETES_DIRECT) {
      throw new ContainerStepExecutionException(format("Invalid infrastructure type: %s", infra.getType()));
    }
    ContainerK8sInfra infrastructure = (ContainerK8sInfra) infra;

    ContainerDetailsSweepingOutput k8PodDetails = ContainerDetailsSweepingOutput.builder()
                                                      .stepIdentifier(stepIdentifier)
                                                      .accountId(AmbianceUtils.getAccountId(ambiance))
                                                      .build();
    k8sPodInitUtils.consumeSweepingOutput(ambiance, k8PodDetails, INIT_POD);
    return buildK8DirectTaskParams(containerStepSpec, k8PodDetails, infrastructure, ambiance, logPrefix);
  }

  private Map<EnvVariableEnum, String> convertDetailMap(Map<String, String> connectorSecretEnvMapMap) {
    return emptyIfNull(connectorSecretEnvMapMap)
        .entrySet()
        .stream()
        .collect(Collectors.toMap(e -> EnvVariableEnum.valueOf(e.getKey()), Map.Entry::getValue));
  }

  private Optional<CIExecutionImages> fetchCiExecutionImagesWithRetries(
      String accountIdentifier, StageInfraDetails.Type infraType) {
    try {
      return Failsafe.with(RETRY_POLICY_EXECUTION_CONFIGS)
          .get(() -> fetchCiExecutionImagesInternal(accountIdentifier, infraType));
    } catch (Exception ex) {
      log.error("Failed to fetch CI execution configs, will use default execution config", ex);
      return Optional.empty();
    }
  }

  private Optional<CIExecutionImages> fetchCiExecutionImagesInternal(
      String accountIdentifier, StageInfraDetails.Type infraType) throws IOException {
    final Response<ResponseDTO<CIExecutionImages>> response =
        ciServiceResourceClient.getCustomersExecutionConfig(infraType, true, accountIdentifier).execute();
    if (response.isSuccessful()) {
      if (response.body() != null) {
        CIExecutionImages data = response.body().getData();
        return Optional.ofNullable(data);
      }
      return Optional.empty();
    } else {
      // silently ignore error during reading the error body
      try (ResponseBody errorBody = response.errorBody()) {
        String errorBodyString = errorBody != null ? errorBody.string() : null;
        log.error(FAILED_TO_FETCH_CI_EXECUTION_CONFIG_MSG + ": " + errorBodyString);
      }
      throw new InvalidRequestException(FAILED_TO_FETCH_CI_EXECUTION_CONFIG_MSG);
    }
  }
}
