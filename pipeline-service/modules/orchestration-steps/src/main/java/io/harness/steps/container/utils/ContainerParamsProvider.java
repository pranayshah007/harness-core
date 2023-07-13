/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.DELEGATE_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.DELEGATE_SERVICE_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.DELEGATE_SERVICE_ID_VARIABLE_VALUE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_LE_STATUS_REST_ENABLED;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_LOG_PREFIX_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_PIPELINE_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_STAGE_ID_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_CONTAINER_CPU;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_CONTAINER_MEM;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PWSH_COMMAND;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.SETUP_ADDON_CONTAINER_NAME;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.SH_COMMAND;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.UNIX_SETUP_ADDON_ARGS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.WIN_SETUP_ADDON_ARGS;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.ci.pod.CICommonConstants.LITE_ENGINE_CONTAINER_NAME;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.TEXT;

import io.harness.beans.FeatureName;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.exception.WingsException;
import io.harness.k8s.model.ImageDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerDetailsSweepingOutput;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.utils.CiIntegrationStageUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ContainerParamsProvider {
  @Inject ContainerExecutionConfig containerExecutionConfig;
  @Inject PmsFeatureFlagHelper featureFlagHelper;

  public CIK8ContainerParams getLiteEngineContainerParams(ConnectorDetails harnessInternalImageConnector,
      ContainerDetailsSweepingOutput k8PodDetails, Integer stageCpuRequest, Integer stageMemoryRequest,
      Map<String, String> logEnvVars, Map<String, String> volumeToMountPath, String workDirPath,
      ContainerSecurityContext ctrSecurityContext, String logPrefix, Ambiance ambiance,
      CIExecutionImages overridenExecutionImages, String imagePullPolicy) {
    String imageName = overridenExecutionImages == null || isEmpty(overridenExecutionImages.getLiteEngineTag())
        ? containerExecutionConfig.getLiteEngineImage()
        : overridenExecutionImages.getLiteEngineTag();

    return CIK8ContainerParams.builder()
        .name(LITE_ENGINE_CONTAINER_NAME)
        .containerResourceParams(getLiteEngineResourceParams(stageCpuRequest, stageMemoryRequest))
        .envVars(getLiteEngineEnvVars(k8PodDetails, workDirPath, logPrefix, ambiance))
        .containerType(CIContainerType.LITE_ENGINE)
        .containerSecrets(
            ContainerSecrets.builder().plainTextSecretsByName(getLiteEngineSecretVars(logEnvVars)).build())
        .imageDetailsWithConnector(
            ImageDetailsWithConnector.builder()
                .imageDetails(ImageDetails.builder()
                                  .name(getFullyQualifiedImageName(imageName, harnessInternalImageConnector))
                                  .build())
                .imageConnectorDetails(harnessInternalImageConnector)
                .build())
        .volumeToMountPath(volumeToMountPath)
        .securityContext(ctrSecurityContext)
        .workingDir(workDirPath)
        .imagePullPolicy(imagePullPolicy)
        .build();
  }

  private ContainerResourceParams getLiteEngineResourceParams(Integer stepCpuRequest, Integer stepMemoryRequest) {
    Integer cpu = stepCpuRequest + LITE_ENGINE_CONTAINER_CPU;
    Integer memory = stepMemoryRequest + LITE_ENGINE_CONTAINER_MEM;
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

  private Map<String, String> getLiteEngineEnvVars(
      ContainerDetailsSweepingOutput k8PodDetails, String workDirPath, String logPrefix, Ambiance ambiance) {
    Map<String, String> envVars = new HashMap<>();
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    final String stageID = k8PodDetails.getStepIdentifier();
    final String executionID = ambiance.getPlanExecutionId();

    // Check whether FF to enable blob upload to log service (as opposed to directly blob storage) is enabled
    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_INDIRECT_LOG_UPLOAD)) {
      envVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    }
    //     Check whether FF is enabled to send LE to manager status update via rest
    if (featureFlagHelper.isEnabled(accountID, FeatureName.CI_LE_STATUS_REST_ENABLED)) {
      envVars.put(HARNESS_LE_STATUS_REST_ENABLED, "true");
    }

    // Add environment variables that need to be used inside the lite engine container
    envVars.put(HARNESS_WORKSPACE, workDirPath);
    envVars.put(DELEGATE_SERVICE_ENDPOINT_VARIABLE, containerExecutionConfig.getDelegateServiceEndpointVariableValue());
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

  public Map<String, SecretParams> getLiteEngineSecretVars(Map<String, String> logEnvVars) {
    Map<String, String> vars = new HashMap<>(logEnvVars);

    Map<String, SecretParams> secretVars = new HashMap<>();
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      secretVars.put(entry.getKey(),
          SecretParams.builder().secretKey(entry.getKey()).value(encodeBase64(entry.getValue())).type(TEXT).build());
    }
    return secretVars;
  }

  public CIK8ContainerParams getSetupAddonContainerParams(ConnectorDetails harnessInternalImageConnector,
      Map<String, String> volumeToMountPath, String workDir, ContainerSecurityContext ctrSecurityContext, OSType os,
      CIExecutionImages overridenExecutionImages, String imagePullPolicy) {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(HARNESS_WORKSPACE, workDir);

    final String imageName = overridenExecutionImages == null || isEmpty(overridenExecutionImages.getAddonTag())
        ? containerExecutionConfig.getAddonImage()
        : overridenExecutionImages.getAddonTag();
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
        .imageDetailsWithConnector(
            ImageDetailsWithConnector.builder()
                .imageDetails(ImageDetails.builder()
                                  .name(getFullyQualifiedImageName(imageName, harnessInternalImageConnector))
                                  .build())
                .imageConnectorDetails(harnessInternalImageConnector)
                .build())
        .containerSecrets(ContainerSecrets.builder().build())
        .volumeToMountPath(volumeToMountPath)
        .commands(commands)
        .args(args)
        .securityContext(ctrSecurityContext)
        .containerResourceParams(getAddonResourceParams())
        .imagePullPolicy(imagePullPolicy)
        .build();
  }

  public String getFullyQualifiedImageName(String imageName, ConnectorDetails connectorDetails) {
    try {
      return CiIntegrationStageUtils.getFullyQualifiedImageName(imageName, connectorDetails);
    } catch (WingsException ex) {
      log.error("Error while getting Fully qualified image", ex);
      throw new ContainerStepExecutionException(ex.getMessage());
    }
  }
}
