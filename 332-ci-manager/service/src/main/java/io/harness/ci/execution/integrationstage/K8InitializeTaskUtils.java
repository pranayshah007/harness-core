/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.FeatureName.CIE_ENABLED_RBAC;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveIntegerParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveOSType;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODE_BASE_CONNECTOR_REF;
import static io.harness.ci.commonconstants.CICommonPodConstants.POD_NAME_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.ACCOUNT_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.ADDON_VOLUME;
import static io.harness.ci.commonconstants.CIExecutionConstants.ADDON_VOL_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.BUILD_NUMBER_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_LOG_PREFIX_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_PIPELINE_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_STAGE_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_USER_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.ci.commonconstants.CIExecutionConstants.LABEL_REGEX;
import static io.harness.ci.commonconstants.CIExecutionConstants.LOG_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.LOG_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.ORG_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.PIPELINE_EXECUTION_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.PIPELINE_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.POD_MAX_WAIT_UNTIL_READY_SECS;
import static io.harness.ci.commonconstants.CIExecutionConstants.PROJECT_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.SHARED_VOLUME_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.STAGE_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.STAGE_NAME_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_VOLUME;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_WORK_DIR;
import static io.harness.ci.commonconstants.CIExecutionConstants.TI_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.TI_SERVICE_TOKEN_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.VOLUME_PREFIX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.GOLANG_CACHE_DIR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.GOLANG_CACHE_ENV_NAME;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.GRADLE_CACHE_DIR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.GRADLE_CACHE_ENV_NAME;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PLUGIN_PIPELINE;
import static io.harness.ci.utils.UsageUtils.getExecutionUser;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_TOKEN_VARIABLE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.Character.toLowerCase;
import static java.lang.String.format;
import static org.apache.commons.lang3.CharUtils.isAsciiAlphanumeric;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.quantity.unit.StorageQuantityUnit;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.CodeBaseConnectorRefSweepingOutput;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.k8.Capabilities;
import io.harness.beans.yaml.extended.infrastrucutre.k8.SecurityContext;
import io.harness.beans.yaml.extended.infrastrucutre.k8.Toleration;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.beans.yaml.extended.volumes.HostPathYaml;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.ci.utils.ExceptionUtility;
import io.harness.ci.utils.GithubApiFunctor;
import io.harness.ci.utils.GithubApiTokenEvaluator;
import io.harness.ci.utils.QuantityUtils;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerCapabilities;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.EmptyDirVolume;
import io.harness.delegate.beans.ci.pod.EmptyDirVolume.EmptyDirVolumeBuilder;
import io.harness.delegate.beans.ci.pod.HostPathVolume;
import io.harness.delegate.beans.ci.pod.PVCVolume;
import io.harness.delegate.beans.ci.pod.PodToleration;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class K8InitializeTaskUtils {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private CILogServiceUtils logServiceUtils;
  @Inject private TIServiceUtils tiServiceUtils;
  @Inject private STOServiceUtils stoServiceUtils;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject private SecretUtils secretUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;

  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;
  static final String SOURCE = "123456789bcdfghjklmnpqrstvwxyz";
  static final Integer RANDOM_LENGTH = 8;
  private static final SecureRandom random = new SecureRandom();

  public String generatePodName(String identifier) {
    return POD_NAME_PREFIX + "-" + getK8PodIdentifier(identifier) + "-"
        + generateRandomAlphaNumericString(RANDOM_LENGTH);
  }

  private String getK8PodIdentifier(String identifier) {
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

  private static String generateRandomAlphaNumericString(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(SOURCE.charAt(random.nextInt(SOURCE.length())));
    }
    return sb.toString();
  }

  public Map<String, String> getBuildLabels(Ambiance ambiance, K8PodDetails k8PodDetails) {
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final String pipelineExecutionID = ambiance.getPlanExecutionId();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    final String stageID = k8PodDetails.getStageID();
    final String stageName = k8PodDetails.getStageName();

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
    if (isLabelAllowed(stageID)) {
      labels.put(STAGE_ID_ATTR, stageID);
    }
    if (isLabelAllowed(stageName)) {
      labels.put(STAGE_NAME_ATTR, stageName);
    }
    if (isLabelAllowed(String.valueOf(buildNumber))) {
      labels.put(BUILD_NUMBER_ATTR, String.valueOf(buildNumber));
    }
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

  public OSType getOS(Infrastructure infrastructure) {
    if (infrastructure.getType() != Infrastructure.Type.KUBERNETES_DIRECT) {
      return OSType.Linux;
    }

    if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) infrastructure;
    return resolveOSType(k8sDirectInfraYaml.getSpec().getOs());
  }

  public List<String> getSharedPaths(InitializeStepInfo initializeStepInfo) {
    IntegrationStageConfig integrationStageConfig = initializeStepInfo.getStageElementConfig();
    String stageID = initializeStepInfo.getStageIdentifier();
    if (integrationStageConfig.getSharedPaths().isExpression()) {
      ExceptionUtility.throwUnresolvedExpressionException(integrationStageConfig.getSharedPaths().getExpressionValue(),
          "sharedPath", "stage with identifier: " + stageID);
    }
    return (List<String>) integrationStageConfig.getSharedPaths().fetchFinalValue();
  }

  public Map<String, String> getVolumeToMountPath(List<String> sharedPaths, List<PodVolume> volumes) {
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

  public List<PodVolume> convertDirectK8Volumes(K8sDirectInfraYaml k8sDirectInfraYaml) {
    List<PodVolume> podVolumes = new ArrayList<>();

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

  public List<PodToleration> getPodTolerations(ParameterField<List<Toleration>> parameterizedTolerations) {
    List<PodToleration> podTolerations = new ArrayList<>();
    List<Toleration> tolerations = RunTimeInputHandler.resolveTolerations(parameterizedTolerations);
    if (tolerations == null) {
      return podTolerations;
    }

    for (Toleration toleration : tolerations) {
      String effect = resolveStringParameter("effect", null, "infrastructure", toleration.getEffect(), false);
      String key = resolveStringParameter("key", null, "infrastructure", toleration.getKey(), false);
      String operator = resolveStringParameter("operator", null, "infrastructure", toleration.getOperator(), false);
      String value = resolveStringParameter("value", null, "infrastructure", toleration.getValue(), false);
      Integer tolerationSeconds = resolveIntegerParameter(toleration.getTolerationSeconds(), null);

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

  public int getPodWaitUntilReadTimeout(K8sDirectInfraYaml k8sDirectInfraYaml) {
    ParameterField<String> timeout = k8sDirectInfraYaml.getSpec().getInitTimeout();

    int podWaitUntilReadyTimeout = POD_MAX_WAIT_UNTIL_READY_SECS;
    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      long timeoutInMillis = Timeout.fromString((String) timeout.fetchFinalValue()).getTimeoutInMillis();
      podWaitUntilReadyTimeout = (int) (timeoutInMillis / 1000);
    }
    return podWaitUntilReadyTimeout;
  }

  public ContainerSecurityContext getCtrSecurityContext(Infrastructure infrastructure) {
    if (infrastructure.getType() != Infrastructure.Type.KUBERNETES_DIRECT) {
      return ContainerSecurityContext.builder().build();
    }

    OSType os = getOS(infrastructure);
    SecurityContext securityContext = null;
    if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT
        && ((K8sDirectInfraYaml) infrastructure).getSpec() != null) {
      securityContext = ((K8sDirectInfraYaml) infrastructure).getSpec().getContainerSecurityContext().getValue();
    }

    if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
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
        .runAsUser(resolveIntegerParameter(securityContext.getRunAsUser(), null))
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

  public Map<String, ConnectorDetails> resolveGitAppFunctor(
      NGAccess ngAccess, InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    String codeBaseConnectorRef = null;
    if (initializeStepInfo.getCiCodebase() != null
        && initializeStepInfo.getCiCodebase().getConnectorRef().getValue() != null) {
      codeBaseConnectorRef = initializeStepInfo.getCiCodebase().getConnectorRef().getValue();
      if (isNotEmpty(codeBaseConnectorRef)) {
        consumeSweepingOutput(ambiance,
            CodeBaseConnectorRefSweepingOutput.builder().codeBaseConnectorRef(codeBaseConnectorRef).build(),
            CODE_BASE_CONNECTOR_REF);
      }
    }
    GithubApiTokenEvaluator githubApiTokenEvaluator =
        GithubApiTokenEvaluator.builder()
            .githubApiFunctorConfig(GithubApiFunctor.Config.builder()
                                        .codeBaseConnectorRef(codeBaseConnectorRef)
                                        .fetchConnector(true)
                                        .build())
            .connectorUtils(connectorUtils)
            .build();

    return githubApiTokenEvaluator.resolve(initializeStepInfo, ngAccess, ambiance.getExpressionFunctorToken());
  }

  public <T extends ExecutionSweepingOutput> void consumeSweepingOutput(Ambiance ambiance, T value, String key) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(key));
    if (!optionalSweepingOutput.isFound()) {
      executionSweepingOutputResolver.consume(ambiance, key, value, StepOutcomeGroup.STAGE.name());
    }
  }

  @NotNull
  public Map<String, String> getLogServiceEnvVariables(K8PodDetails k8PodDetails, String accountID) {
    Map<String, String> envVars = new HashMap<>();
    final String logServiceBaseUrl = logServiceUtils.getLogServiceConfig().getBaseUrl();

    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to fetch log service token attempt: {}"),
            format("Failed to fetch log service token after retrying {} times"));

    // Make a call to the log service and get back the token
    String logServiceToken =
        Failsafe.with(retryPolicy).get(() -> { return logServiceUtils.getLogServiceToken(accountID); });

    envVars.put(LOG_SERVICE_TOKEN_VARIABLE, logServiceToken);
    envVars.put(LOG_SERVICE_ENDPOINT_VARIABLE, logServiceBaseUrl);

    return envVars;
  }

  @NotNull
  public Map<String, String> getTIServiceEnvVariables(String accountId) {
    Map<String, String> envVars = new HashMap<>();
    final String tiServiceBaseUrl = tiServiceUtils.getTiServiceConfig().getBaseUrl();

    String tiServiceToken = "token";

    // Make a call to the TI service and get back the token. We do not need TI service token for all steps,
    // so we can continue even if the service is down.
    // TODO: (vistaar) Get token only when TI service interaction is required.
    try {
      tiServiceToken = tiServiceUtils.getTIServiceToken(accountId);
    } catch (Exception e) {
      log.error("Could not call token endpoint for TI service", e);
    }

    envVars.put(TI_SERVICE_TOKEN_VARIABLE, tiServiceToken);
    envVars.put(TI_SERVICE_ENDPOINT_VARIABLE, tiServiceBaseUrl);

    return envVars;
  }

  @NotNull
  public Map<String, String> getSTOServiceEnvVariables(String accountId) {
    Map<String, String> envVars = new HashMap<>();
    final String stoServiceBaseUrl = stoServiceUtils.getStoServiceConfig().getBaseUrl();

    String stoServiceToken = "token";

    // Make a call to the STO service and get back the token.
    try {
      stoServiceToken = stoServiceUtils.getSTOServiceToken(accountId);
    } catch (Exception e) {
      log.error("Could not call token endpoint for STO service", e);
    }

    envVars.put(STO_SERVICE_TOKEN_VARIABLE, stoServiceToken);
    envVars.put(STO_SERVICE_ENDPOINT_VARIABLE, stoServiceBaseUrl);

    return envVars;
  }

  @NotNull
  public Map<String, String> getCommonStepEnvVariables(K8PodDetails k8PodDetails, Map<String, String> gitEnvVars,
      Map<String, String> runtimeCodebaseVars, String workDirPath, String logPrefix, Ambiance ambiance) {
    Map<String, String> envVars = new HashMap<>();
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String userID = getExecutionUser(ambiance.getMetadata().getPrincipalInfo());
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    final String stageID = k8PodDetails.getStageID();
    final String executionID = ambiance.getPlanExecutionId();

    // Add git connector environment variables
    envVars.putAll(gitEnvVars);

    // Add runtime git vars, i.e. manual pull request execution data.
    envVars.putAll(runtimeCodebaseVars);

    // Check whether FF to enable blob upload to log service (as opposed to directly blob storage) is enabled
    if (featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, accountID)) {
      log.info("Indirect log upload FF is enabled for accountID: {}", accountID);
      envVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    }

    // Add other environment variables needed in the containers
    envVars.put(HARNESS_WORKSPACE, workDirPath);
    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, accountID);
    envVars.put(HARNESS_USER_ID_VARIABLE, userID);
    envVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    envVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    envVars.put(HARNESS_PIPELINE_ID_VARIABLE, pipelineID);
    envVars.put(PLUGIN_PIPELINE, pipelineID);
    envVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildNumber));
    envVars.put(HARNESS_STAGE_ID_VARIABLE, stageID);
    envVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    envVars.put(HARNESS_LOG_PREFIX_VARIABLE, logPrefix);
    return envVars;
  }

  public Map<String, String> getCacheEnvironmentVariable() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(GOLANG_CACHE_ENV_NAME, GOLANG_CACHE_DIR);
    envVars.put(GRADLE_CACHE_ENV_NAME, GRADLE_CACHE_DIR);
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

  @NotNull
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
              if (secretVariableDetail == null) {
                return null;
              }
              return createEntityDetails(secretVariableDetail.getSecretVariableDTO().getSecret().getIdentifier(),
                  accountIdentifier, projectIdentifier, orgIdentifier);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    if (isNotEmpty(entityDetails) && featureFlagService.isEnabled(CIE_ENABLED_RBAC, accountIdentifier)) {
      pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, false);
    }
  }

  private EntityDetail createEntityDetails(
      String secretIdentifier, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(secretIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    return EntityDetail.builder().entityRef(connectorRef).type(EntityType.SECRETS).build();
  }
}
