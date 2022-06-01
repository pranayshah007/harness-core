/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stateutils.buildstate.providers;

import static io.harness.common.CIExecutionConstants.DELEGATE_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.CIExecutionConstants.DELEGATE_SERVICE_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.DELEGATE_SERVICE_ID_VARIABLE_VALUE;
import static io.harness.common.CIExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.common.CIExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_LOG_PREFIX_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_PIPELINE_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_STAGE_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.common.CIExecutionConstants.LITE_ENGINE_CONTAINER_CPU;
import static io.harness.common.CIExecutionConstants.LITE_ENGINE_CONTAINER_MEM;
import static io.harness.common.CIExecutionConstants.LITE_ENGINE_CONTAINER_NAME;
import static io.harness.common.CIExecutionConstants.PWSH_COMMAND;
import static io.harness.common.CIExecutionConstants.SETUP_ADDON_CONTAINER_NAME;
import static io.harness.common.CIExecutionConstants.SH_COMMAND;
import static io.harness.common.CIExecutionConstants.UNIX_SETUP_ADDON_ARGS;
import static io.harness.common.CIExecutionConstants.WIN_SETUP_ADDON_ARGS;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.TEXT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.execution.CIExecutionConfigService;
import io.harness.ff.CIFeatureFlagService;
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
      ContainerSecurityContext ctrSecurityContext, String logPrefix, Ambiance ambiance) {
    String imageName = ciExecutionConfigService.getLiteEngineImage(AmbianceUtils.getAccountId(ambiance));
    String fullyQualifiedImage =
        IntegrationStageUtils.getFullyQualifiedImageName(imageName, harnessInternalImageConnector);
    return CIK8ContainerParams.builder()
        .name(LITE_ENGINE_CONTAINER_NAME)
        .containerResourceParams(getLiteEngineResourceParams(stageCpuRequest, stageMemoryRequest))
        .envVars(getLiteEngineEnvVars(k8PodDetails, workDirPath, logPrefix, ambiance))
        .containerType(CIContainerType.LITE_ENGINE)
        .containerSecrets(ContainerSecrets.builder()
                              .connectorDetailsMap(publishArtifactConnectors)
                              .plainTextSecretsByName(getLiteEngineSecretVars(logEnvVars, tiEnvVars, stoEnvVars))
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

    // Add environment variables that need to be used inside the lite engine container
    envVars.put(HARNESS_WORKSPACE, workDirPath);
    envVars.put(DELEGATE_SERVICE_ENDPOINT_VARIABLE, ciExecutionServiceConfig.getDelegateServiceEndpointVariableValue());
    envVars.put(DELEGATE_SERVICE_ID_VARIABLE, DELEGATE_SERVICE_ID_VARIABLE_VALUE);
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

  public Map<String, SecretParams> getLiteEngineSecretVars(
      Map<String, String> logEnvVars, Map<String, String> tiEnvVars, Map<String, String> stoEnvVars) {
    Map<String, String> vars = new HashMap<>();
    vars.putAll(logEnvVars);
    vars.putAll(tiEnvVars);
    vars.putAll(stoEnvVars);

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
