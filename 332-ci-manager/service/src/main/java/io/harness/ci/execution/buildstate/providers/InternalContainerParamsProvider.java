/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.buildstate.providers;

import static io.harness.ci.commonconstants.CIExecutionConstants.DELEGATE_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.DELEGATE_SERVICE_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.DELEGATE_SERVICE_ID_VARIABLE_VALUE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_LE_STATUS_REST_ENABLED;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_LOG_PREFIX_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_PIPELINE_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_STAGE_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_USER_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.ci.commonconstants.CIExecutionConstants.LITE_ENGINE_CONTAINER_CPU;
import static io.harness.ci.commonconstants.CIExecutionConstants.LITE_ENGINE_CONTAINER_MEM;
import static io.harness.ci.commonconstants.CIExecutionConstants.PWSH_COMMAND;
import static io.harness.ci.commonconstants.CIExecutionConstants.SETUP_ADDON_CONTAINER_NAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.SH_COMMAND;
import static io.harness.ci.commonconstants.CIExecutionConstants.UNIX_SETUP_ADDON_ARGS;
import static io.harness.ci.commonconstants.CIExecutionConstants.WIN_SETUP_ADDON_ARGS;
import static io.harness.ci.utils.UsageUtils.getExecutionUser;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.delegate.beans.ci.pod.CICommonConstants.LITE_ENGINE_CONTAINER_NAME;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.TEXT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.integrationstage.SecretEnvVars;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides container parameters for internally used containers
 */

@Singleton
@OwnedBy(HarnessTeam.CI)
public class InternalContainerParamsProvider {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject CIExecutionConfigService ciExecutionConfigService;
  @Inject private CIFeatureFlagService featureFlagService;

  public CIK8ContainerParams getSetupAddonContainerParams(ConnectorDetails harnessInternalImageConnector,
      Map<String, String> volumeToMountPath, String workDir, ContainerSecurityContext ctrSecurityContext,
      String accountIdentifier, OSType os) {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(HARNESS_WORKSPACE, workDir);

    String imageName = ciExecutionConfigService.getAddonImage(accountIdentifier);
    String fullyQualifiedImage =
        IntegrationStageUtils.getFullyQualifiedImageName(imageName, harnessInternalImageConnector);
    List<String> commands = SH_COMMAND;
    List<String> args = Arrays.asList(UNIX_SETUP_ADDON_ARGS);
    if (os == OSType.Windows) {
      commands = PWSH_COMMAND;
      args = Arrays.asList(WIN_SETUP_ADDON_ARGS);
    }
    return CIK8ContainerParams.builder()
        .name(SETUP_ADDON_CONTAINER_NAME)
        .envVars(envVars)
        .containerType(CIContainerType.ADD_ON)
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder()
                                       .imageDetails(IntegrationStageUtils.getImageInfo(fullyQualifiedImage))
                                       .imageConnectorDetails(harnessInternalImageConnector)
                                       .build())
        .containerSecrets(ContainerSecrets.builder().build())
        .volumeToMountPath(volumeToMountPath)
        .commands(commands)
        .args(args)
        .securityContext(ctrSecurityContext)
        .containerResourceParams(getAddonResourceParams())
        .build();
  }

  public CIK8ContainerParams getLiteEngineContainerParams(ConnectorDetails harnessInternalImageConnector,
      Map<String, ConnectorDetails> publishArtifactConnectors, K8PodDetails k8PodDetails, Integer stageCpuRequest,
      Integer stageMemoryRequest, Map<String, String> logEnvVars, Map<String, String> tiEnvVars,
      Map<String, String> stoEnvVars, Map<String, String> volumeToMountPath, String workDirPath,
      ContainerSecurityContext ctrSecurityContext, String logPrefix, Ambiance ambiance, SecretEnvVars secretEnvVars) {
    String imageName = ciExecutionConfigService.getLiteEngineImage(AmbianceUtils.getAccountId(ambiance));
    String fullyQualifiedImage =
        IntegrationStageUtils.getFullyQualifiedImageName(imageName, harnessInternalImageConnector);
    return CIK8ContainerParams.builder()
        .name(LITE_ENGINE_CONTAINER_NAME)
        .containerResourceParams(getLiteEngineResourceParams(stageCpuRequest, stageMemoryRequest))
        .envVars(getLiteEngineEnvVars(k8PodDetails, workDirPath, logPrefix, ambiance))
        .containerType(CIContainerType.LITE_ENGINE)
        .containerSecrets(
            ContainerSecrets.builder()
                .connectorDetailsMap(publishArtifactConnectors)
                .plainTextSecretsByName(getLiteEngineSecretVars(logEnvVars, tiEnvVars, stoEnvVars, secretEnvVars))
                .build())
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder()
                                       .imageDetails(IntegrationStageUtils.getImageInfo(fullyQualifiedImage))
                                       .imageConnectorDetails(harnessInternalImageConnector)
                                       .build())
        .volumeToMountPath(volumeToMountPath)
        .securityContext(ctrSecurityContext)
        .workingDir(workDirPath)
        .build();
  }

  private Map<String, String> getLiteEngineEnvVars(
      K8PodDetails k8PodDetails, String workDirPath, String logPrefix, Ambiance ambiance) {
    Map<String, String> envVars = new HashMap<>();
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String userID = getExecutionUser(ambiance.getMetadata().getPrincipalInfo());
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    final String stageID = k8PodDetails.getStageID();
    final String executionID = ambiance.getPlanExecutionId();

    // Check whether FF to enable blob upload to log service (as opposed to directly blob storage) is enabled
    if (featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, accountID)) {
      envVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    }
    // Check whether FF is enabled to send LE to manager status update via rest
    if (featureFlagService.isEnabled(FeatureName.CI_LE_STATUS_REST_ENABLED, accountID)) {
      envVars.put(HARNESS_LE_STATUS_REST_ENABLED, "true");
    }

    // Add environment variables that need to be used inside the lite engine container
    envVars.put(HARNESS_WORKSPACE, workDirPath);
    envVars.put(DELEGATE_SERVICE_ENDPOINT_VARIABLE, ciExecutionServiceConfig.getDelegateServiceEndpointVariableValue());
    envVars.put(DELEGATE_SERVICE_ID_VARIABLE, DELEGATE_SERVICE_ID_VARIABLE_VALUE);
    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, accountID);
    envVars.put(HARNESS_USER_ID_VARIABLE, userID);
    envVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    envVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    envVars.put(HARNESS_PIPELINE_ID_VARIABLE, pipelineID);
    envVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildNumber));
    envVars.put(HARNESS_STAGE_ID_VARIABLE, stageID);
    envVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    envVars.put(HARNESS_LOG_PREFIX_VARIABLE, logPrefix);
    return envVars;
  }

  public Map<String, SecretParams> getLiteEngineSecretVars(Map<String, String> logEnvVars,
      Map<String, String> tiEnvVars, Map<String, String> stoEnvVars, SecretEnvVars secretEnvVars) {
    Map<String, String> vars = new HashMap<>();
    vars.putAll(logEnvVars);
    vars.putAll(tiEnvVars);
    vars.putAll(stoEnvVars);
    if (secretEnvVars != null) {
      if (EmptyPredicate.isNotEmpty(secretEnvVars.getSscaEnvVars())) {
        vars.putAll(secretEnvVars.getSscaEnvVars());
      }
    }

    Map<String, SecretParams> secretVars = new HashMap<>();
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      secretVars.put(entry.getKey(),
          SecretParams.builder().secretKey(entry.getKey()).value(encodeBase64(entry.getValue())).type(TEXT).build());
    }
    return secretVars;
  }

  private ContainerResourceParams getLiteEngineResourceParams(Integer stageCpuRequest, Integer stageMemoryRequest) {
    Integer cpu = stageCpuRequest + LITE_ENGINE_CONTAINER_CPU;
    Integer memory = stageMemoryRequest + LITE_ENGINE_CONTAINER_MEM;
    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(cpu)
        .resourceRequestMemoryMiB(memory)
        .resourceLimitMilliCpu(cpu)
        .resourceLimitMemoryMiB(memory)
        .build();
  }

  private ContainerResourceParams getAddonResourceParams() {
    Integer cpu = LITE_ENGINE_CONTAINER_CPU;
    Integer memory = LITE_ENGINE_CONTAINER_MEM;
    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(cpu)
        .resourceRequestMemoryMiB(memory)
        .resourceLimitMilliCpu(cpu)
        .resourceLimitMemoryMiB(memory)
        .build();
  }
}
