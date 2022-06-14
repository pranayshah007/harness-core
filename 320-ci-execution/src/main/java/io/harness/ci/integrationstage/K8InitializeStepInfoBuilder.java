/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.UNRESOLVED_PARAMETER;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveIntegerParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveOSType;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameterWithDefaultValue;
import static io.harness.common.CICommonPodConstants.POD_NAME_PREFIX;
import static io.harness.common.CIExecutionConstants.ADDON_VOLUME;
import static io.harness.common.CIExecutionConstants.ADDON_VOL_MOUNT_PATH;
import static io.harness.common.CIExecutionConstants.DEFAULT_CONTAINER_CPU_POV;
import static io.harness.common.CIExecutionConstants.DEFAULT_CONTAINER_MEM_POV;
import static io.harness.common.CIExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.common.CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS;
import static io.harness.common.CIExecutionConstants.SHARED_VOLUME_PREFIX;
import static io.harness.common.CIExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.common.CIExecutionConstants.STEP_PREFIX;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.common.CIExecutionConstants.STEP_VOLUME;
import static io.harness.common.CIExecutionConstants.STEP_WORK_DIR;
import static io.harness.common.CIExecutionConstants.VOLUME_PREFIX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.Character.toLowerCase;
import static java.lang.String.format;
import static org.apache.commons.lang3.CharUtils.isAsciiAlphanumeric;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.environment.BuildJobEnvInfo;
import io.harness.beans.environment.K8BuildJobEnvInfo;
import io.harness.beans.environment.pod.PodSetupInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.quantity.unit.DecimalQuantityUnit;
import io.harness.beans.quantity.unit.StorageQuantityUnit;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.beans.yaml.extended.volumes.HostPathYaml;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml;
import io.harness.ci.utils.QuantityUtils;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.EmptyDirVolume;
import io.harness.delegate.beans.ci.pod.EmptyDirVolume.EmptyDirVolumeBuilder;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.pod.HostPathVolume;
import io.harness.delegate.beans.ci.pod.PVCParams;
import io.harness.delegate.beans.ci.pod.PVCVolume;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.execution.CIExecutionConfigService;
import io.harness.ff.CIFeatureFlagService;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.stateutils.buildstate.PluginSettingUtils;
import io.harness.stateutils.buildstate.providers.StepContainerUtils;
import io.harness.steps.CIStepInfoUtils;
import io.harness.util.ExceptionUtility;
import io.harness.util.PortFinder;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
@Deprecated
public class K8InitializeStepInfoBuilder implements InitializeStepInfoBuilder {
  private static final String PLUGIN_USERNAME = "PLUGIN_USERNAME";
  private static final String PLUGIN_PASSW = "PLUGIN_PASSWORD";
  private static final String PLUGIN_REGISTRY = "PLUGIN_REGISTRY";
  private static final String PLUGIN_ACCESS_KEY = "PLUGIN_ACCESS_KEY";
  private static final String PLUGIN_SECRET_KEY = "PLUGIN_SECRET_KEY";
  private static final String PLUGIN_JSON_KEY = "PLUGIN_JSON_KEY";
  private static final String PLUGIN_URL = "PLUGIN_URL";
  static final String SOURCE = "123456789bcdfghjklmnpqrstvwxyz";
  static final Integer RANDOM_LENGTH = 8;
  private static final SecureRandom random = new SecureRandom();
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject private ConnectorUtils connectorUtils;

  @Override
  public BuildJobEnvInfo getInitializeStepInfoBuilder(StageElementConfig stageElementConfig,
      Infrastructure infrastructure, CIExecutionArgs ciExecutionArgs, List<ExecutionWrapperConfig> steps,
      Ambiance ambiance) {
    String uniqueStageExecutionIdentifier = generatePodName(stageElementConfig.getIdentifier());
    return K8BuildJobEnvInfo.builder()
        .podsSetupInfo(getCIPodsSetupInfo(stageElementConfig, infrastructure, ciExecutionArgs, steps, true,
            uniqueStageExecutionIdentifier, AmbianceUtils.getAccountId(ambiance)))
        .workDir(STEP_WORK_DIR)
        .stepConnectorRefs(getStepConnectorRefs(stageElementConfig, ambiance))
        .build();
  }

  private String generatePodName(String identifier) {
    return POD_NAME_PREFIX + "-" + getK8PodIdentifier(identifier) + "-"
        + generateRandomAlphaNumericString(RANDOM_LENGTH);
  }

  public String getK8PodIdentifier(String identifier) {
    StringBuilder sb = new StringBuilder(15);
    for (char c : identifier.toCharArray()) {
      if (isAsciiAlphanumeric(c)) {
        sb.append(toLowerCase(c));
      }
      if (sb.length() == 15) {
        return sb.toString();
      }
    }
    return sb.toString();
  }

  public static String generateRandomAlphaNumericString(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(SOURCE.charAt(random.nextInt(SOURCE.length())));
    }
    return sb.toString();
  }

  private K8BuildJobEnvInfo.PodsSetupInfo getCIPodsSetupInfo(StageElementConfig stageElementConfig,
      Infrastructure infrastructure, CIExecutionArgs ciExecutionArgs, List<ExecutionWrapperConfig> steps,
      boolean isFirstPod, String podName, String accountId) {
    List<PodSetupInfo> pods = new ArrayList<>();

    OSType os = getOS(infrastructure);

    Set<Integer> usedPorts = new HashSet<>();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();
    String workDirPath = STEP_WORK_DIR;
    List<ContainerDefinitionInfo> serviceContainerDefinitionInfos = CIServiceBuilder.createServicesContainerDefinition(
        stageElementConfig, portFinder, ciExecutionConfigService.getCiExecutionServiceConfig(), os);
    List<ContainerDefinitionInfo> stepContainerDefinitionInfos =
        createStepsContainerDefinition(steps, stageElementConfig, ciExecutionArgs, portFinder, accountId, os);

    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    containerDefinitionInfos.addAll(serviceContainerDefinitionInfos);
    containerDefinitionInfos.addAll(stepContainerDefinitionInfos);

    Integer stageMemoryRequest = getStageMemoryRequest(steps, accountId);
    Integer stageCpuRequest = getStageCpuRequest(steps, accountId);
    List<String> serviceIdList = CIServiceBuilder.getServiceIdList(stageElementConfig);
    List<Integer> serviceGrpcPortList = CIServiceBuilder.getServiceGrpcPortList(stageElementConfig);
    IntegrationStageConfig integrationStageConfig = IntegrationStageUtils.getIntegrationStageConfig(stageElementConfig);

    List<PodVolume> volumes = convertVolumes(infrastructure);

    if (integrationStageConfig.getSharedPaths().isExpression()) {
      ExceptionUtility.throwUnresolvedExpressionException(integrationStageConfig.getSharedPaths().getExpressionValue(),
          "sharedPath", "stage with identifier: " + stageElementConfig.getIdentifier());
    }
    Map<String, String> volumeToMountPath =
        getVolumeToMountPath((List<String>) integrationStageConfig.getSharedPaths().fetchFinalValue(), volumes);
    pods.add(PodSetupInfo.builder()
                 .podSetupParams(
                     PodSetupInfo.PodSetupParams.builder().containerDefinitionInfos(containerDefinitionInfos).build())
                 .name(podName)
                 .pvcParamsList(createPVCParams(isFirstPod, podName, volumeToMountPath))
                 .volumeToMountPath(volumeToMountPath)
                 .stageCpuRequest(stageCpuRequest)
                 .stageMemoryRequest(stageMemoryRequest)
                 .serviceIdList(serviceIdList)
                 .serviceGrpcPortList(serviceGrpcPortList)
                 .workDirPath(workDirPath)
                 .volumes(volumes)
                 .build());
    return K8BuildJobEnvInfo.PodsSetupInfo.builder().podSetupInfoList(pods).build();
  }

  private OSType getOS(Infrastructure infrastructure) {
    if (infrastructure.getType() != Infrastructure.Type.KUBERNETES_DIRECT) {
      return OSType.Linux;
    }

    if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) infrastructure;
    return resolveOSType(k8sDirectInfraYaml.getSpec().getOs());
  }

  private List<PVCParams> createPVCParams(boolean isFirstPod, String podName, Map<String, String> volumeToMountPath) {
    List<PVCParams> pvcParamsList = new ArrayList<>();

    for (String volumeName : volumeToMountPath.keySet()) {
      String claimName = format("%s-%s", podName, volumeName);
      pvcParamsList.add(PVCParams.builder()
                            .claimName(claimName)
                            .volumeName(volumeName)
                            .isPresent(!isFirstPod)
                            .sizeMib(ciExecutionConfigService.getCiExecutionServiceConfig().getPvcDefaultStorageSize())
                            .storageClass(PVC_DEFAULT_STORAGE_CLASS)
                            .build());
    }
    return pvcParamsList;
  }

  private Map<String, String> getVolumeToMountPath(List<String> sharedPaths, List<PodVolume> volumes) {
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(STEP_VOLUME, STEP_MOUNT_PATH);
    volumeToMountPath.put(ADDON_VOLUME, ADDON_VOL_MOUNT_PATH);

    int index = 0;
    if (sharedPaths != null) {
      for (String path : sharedPaths) {
        if (isEmpty(path)) {
          continue;
        }

        String volumeName = format("%s%d", SHARED_VOLUME_PREFIX, index);
        if (path.equals(STEP_MOUNT_PATH) || path.equals(ADDON_VOL_MOUNT_PATH)) {
          throw new InvalidRequestException(format("Shared path: %s is a reserved keyword ", path));
        }
        volumeToMountPath.put(volumeName, path);
        index++;
      }
    }

    if (isNotEmpty(volumes)) {
      for (PodVolume volume : volumes) {
        if (volume.getType() == PodVolume.Type.EMPTY_DIR) {
          EmptyDirVolume emptyDirVolume = (EmptyDirVolume) volume;
          volumeToMountPath.put(emptyDirVolume.getName(), emptyDirVolume.getMountPath());
        } else if (volume.getType() == PodVolume.Type.HOST_PATH) {
          HostPathVolume hostPathVolume = (HostPathVolume) volume;
          volumeToMountPath.put(hostPathVolume.getName(), hostPathVolume.getMountPath());
        } else if (volume.getType() == PodVolume.Type.PVC) {
          PVCVolume pvcVolume = (PVCVolume) volume;
          volumeToMountPath.put(pvcVolume.getName(), pvcVolume.getMountPath());
        }
      }
    }
    return volumeToMountPath;
  }

  private List<ContainerDefinitionInfo> createStepsContainerDefinition(List<ExecutionWrapperConfig> steps,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, String accountId,
      OSType os) {
    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    if (steps == null) {
      return containerDefinitionInfos;
    }

    int stepIndex = 0;
    for (ExecutionWrapperConfig executionWrapper : steps) {
      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
        stepIndex++;
        if (stepElementConfig.getTimeout() != null && stepElementConfig.getTimeout().isExpression()) {
          throw new InvalidRequestException(
              "Timeout field must be resolved in step: " + stepElementConfig.getIdentifier());
        }

        ContainerDefinitionInfo containerDefinitionInfo = createStepContainerDefinition(
            stepElementConfig, integrationStage, ciExecutionArgs, portFinder, stepIndex, accountId, os);
        if (containerDefinitionInfo != null) {
          containerDefinitionInfos.add(containerDefinitionInfo);
        }
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig =
            IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
        if (isNotEmpty(parallelStepElementConfig.getSections())) {
          for (ExecutionWrapperConfig executionWrapperInParallel : parallelStepElementConfig.getSections()) {
            if (executionWrapperInParallel.getStep() == null || executionWrapperInParallel.getStep().isNull()) {
              continue;
            }

            stepIndex++;
            StepElementConfig stepElementConfig =
                IntegrationStageUtils.getStepElementConfig(executionWrapperInParallel);
            ContainerDefinitionInfo containerDefinitionInfo = createStepContainerDefinition(
                stepElementConfig, integrationStage, ciExecutionArgs, portFinder, stepIndex, accountId, os);
            if (containerDefinitionInfo != null) {
              containerDefinitionInfos.add(containerDefinitionInfo);
            }
          }
        }
      }
    }
    return containerDefinitionInfos;
  }

  private ContainerDefinitionInfo createStepContainerDefinition(StepElementConfig stepElement,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String accountId, OSType os) {
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return null;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    validateStepType(ciStepInfo.getNonYamlInfo().getStepInfoType(), os);

    long timeout = TimeoutUtils.getTimeoutInSeconds(stepElement.getTimeout(), ciStepInfo.getDefaultTimeout());
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return createRunStepContainerDefinition((RunStepInfo) ciStepInfo, integrationStage, ciExecutionArgs, portFinder,
            stepIndex, stepElement.getIdentifier(), stepElement.getName(), accountId, os);
      case DOCKER:
      case ECR:
      case GCR:
      case SAVE_CACHE_S3:
      case RESTORE_CACHE_S3:
      case RESTORE_CACHE_GCS:
      case SAVE_CACHE_GCS:
      case SECURITY:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_S3:
      case UPLOAD_GCS:
        return createPluginCompatibleStepContainerDefinition((PluginCompatibleStep) ciStepInfo, integrationStage,
            ciExecutionArgs, portFinder, stepIndex, stepElement.getIdentifier(), stepElement.getName(),
            stepElement.getType(), timeout, accountId, os);
      case PLUGIN:
        return createPluginStepContainerDefinition((PluginStepInfo) ciStepInfo, integrationStage, ciExecutionArgs,
            portFinder, stepIndex, stepElement.getIdentifier(), stepElement.getName(), accountId, os);
      case RUN_TESTS:
        return createRunTestsStepContainerDefinition((RunTestsStepInfo) ciStepInfo, integrationStage, ciExecutionArgs,
            portFinder, stepIndex, stepElement.getIdentifier(), accountId, os);
      default:
        return null;
    }
  }

  private void validateStepType(CIStepInfoType stepType, OSType os) {
    if (os != OSType.Windows) {
      return;
    }

    switch (stepType) {
      case DOCKER:
      case ECR:
      case GCR:
        throw new CIStageExecutionException(format("%s step not allowed in windows kubernetes builds", stepType));
      default:
        return;
    }
  }

  private ContainerDefinitionInfo createPluginCompatibleStepContainerDefinition(PluginCompatibleStep stepInfo,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String stepName, String stepType, long timeout, String accountId, OSType os) {
    Integer port = portFinder.getNextPort();

    String containerName = format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> envVarMap = new HashMap<>();
    envVarMap.putAll(getEnvVariables(integrationStage));
    envVarMap.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    envVarMap.putAll(PluginSettingUtils.getPluginCompatibleEnvVariables(stepInfo, identifier, timeout, Type.K8));
    Integer runAsUser = resolveIntegerParameter(stepInfo.getRunAsUser(), null);

    Boolean privileged = null;
    if (CIStepInfoUtils.getPrivilegedMode(stepInfo) != null) {
      privileged = CIStepInfoUtils.getPrivilegedMode(stepInfo).getValue();
    }
    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(envVarMap)
        .secretVariables(getSecretVariables(integrationStage))
        .containerImageDetails(
            ContainerImageDetails.builder()
                .imageDetails(IntegrationStageUtils.getImageInfo(
                    CIStepInfoUtils.getPluginCustomStepImage(stepInfo, ciExecutionConfigService, Type.K8, accountId)))
                .build())
        .isHarnessManagedImage(true)
        .containerResourceParams(getStepContainerResource(stepInfo.getResources(), stepType, identifier, accountId))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.PLUGIN)
        .stepIdentifier(identifier)
        .stepName(stepName)
        .imagePullPolicy(RunTimeInputHandler.resolveImagePullPolicy(CIStepInfoUtils.getImagePullPolicy(stepInfo)))
        .privileged(privileged)
        .runAsUser(runAsUser)
        .build();
  }

  private ContainerDefinitionInfo createRunStepContainerDefinition(RunStepInfo runStepInfo,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String name, String accountId, OSType os) {
    if (runStepInfo.getImage() == null) {
      throw new CIStageExecutionException("image can't be empty in k8s infrastructure");
    }

    if (runStepInfo.getConnectorRef() == null) {
      throw new CIStageExecutionException("connector ref can't be empty in k8s infrastructure");
    }

    Integer port = portFinder.getNextPort();

    String containerName = format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> stepEnvVars = new HashMap<>();
    stepEnvVars.putAll(getEnvVariables(integrationStage));
    stepEnvVars.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    Map<String, String> envvars =
        resolveMapParameter("envVariables", "Run", identifier, runStepInfo.getEnvVariables(), false);
    if (!isEmpty(envvars)) {
      stepEnvVars.putAll(envvars);
    }
    Integer runAsUser = resolveIntegerParameter(runStepInfo.getRunAsUser(), null);

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(stepEnvVars)
        .stepIdentifier(identifier)
        .secretVariables(getSecretVariables(integrationStage))
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(IntegrationStageUtils.getImageInfo(resolveStringParameter(
                                       "Image", "Run", identifier, runStepInfo.getImage(), true)))
                                   .connectorIdentifier(resolveStringParameter(
                                       "connectorRef", "Run", identifier, runStepInfo.getConnectorRef(), true))
                                   .build())
        .containerResourceParams(getStepContainerResource(runStepInfo.getResources(), "Run", identifier, accountId))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.RUN)
        .stepName(name)
        .privileged(runStepInfo.getPrivileged().getValue())
        .runAsUser(runAsUser)
        .imagePullPolicy(RunTimeInputHandler.resolveImagePullPolicy(runStepInfo.getImagePullPolicy()))
        .build();
  }

  private ContainerDefinitionInfo createRunTestsStepContainerDefinition(RunTestsStepInfo runTestsStepInfo,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String accountId, OSType os) {
    Integer port = portFinder.getNextPort();

    if (runTestsStepInfo.getImage() == null) {
      throw new CIStageExecutionException("image can't be empty in k8s infrastructure");
    }

    if (runTestsStepInfo.getConnectorRef() == null) {
      throw new CIStageExecutionException("connector ref can't be empty in k8s infrastructure");
    }

    String containerName = format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> stepEnvVars = new HashMap<>();
    stepEnvVars.putAll(getEnvVariables(integrationStage));
    stepEnvVars.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    Map<String, String> envvars =
        resolveMapParameter("envVariables", "RunTests", identifier, runTestsStepInfo.getEnvVariables(), false);
    if (!isEmpty(envvars)) {
      stepEnvVars.putAll(envvars);
    }
    Integer runAsUser = resolveIntegerParameter(runTestsStepInfo.getRunAsUser(), null);

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(stepEnvVars)
        .stepIdentifier(identifier)
        .secretVariables(getSecretVariables(integrationStage))
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(IntegrationStageUtils.getImageInfo(resolveStringParameter(
                                       "Image", "RunTest", identifier, runTestsStepInfo.getImage(), true)))
                                   .connectorIdentifier(resolveStringParameter(
                                       "connectorRef", "RunTest", identifier, runTestsStepInfo.getConnectorRef(), true))
                                   .build())
        .containerResourceParams(
            getStepContainerResource(runTestsStepInfo.getResources(), "RunTests", identifier, accountId))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.TEST_INTELLIGENCE)
        .privileged(runTestsStepInfo.getPrivileged().getValue())
        .runAsUser(runAsUser)
        .imagePullPolicy(RunTimeInputHandler.resolveImagePullPolicy(runTestsStepInfo.getImagePullPolicy()))
        .build();
  }

  private ContainerDefinitionInfo createPluginStepContainerDefinition(PluginStepInfo pluginStepInfo,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String name, String accountId, OSType os) {
    Integer port = portFinder.getNextPort();

    String containerName = format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> envVarMap = new HashMap<>();
    envVarMap.putAll(getEnvVariables(integrationStage));
    envVarMap.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    if (!isEmpty(pluginStepInfo.getEnvVariables())) {
      envVarMap.putAll(pluginStepInfo.getEnvVariables());
    }

    Integer runAsUser = resolveIntegerParameter(pluginStepInfo.getRunAsUser(), null);

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(envVarMap)
        .stepIdentifier(identifier)
        .secretVariables(getSecretVariables(integrationStage))
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(IntegrationStageUtils.getImageInfo(resolveStringParameter(
                                       "Image", "Plugin", identifier, pluginStepInfo.getImage(), true)))
                                   .connectorIdentifier(resolveStringParameter(
                                       "connectorRef", "Plugin", identifier, pluginStepInfo.getConnectorRef(), true))
                                   .build())
        .containerResourceParams(
            getStepContainerResource(pluginStepInfo.getResources(), "Plugin", identifier, accountId))
        .isHarnessManagedImage(pluginStepInfo.isHarnessManagedImage())
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.PLUGIN)
        .stepName(name)
        .privileged(pluginStepInfo.getPrivileged().getValue())
        .runAsUser(runAsUser)
        .imagePullPolicy(RunTimeInputHandler.resolveImagePullPolicy(pluginStepInfo.getImagePullPolicy()))
        .build();
  }

  private ContainerResourceParams getStepContainerResource(
      ContainerResource resource, String stepType, String stepId, String accountId) {
    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
        .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
        .resourceLimitMilliCpu(getContainerCpuLimit(resource, stepType, stepId, accountId))
        .resourceLimitMemoryMiB(getContainerMemoryLimit(resource, stepType, stepId, accountId))
        .build();
  }

  private List<SecretNGVariable> getSecretVariables(StageElementConfig stageElementConfig) {
    if (isEmpty(stageElementConfig.getVariables())) {
      return Collections.emptyList();
    }

    return stageElementConfig.getVariables()
        .stream()
        .filter(variable -> variable.getType() == NGVariableType.SECRET)
        .map(customVariable -> (SecretNGVariable) customVariable)
        .collect(Collectors.toList());
  }

  private ParallelStepElementConfig getParallelStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig parallel node", ex);
    }
  }

  private StepElementConfig getStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getStep().toString(), StepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig step node", ex);
    }
  }

  private Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> getStepConnectorRefs(
      StageElementConfig stageElementConfig, Ambiance ambiance) {
    IntegrationStageConfig integrationStageConfig = IntegrationStageUtils.getIntegrationStageConfig(stageElementConfig);

    List<ExecutionWrapperConfig> executionWrappers = integrationStageConfig.getExecution().getSteps();
    if (isEmpty(executionWrappers)) {
      return Collections.emptyMap();
    }

    Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> map = new HashMap<>();
    for (ExecutionWrapperConfig executionWrapperConfig : executionWrappers) {
      if (executionWrapperConfig.getParallel() != null && !executionWrapperConfig.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig = getParallelStepElementConfig(executionWrapperConfig);
        for (ExecutionWrapperConfig executionWrapper : parallelStepElementConfig.getSections()) {
          if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
            StepElementConfig stepElementConfig = getStepElementConfig(executionWrapper);
            map.putAll(getStepConnectorConversionInfo(stepElementConfig, ambiance));
          }
        }
      } else if (executionWrapperConfig.getStep() != null && !executionWrapperConfig.getStep().isNull()) {
        StepElementConfig stepElementConfig = getStepElementConfig(executionWrapperConfig);
        map.putAll(getStepConnectorConversionInfo(stepElementConfig, ambiance));
      }
    }
    return map;
  }

  private Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> getStepConnectorConversionInfo(
      StepElementConfig stepElement, Ambiance ambiance) {
    Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> map = new HashMap<>();
    if (stepElement.getStepSpecType() instanceof PluginCompatibleStep) {
      map.put(stepElement.getIdentifier(), new ArrayList<>());
      PluginCompatibleStep step = (PluginCompatibleStep) stepElement.getStepSpecType();
      String connectorRef = PluginSettingUtils.getConnectorRef(step);
      Map<EnvVariableEnum, String> envToSecretMap =
          PluginSettingUtils.getConnectorSecretEnvMap(step.getNonYamlInfo().getStepInfoType());
      map.get(stepElement.getIdentifier())
          .add(K8BuildJobEnvInfo.ConnectorConversionInfo.builder()
                   .connectorRef(connectorRef)
                   .envToSecretsMap(envToSecretMap)
                   .build());
      List<K8BuildJobEnvInfo.ConnectorConversionInfo> baseConnectorConversionInfo =
          this.getBaseImageConnectorConversionInfo(step, ambiance);
      map.get(stepElement.getIdentifier()).addAll(baseConnectorConversionInfo);
    }
    return map;
  }

  private List<K8BuildJobEnvInfo.ConnectorConversionInfo> getBaseImageConnectorConversionInfo(
      PluginCompatibleStep step, Ambiance ambiance) {
    List<String> baseConnectorRefs = PluginSettingUtils.getBaseImageConnectorRefs(step);
    List<K8BuildJobEnvInfo.ConnectorConversionInfo> baseImageConnectorConversionInfos = new ArrayList<>();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    if (!isEmpty(baseConnectorRefs)) {
      baseImageConnectorConversionInfos =
          baseConnectorRefs.stream()
              .map(baseConnectorRef -> {
                CIStepInfoType stepInfoType;
                // get connector details
                ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, baseConnectorRef);
                switch (connectorDetails.getConnectorType()) {
                  case DOCKER:
                    stepInfoType = CIStepInfoType.DOCKER;
                    break;
                  default:
                    throw new IllegalStateException(
                        "Unexpected base connector: " + connectorDetails.getConnectorType());
                }
                Map<EnvVariableEnum, String> envToSecretMap = PluginSettingUtils.getConnectorSecretEnvMap(stepInfoType);
                return K8BuildJobEnvInfo.ConnectorConversionInfo.builder()
                    .connectorRef(baseConnectorRef)
                    .envToSecretsMap(envToSecretMap)
                    .build();
              })
              .collect(Collectors.toList());
    }
    return baseImageConnectorConversionInfos;
  }

  private Map<String, String> getEnvVariables(StageElementConfig stageElementConfig) {
    if (isEmpty(stageElementConfig.getVariables())) {
      return Collections.emptyMap();
    }

    return stageElementConfig.getVariables()
        .stream()
        .filter(customVariables -> customVariables.getType() == NGVariableType.STRING)
        .map(customVariable -> (StringNGVariable) customVariable)
        .collect(Collectors.toMap(ngVariable
            -> ngVariable.getName(),
            ngVariable
            -> resolveStringParameterWithDefaultValue("variableValue", "stage", stageElementConfig.getIdentifier(),
                ngVariable.getValue(), false, ngVariable.getDefaultValue())));
  }

  private Integer getContainerMemoryLimit(
      ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer memoryLimit = ciExecutionConfigService.getCiExecutionServiceConfig().getDefaultMemoryLimit();

    if (featureFlagService.isEnabled(FeatureName.CI_INCREASE_DEFAULT_RESOURCES, accountID)) {
      log.info("Increase default resources FF is enabled for accountID: {}", accountID);
      memoryLimit = DEFAULT_CONTAINER_MEM_POV;
    }

    if (resource != null && resource.getLimits() != null && resource.getLimits().getMemory() != null) {
      String memoryLimitMemoryQuantity =
          resolveStringParameter("memory", stepType, stepId, resource.getLimits().getMemory(), false);
      if (isNotEmpty(memoryLimitMemoryQuantity) && !UNRESOLVED_PARAMETER.equals(memoryLimitMemoryQuantity)) {
        memoryLimit = QuantityUtils.getStorageQuantityValueInUnit(memoryLimitMemoryQuantity, StorageQuantityUnit.Mi);
      }
    }
    return memoryLimit;
  }

  private Integer getContainerCpuLimit(ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer cpuLimit = ciExecutionConfigService.getCiExecutionServiceConfig().getDefaultCPULimit();

    if (featureFlagService.isEnabled(FeatureName.CI_INCREASE_DEFAULT_RESOURCES, accountID)) {
      log.info("Increase default resources FF is enabled for accountID: {}", accountID);
      cpuLimit = DEFAULT_CONTAINER_CPU_POV;
    }

    if (resource != null && resource.getLimits() != null && resource.getLimits().getCpu() != null) {
      String cpuLimitQuantity = resolveStringParameter("cpu", stepType, stepId, resource.getLimits().getCpu(), false);
      if (isNotEmpty(cpuLimitQuantity) && !UNRESOLVED_PARAMETER.equals(cpuLimitQuantity)) {
        cpuLimit = QuantityUtils.getCpuQuantityValueInUnit(cpuLimitQuantity, DecimalQuantityUnit.m);
      }
    }
    return cpuLimit;
  }

  private Integer getStageMemoryRequest(List<ExecutionWrapperConfig> steps, String accountId) {
    Integer stageMemoryRequest = 0;
    for (ExecutionWrapperConfig step : steps) {
      Integer executionWrapperMemoryRequest = getExecutionWrapperMemoryRequest(step, accountId);
      stageMemoryRequest = Math.max(stageMemoryRequest, executionWrapperMemoryRequest);
    }
    return stageMemoryRequest;
  }

  private Integer getExecutionWrapperMemoryRequest(ExecutionWrapperConfig executionWrapper, String accountId) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperMemoryRequest = 0;
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
      executionWrapperMemoryRequest = getStepMemoryLimit(stepElementConfig, accountId);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallel = IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallel.getSections())) {
        for (ExecutionWrapperConfig wrapper : parallel.getSections()) {
          executionWrapperMemoryRequest += getExecutionWrapperMemoryRequest(wrapper, accountId);
        }
      }
    }
    return executionWrapperMemoryRequest;
  }

  private Integer getStepMemoryLimit(StepElementConfig stepElement, String accountId) {
    Integer zeroMemory = 0;
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return zeroMemory;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return getContainerMemoryLimit(
            ((RunStepInfo) ciStepInfo).getResources(), stepElement.getType(), stepElement.getIdentifier(), accountId);
      case PLUGIN:
        return getContainerMemoryLimit(((PluginStepInfo) ciStepInfo).getResources(), stepElement.getType(),
            stepElement.getIdentifier(), accountId);
      case RUN_TESTS:
        return getContainerMemoryLimit(((RunTestsStepInfo) ciStepInfo).getResources(), stepElement.getType(),
            stepElement.getIdentifier(), accountId);
      case GCR:
      case ECR:
      case DOCKER:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_GCS:
      case UPLOAD_S3:
      case RESTORE_CACHE_GCS:
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
      case SAVE_CACHE_GCS:
      case SECURITY:
        return getContainerMemoryLimit(((PluginCompatibleStep) ciStepInfo).getResources(), stepElement.getType(),
            stepElement.getIdentifier(), accountId);
      default:
        return zeroMemory;
    }
  }

  private Integer getStageCpuRequest(List<ExecutionWrapperConfig> steps, String accountId) {
    Integer stageCpuRequest = 0;
    for (ExecutionWrapperConfig step : steps) {
      Integer executionWrapperCpuRequest = getExecutionWrapperCpuRequest(step, accountId);
      stageCpuRequest = Math.max(stageCpuRequest, executionWrapperCpuRequest);
    }
    return stageCpuRequest;
  }

  private Integer getExecutionWrapperCpuRequest(ExecutionWrapperConfig executionWrapper, String accountId) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperCpuRequest = 0;
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
      executionWrapperCpuRequest = getStepCpuLimit(stepElementConfig, accountId);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallelStepElement =
          IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallelStepElement.getSections())) {
        for (ExecutionWrapperConfig wrapper : parallelStepElement.getSections()) {
          executionWrapperCpuRequest += getExecutionWrapperCpuRequest(wrapper, accountId);
        }
      }
    }
    return executionWrapperCpuRequest;
  }

  private Integer getStepCpuLimit(StepElementConfig stepElement, String accountId) {
    Integer zeroCpu = 0;
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return zeroCpu;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return getContainerCpuLimit(
            ((RunStepInfo) ciStepInfo).getResources(), stepElement.getType(), stepElement.getIdentifier(), accountId);
      case PLUGIN:
        return getContainerCpuLimit(((PluginStepInfo) ciStepInfo).getResources(), stepElement.getType(),
            stepElement.getIdentifier(), accountId);
      case RUN_TESTS:
        return getContainerCpuLimit(((RunTestsStepInfo) ciStepInfo).getResources(), stepElement.getType(),
            stepElement.getIdentifier(), accountId);
      case GCR:
      case ECR:
      case DOCKER:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_GCS:
      case UPLOAD_S3:
      case RESTORE_CACHE_GCS:
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
      case SAVE_CACHE_GCS:
      case SECURITY:
        return getContainerCpuLimit(((PluginCompatibleStep) ciStepInfo).getResources(), stepElement.getType(),
            stepElement.getIdentifier(), accountId);
      default:
        return zeroCpu;
    }
  }

  private List<PodVolume> convertVolumes(Infrastructure infrastructure) {
    List<PodVolume> podVolumes = new ArrayList<>();
    if (infrastructure.getType() != Infrastructure.Type.KUBERNETES_DIRECT) {
      return podVolumes;
    }

    if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }
    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) infrastructure;
    List<CIVolume> volumes = k8sDirectInfraYaml.getSpec().getVolumes().getValue();
    if (isEmpty(volumes)) {
      return podVolumes;
    }

    int index = 0;
    for (CIVolume volume : volumes) {
      String volumeName = format("%s%d", VOLUME_PREFIX, index);
      if (volume.getType() == CIVolume.Type.EMPTY_DIR) {
        podVolumes.add(convertEmptyDir(volumeName, (EmptyDirYaml) volume));
      } else if (volume.getType() == CIVolume.Type.HOST_PATH) {
        podVolumes.add(convertHostPath(volumeName, (HostPathYaml) volume));
      } else if (volume.getType() == CIVolume.Type.PERSISTENT_VOLUME_CLAIM) {
        podVolumes.add(convertPVCVolume(volumeName, (PersistentVolumeClaimYaml) volume));
      }

      index++;
    }
    return podVolumes;
  }

  private EmptyDirVolume convertEmptyDir(String volumeName, EmptyDirYaml emptyDirYaml) {
    EmptyDirVolumeBuilder emptyDirVolumeBuilder = EmptyDirVolume.builder()
                                                      .name(volumeName)
                                                      .mountPath(emptyDirYaml.getMountPath().getValue())
                                                      .medium(emptyDirYaml.getSpec().getMedium().getValue());
    String sizeStr = emptyDirYaml.getSpec().getSize().getValue();
    if (isNotEmpty(sizeStr)) {
      emptyDirVolumeBuilder.sizeMib(QuantityUtils.getStorageQuantityValueInUnit(sizeStr, StorageQuantityUnit.Mi));
    }
    return emptyDirVolumeBuilder.build();
  }

  private HostPathVolume convertHostPath(String volumeName, HostPathYaml hostPathYaml) {
    return HostPathVolume.builder()
        .name(volumeName)
        .mountPath(hostPathYaml.getMountPath().getValue())
        .path(hostPathYaml.getSpec().getPath().getValue())
        .hostPathType(hostPathYaml.getSpec().getType().getValue())
        .build();
  }

  private PVCVolume convertPVCVolume(String volumeName, PersistentVolumeClaimYaml pvcYaml) {
    return PVCVolume.builder()
        .name(volumeName)
        .mountPath(pvcYaml.getMountPath().getValue())
        .claimName(pvcYaml.getSpec().getClaimName().getValue())
        .build();
  }
}
