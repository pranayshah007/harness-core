/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.ACCOUNT_ID_ATTR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.ADDON_VOLUME;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.ADDON_VOL_MOUNT_PATH;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_LOG_PREFIX_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_PIPELINE_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_STAGE_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.IMAGE_PATH_SPLIT_REGEX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LABEL_REGEX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LOG_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LOG_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.ORG_ID_ATTR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PIPELINE_EXECUTION_ID_ATTR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PIPELINE_ID_ATTR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.POD_MAX_WAIT_UNTIL_READY_SECS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PROJECT_ID_ATTR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_VOLUME;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_WORK_DIR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.VOLUME_PREFIX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.steps.container.constants.ContainerStepExecutionConstants.POD_NAME_PREFIX;
import static io.harness.steps.container.constants.ContainerStepExecutionConstants.STEP_ID_ATTR;
import static io.harness.steps.container.utils.ContainerStepResolverUtils.resolveOSType;
import static io.harness.steps.plugin.infrastructure.ContainerStepInfra.Type.KUBERNETES_DIRECT;

import static java.lang.Character.toLowerCase;
import static java.lang.String.format;
import static org.apache.commons.lang3.CharUtils.isAsciiAlphanumeric;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.quantity.unit.DecimalQuantityUnit;
import io.harness.beans.quantity.unit.StorageQuantityUnit;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.k8.Capabilities;
import io.harness.beans.yaml.extended.infrastrucutre.k8.SecurityContext;
import io.harness.beans.yaml.extended.infrastrucutre.k8.Toleration;
import io.harness.ci.utils.QuantityUtils;
import io.harness.delegate.beans.ci.pod.ContainerCapabilities;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.EmptyDirVolume;
import io.harness.delegate.beans.ci.pod.EmptyDirVolume.EmptyDirVolumeBuilder;
import io.harness.delegate.beans.ci.pod.HostPathVolume;
import io.harness.delegate.beans.ci.pod.PVCVolume;
import io.harness.delegate.beans.ci.pod.PodToleration;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.exception.GeneralException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.k8s.model.ImageDetails;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.ExpressionResolverUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerDetailsSweepingOutput;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.plugin.IContainerStepSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.steps.plugin.infrastructure.volumes.ContainerVolume;
import io.harness.steps.plugin.infrastructure.volumes.EmptyDirYaml;
import io.harness.steps.plugin.infrastructure.volumes.HostPathYaml;
import io.harness.steps.plugin.infrastructure.volumes.PersistentVolumeClaimYaml;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class K8sPodInitUtils {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private SecretUtils secretUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;

  @Inject private PmsFeatureFlagHelper featureFlagHelper;
  @Inject private LogStreamingServiceConfiguration logStreamingServiceConfiguration;
  @Inject ContainerExecutionConfig containerExecutionConfig;
  @Inject LogStreamingStepClientFactory logStreamingStepClientFactory;

  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;
  static final String SOURCE = "123456789bcdfghjklmnpqrstvwxyz";
  static final Integer RANDOM_LENGTH = 8;
  private static final SecureRandom random = new SecureRandom();
  public static String UNRESOLVED_PARAMETER = "UNRESOLVED_PARAMETER";

  public String generatePodName(String identifier) {
    return POD_NAME_PREFIX + "-" + getK8PodIdentifier(identifier) + "-"
        + generateRandomAlphaNumericString(RANDOM_LENGTH);
  }

  private String getK8PodIdentifier(String identifier) {
    StringBuilder sb = new StringBuilder(15);
    for (char c : identifier.toCharArray()) {
      if (c == '_') {
        continue;
      }
      if (isAsciiAlphanumeric(c)) {
        sb.append(toLowerCase(c));
      }
      if (sb.length() == 15) {
        return sb.toString();
      }
    }
    return sb.toString();
  }

  private static String generateRandomAlphaNumericString(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(SOURCE.charAt(random.nextInt(SOURCE.length())));
    }
    return sb.toString();
  }

  public Map<String, String> getLabels(Ambiance ambiance, String stepId) {
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final String pipelineExecutionID = ambiance.getPlanExecutionId();

    Map<String, String> labels = new HashMap<>();
    if (isLabelAllowed(accountID)) {
      labels.put(ACCOUNT_ID_ATTR, accountID);
    }
    if (isLabelAllowed(orgID)) {
      labels.put(ORG_ID_ATTR, orgID);
    }
    if (isLabelAllowed(projectID)) {
      labels.put(PROJECT_ID_ATTR, projectID);
    }
    if (isLabelAllowed(pipelineID)) {
      labels.put(PIPELINE_ID_ATTR, pipelineID);
    }
    if (isLabelAllowed(pipelineExecutionID)) {
      labels.put(PIPELINE_EXECUTION_ID_ATTR, pipelineExecutionID);
    }
    if (isLabelAllowed(stepId)) {
      labels.put(STEP_ID_ATTR, stepId);
    }
    // todo(abhinav): check if anything else needed here
    return labels;
  }

  private boolean isLabelAllowed(String label) {
    if (label == null) {
      return false;
    }

    return label.matches(LABEL_REGEX);
  }

  public String getWorkDir() {
    return STEP_WORK_DIR;
  }

  public OSType getOS(ContainerStepInfra infrastructure) {
    if (infrastructure.getType() != KUBERNETES_DIRECT) {
      return OSType.Linux;
    }

    if (((ContainerK8sInfra) infrastructure).getSpec() == null) {
      throw new ContainerStepExecutionException("Input infrastructure can not be empty");
    }

    ContainerK8sInfra k8sDirectInfraYaml = (ContainerK8sInfra) infrastructure;
    return resolveOSType(k8sDirectInfraYaml.getSpec().getOs());
  }

  public Map<String, String> getVolumeToMountPath(List<PodVolume> volumes) {
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(STEP_VOLUME, STEP_MOUNT_PATH);
    volumeToMountPath.put(ADDON_VOLUME, ADDON_VOL_MOUNT_PATH);

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

  public List<PodVolume> convertDirectK8Volumes(ContainerK8sInfra k8sDirectInfraYaml) {
    List<PodVolume> podVolumes = new ArrayList<>();

    List<ContainerVolume> volumes = k8sDirectInfraYaml.getSpec().getVolumes().getValue();
    if (isEmpty(volumes)) {
      return podVolumes;
    }

    int index = 0;
    for (ContainerVolume volume : volumes) {
      String volumeName = format("%s%d", VOLUME_PREFIX, index);
      if (volume.getType() == ContainerVolume.Type.EMPTY_DIR) {
        podVolumes.add(convertEmptyDir(volumeName, (EmptyDirYaml) volume));
      } else if (volume.getType() == ContainerVolume.Type.HOST_PATH) {
        podVolumes.add(convertHostPath(volumeName, (HostPathYaml) volume));
      } else if (volume.getType() == ContainerVolume.Type.PERSISTENT_VOLUME_CLAIM) {
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

  private List<Toleration> resolveTolerations(ParameterField<List<Toleration>> tolerations) {
    if (tolerations == null || tolerations.isExpression() || tolerations.getValue() == null) {
      return null;
    } else {
      return tolerations.getValue();
    }
  }

  public List<PodToleration> getPodTolerations(ParameterField<List<Toleration>> parameterizedTolerations) {
    List<PodToleration> podTolerations = new ArrayList<>();
    List<Toleration> tolerations = resolveTolerations(parameterizedTolerations);
    if (tolerations == null) {
      return podTolerations;
    }

    for (Toleration toleration : tolerations) {
      String effect = ExpressionResolverUtils.resolveStringParameter(
          "effect", null, "infrastructure", toleration.getEffect(), false);
      String key =
          ExpressionResolverUtils.resolveStringParameter("key", null, "infrastructure", toleration.getKey(), false);
      String operator = ExpressionResolverUtils.resolveStringParameter(
          "operator", null, "infrastructure", toleration.getOperator(), false);
      String value =
          ExpressionResolverUtils.resolveStringParameter("value", null, "infrastructure", toleration.getValue(), false);
      Integer tolerationSeconds =
          ExpressionResolverUtils.resolveIntegerParameter(toleration.getTolerationSeconds(), null);

      validateTolerationEffect(effect);
      validateTolerationOperator(operator);

      podTolerations.add(PodToleration.builder()
                             .effect(effect)
                             .key(key)
                             .operator(operator)
                             .value(value)
                             .tolerationSeconds(tolerationSeconds)
                             .build());
    }
    return podTolerations;
  }

  private void validateTolerationEffect(String effect) {
    if (isNotEmpty(effect)) {
      if (!effect.equals("NoSchedule") && !effect.equals("PreferNoSchedule") && !effect.equals("NoExecute")) {
        throw new CIStageExecutionException(format("Invalid value %s for effect in toleration", effect));
      }
    }
  }

  private void validateTolerationOperator(String operator) {
    if (isNotEmpty(operator)) {
      if (!operator.equals("Equal") && !operator.equals("Exists")) {
        throw new CIStageExecutionException(format("Invalid value %s for operator in toleration", operator));
      }
    }
  }

  public int getPodWaitUntilReadTimeout(ContainerK8sInfra k8sDirectInfraYaml) {
    ParameterField<String> timeout = k8sDirectInfraYaml.getSpec().getInitTimeout();

    int podWaitUntilReadyTimeout = POD_MAX_WAIT_UNTIL_READY_SECS;
    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      long timeoutInMillis = Timeout.fromString((String) timeout.fetchFinalValue()).getTimeoutInMillis();
      podWaitUntilReadyTimeout = (int) (timeoutInMillis / 1000);
    }
    return podWaitUntilReadyTimeout;
  }

  public ContainerSecurityContext getCtrSecurityContext(ContainerK8sInfra infrastructure) {
    OSType os = getOS(infrastructure);
    SecurityContext securityContext = infrastructure.getSpec().getContainerSecurityContext().getValue();

    if (infrastructure.getSpec() == null) {
      throw new ContainerStepExecutionException("Input infrastructure can not be empty");
    }

    if (securityContext == null || os == OSType.Windows) {
      return ContainerSecurityContext.builder().build();
    }
    return ContainerSecurityContext.builder()
        .allowPrivilegeEscalation(securityContext.getAllowPrivilegeEscalation().getValue())
        .privileged(securityContext.getPrivileged().getValue())
        .procMount(securityContext.getProcMount().getValue())
        .readOnlyRootFilesystem(securityContext.getReadOnlyRootFilesystem().getValue())
        .runAsNonRoot(securityContext.getRunAsNonRoot().getValue())
        .runAsGroup(securityContext.getRunAsGroup().getValue())
        .runAsUser(ExpressionResolverUtils.resolveIntegerParameter(securityContext.getRunAsUser(), null))
        .capabilities(getCtrCapabilities(securityContext.getCapabilities().getValue()))
        .build();
  }

  private ContainerCapabilities getCtrCapabilities(Capabilities capabilities) {
    if (capabilities == null) {
      return ContainerCapabilities.builder().build();
    }

    return ContainerCapabilities.builder()
        .add(capabilities.getAdd().getValue())
        .drop(capabilities.getDrop().getValue())
        .build();
  }

  public <T extends ExecutionSweepingOutput> void consumeSweepingOutput(Ambiance ambiance, T value, String key) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(key));
    if (!optionalSweepingOutput.isFound()) {
      executionSweepingOutputResolver.consume(ambiance, key, value, StepCategory.STEP_GROUP.name());
    }
  }

  public Map<String, String> getLogServiceEnvVariables(ContainerDetailsSweepingOutput k8PodDetails, String accountID) {
    Map<String, String> envVars = new HashMap<>();
    final String logServiceBaseUrl = containerExecutionConfig.getLogStreamingContainerStepBaseUrl();
    log.info("log base url {}", logServiceBaseUrl);
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to fetch log service token attempt: {}"),
            format("Failed to fetch log service token after retrying {} times"));

    // Make a call to the log service and get back the token
    String logServiceToken = Failsafe.with(retryPolicy)
                                 .get(()
                                          -> getLogServiceToken(accountID, logServiceBaseUrl,
                                              logStreamingServiceConfiguration.getServiceToken()));

    envVars.put(LOG_SERVICE_TOKEN_VARIABLE, logServiceToken);
    envVars.put(LOG_SERVICE_ENDPOINT_VARIABLE, logServiceBaseUrl);

    return envVars;
  }

  public String getLogServiceToken(String accountID, String url, String token) {
    log.info("Initiating token request to log service: {}", url);
    try {
      return logStreamingStepClientFactory.retrieveLogStreamingAccountToken(accountID);
    } catch (IOException e) {
      throw new GeneralException("Token request to log service call failed", e);
    }
  }

  public Map<String, String> getCommonStepEnvVariables(
      ContainerDetailsSweepingOutput k8PodDetails, String workDirPath, String logPrefix, Ambiance ambiance) {
    Map<String, String> envVars = new HashMap<>();
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    final String stageID = ambiance.getStageExecutionId();
    final String executionID = ambiance.getPlanExecutionId();

    // Check whether FF to enable blob upload to log service (as opposed to directly blob storage) is enabled
    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_INDIRECT_LOG_UPLOAD)) {
      log.info("Indirect log upload FF is enabled for accountID: {}", accountID);
      envVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    }

    // Add other environment variables needed in the containers
    envVars.put(HARNESS_WORKSPACE, workDirPath);
    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, accountID);
    envVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    envVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    envVars.put(HARNESS_PIPELINE_ID_VARIABLE, pipelineID);
    envVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildNumber));
    envVars.put(HARNESS_STAGE_ID_VARIABLE, stageID);
    envVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    envVars.put(HARNESS_LOG_PREFIX_VARIABLE, logPrefix);
    return envVars;
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  public List<SecretVariableDetails> getSecretVariableDetails(NGAccess ngAccess,
      ContainerDefinitionInfo containerDefinitionInfo, List<SecretVariableDetails> scriptsSecretVariableDetails) {
    List<SecretVariableDetails> secretVariableDetails = new ArrayList<>();
    secretVariableDetails.addAll(scriptsSecretVariableDetails);
    if (isNotEmpty(containerDefinitionInfo.getSecretVariables())) {
      containerDefinitionInfo.getSecretVariables().forEach(
          secretVariable -> secretVariableDetails.add(secretUtils.getSecretVariableDetails(ngAccess, secretVariable)));
    }
    return secretVariableDetails.stream().filter(Objects::nonNull).collect(Collectors.toList());
  }

  public Map<String, String> removeEnvVarsWithSecretRef(Map<String, String> envVars) {
    HashMap<String, String> hashMap = new HashMap<>();
    final Map<String, String> secretEnvVariables =
        envVars.entrySet()
            .stream()
            .filter(entry -> entry.getValue().contains("ngSecretManager"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    envVars.entrySet().removeAll(secretEnvVariables.entrySet());

    return secretEnvVariables;
  }

  public void checkSecretAccess(Ambiance ambiance, List<SecretVariableDetails> secretVariableDetails,
      String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    List<EntityDetail> entityDetails =
        secretVariableDetails.stream()
            .map(secretVariableDetail -> {
              return createEntityDetails(secretVariableDetail.getSecretVariableDTO().getSecret().getIdentifier(),
                  accountIdentifier, projectIdentifier, orgIdentifier);
            })
            .collect(Collectors.toList());

    if (isNotEmpty(entityDetails)) {
      pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, false);
    }
  }

  private EntityDetail createEntityDetails(
      String secretIdentifier, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(secretIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    return EntityDetail.builder().entityRef(connectorRef).type(EntityType.SECRETS).build();
  }

  public Pair<Integer, Integer> getStepRequest(IContainerStepSpec containerStepInfo, String accountId) {
    ContainerResource resources = ((ContainerK8sInfra) containerStepInfo.getInfrastructure()).getSpec().getResources();
    Integer containerCpuLimit =
        getContainerCpuLimit(resources, "Container", containerStepInfo.getIdentifier(), accountId);
    Integer containerMemoryLimit =
        getContainerMemoryLimit(resources, "Container", containerStepInfo.getIdentifier(), accountId);

    return Pair.of(containerCpuLimit, containerMemoryLimit);
  }

  private Integer getContainerCpuLimit(ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer cpuLimit = null;

    if (resource != null && resource.getLimits() != null && resource.getLimits().getCpu() != null) {
      String cpuLimitQuantity =
          ExpressionResolverUtils.resolveStringParameter("cpu", stepType, stepId, resource.getLimits().getCpu(), false);
      if (isNotEmpty(cpuLimitQuantity) && !UNRESOLVED_PARAMETER.equals(cpuLimitQuantity)) {
        cpuLimit = QuantityUtils.getCpuQuantityValueInUnit(cpuLimitQuantity, DecimalQuantityUnit.m);
      }
    }
    return cpuLimit;
  }

  private Integer getContainerMemoryLimit(
      ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer memoryLimit = 0;
    if (resource != null && resource.getLimits() != null && resource.getLimits().getMemory() != null) {
      String memoryLimitMemoryQuantity = ExpressionResolverUtils.resolveStringParameter(
          "memory", stepType, stepId, resource.getLimits().getMemory(), false);
      if (isNotEmpty(memoryLimitMemoryQuantity) && !UNRESOLVED_PARAMETER.equals(memoryLimitMemoryQuantity)) {
        memoryLimit = QuantityUtils.getStorageQuantityValueInUnit(memoryLimitMemoryQuantity, StorageQuantityUnit.Mi);
      }
    }
    return memoryLimit;
  }

  public ImageDetails getImageInfo(String image) {
    String tag = "";
    String name = image;

    if (image.contains(IMAGE_PATH_SPLIT_REGEX)) {
      String[] subTokens = image.split(IMAGE_PATH_SPLIT_REGEX);
      if (subTokens.length > 1) {
        tag = subTokens[subTokens.length - 1];
        String[] nameparts = Arrays.copyOf(subTokens, subTokens.length - 1);
        name = String.join(IMAGE_PATH_SPLIT_REGEX, nameparts);
      }
    }

    return ImageDetails.builder().name(name).tag(tag).build();
  }
}
