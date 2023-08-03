/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveArchType;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveOSType;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STAGE_ARCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STAGE_MACHINE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STAGE_NAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STAGE_OS;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STAGE_TYPE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_WORKSPACE;
import static io.harness.ci.commonconstants.CIExecutionConstants.ACCOUNT_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.ADDON_VOLUME;
import static io.harness.ci.commonconstants.CIExecutionConstants.ADDON_VOL_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.BUILD_NUMBER_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_PIPELINE_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_STAGE_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_USER_ID_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.ORG_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.OSX_ADDON_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.OSX_STEP_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.PIPELINE_EXECUTION_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.PIPELINE_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.PROJECT_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.SHARED_VOLUME_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.STAGE_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.STAGE_RUNTIME_ID_ATTR;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_VOLUME;
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

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.BackgroundStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.stoserviceclient.STOServiceUtils;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class VmInitializeUtils {
  public void validateStageConfig(IntegrationStageConfig integrationStageConfig, String accountId) {
    for (ExecutionWrapperConfig executionWrapper : integrationStageConfig.getExecution().getSteps()) {
      validateStageConfigUtil(executionWrapper);
    }
  }

  private void validateStageConfigUtil(ExecutionWrapperConfig executionWrapper) {
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      CIAbstractStepNode stepNode = IntegrationStageUtils.getStepNode(executionWrapper);
      validateStepConfig(stepNode);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallelStepElementConfig =
          IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallelStepElementConfig.getSections())) {
        for (ExecutionWrapperConfig executionWrapperInParallel : parallelStepElementConfig.getSections()) {
          validateStageConfigUtil(executionWrapperInParallel);
        }
      }
    } else {
      StepGroupElementConfig stepGroupElementConfig = IntegrationStageUtils.getStepGroupElementConfig(executionWrapper);
      if (isNotEmpty(stepGroupElementConfig.getSteps())) {
        for (ExecutionWrapperConfig executionWrapperInStepGroup : stepGroupElementConfig.getSteps()) {
          validateStageConfigUtil(executionWrapperInStepGroup);
        }
      }
    }
  }

  private void validateStepConfig(CIAbstractStepNode stepElementConfig) {
    if (stepElementConfig.getStepSpecType() instanceof CIStepInfo) {
      CIStepInfo ciStepInfo = (CIStepInfo) stepElementConfig.getStepSpecType();
      switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
        case RUN:
          validateRunStepConnector((RunStepInfo) ciStepInfo);
          break;
        case BACKGROUND:
          validateBackgroundStepConnector((BackgroundStepInfo) ciStepInfo);
          break;
        case RUN_TESTS:
          validateRunTestsStepConnector((RunTestsStepInfo) ciStepInfo);
          break;
        case ECR:
          validatePluginStepConnector((PluginCompatibleStep) ciStepInfo);
          break;
        default:
          return;
      }
    }
  }

  private void validateRunTestsStepConnector(RunTestsStepInfo stepInfo) {
    if (stepInfo.getImage() != null && stepInfo.getConnectorRef() == null) {
      throw new CIStageExecutionException("connector ref can't be empty if image is provided");
    }
    if (stepInfo.getImage() == null && stepInfo.getConnectorRef() != null) {
      throw new CIStageExecutionException("image can't be empty if connector ref is provided");
    }
  }

  private void validateRunStepConnector(RunStepInfo runStepInfo) {
    if (runStepInfo.getImage() != null && runStepInfo.getConnectorRef() == null) {
      throw new CIStageExecutionException("connector ref can't be empty if image is provided");
    }
    if (runStepInfo.getImage() == null && runStepInfo.getConnectorRef() != null) {
      throw new CIStageExecutionException("image can't be empty if connector ref is provided");
    }
  }

  private void validateBackgroundStepConnector(BackgroundStepInfo backgroundStepInfo) {
    if (backgroundStepInfo.getImage() != null && backgroundStepInfo.getConnectorRef() == null) {
      throw new CIStageExecutionException("connector ref can't be empty if image is provided");
    }
    if (backgroundStepInfo.getImage() == null && backgroundStepInfo.getConnectorRef() != null) {
      throw new CIStageExecutionException("image can't be empty if connector ref is provided");
    }
  }

  private void validatePluginStepConnector(PluginCompatibleStep pluginStepInfo) {
    List<String> baseImageConnectorRefs = PluginSettingUtils.getBaseImageConnectorRefs(pluginStepInfo);
    if (baseImageConnectorRefs != null) {
      throw new CIStageExecutionException("Base image connector is not allowed for VM Infrastructure.");
    }
  }

  public Map<String, String> getSTOServiceEnvVariables(STOServiceUtils stoServiceUtils, String accountId) {
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

  public Map<String, String> getCommonStepEnvVariables(String stageID, Ambiance ambiance) {
    Map<String, String> envVars = new HashMap<>();
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String userID = getExecutionUser(ambiance.getMetadata().getPrincipalInfo());
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    final String executionID = ambiance.getPlanExecutionId();

    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, accountID);
    envVars.put(HARNESS_USER_ID_VARIABLE, userID);
    envVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    envVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    envVars.put(HARNESS_PIPELINE_ID_VARIABLE, pipelineID);
    envVars.put(PLUGIN_PIPELINE, pipelineID);
    envVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildNumber));
    envVars.put(HARNESS_STAGE_ID_VARIABLE, stageID);
    envVars.put(DRONE_STAGE_NAME, stageID);
    envVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    return envVars;
  }

  public Map<String, String> getCacheEnvironmentVariable() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(GOLANG_CACHE_ENV_NAME, GOLANG_CACHE_DIR);
    envVars.put(GRADLE_CACHE_ENV_NAME, GRADLE_CACHE_DIR);
    return envVars;
  }
  public Map<String, String> getStageEnvVars(
      ParameterField<Platform> platform, OSType os, String workDir, String poolID, Infrastructure infrastructure) {
    Map<String, String> envVars = new HashMap<>();

    if (platform != null && platform.getValue() != null && platform.getValue().getArch() != null) {
      ArchType arch = RunTimeInputHandler.resolveArchType(platform.getValue().getArch());
      envVars.put(DRONE_STAGE_ARCH, arch.toString());
    }
    envVars.put(DRONE_STAGE_OS, os.toString());
    envVars.put(DRONE_WORKSPACE, workDir);
    envVars.put(DRONE_STAGE_MACHINE, poolID);
    envVars.put(DRONE_STAGE_TYPE, infrastructure.getType().toString());

    return envVars;
  }

  public Map<String, String> getVolumeToMountPath(ParameterField<List<String>> parameterSharedPaths, OSType os) {
    Map<String, String> volumeToMountPath = new HashMap<>();
    String stepMountPath = getStepMountPath(os);
    String addonMountPath = getAddonMountPath(os);
    volumeToMountPath.put(STEP_VOLUME, stepMountPath);
    volumeToMountPath.put(ADDON_VOLUME, addonMountPath);

    if (parameterSharedPaths == null) {
      return volumeToMountPath;
    }

    List<String> sharedPaths = (List<String>) parameterSharedPaths.fetchFinalValue();
    if (isEmpty(sharedPaths)) {
      return volumeToMountPath;
    }

    int index = 0;
    for (String path : sharedPaths) {
      if (isEmpty(path)) {
        continue;
      }

      String volumeName = format("%s%d", SHARED_VOLUME_PREFIX, index);
      if (path.equals(STEP_MOUNT_PATH)) {
        throw new InvalidRequestException(format("Shared path: %s is a reserved keyword ", path));
      }
      volumeToMountPath.put(volumeName, path);
      index++;
    }
    return volumeToMountPath;
  }

  private String getStepMountPath(OSType os) {
    if (os.equals(OSType.MacOS)) {
      return OSX_STEP_MOUNT_PATH;
    }
    return STEP_MOUNT_PATH;
  }

  private String getAddonMountPath(OSType os) {
    if (os.equals(OSType.MacOS)) {
      return OSX_ADDON_MOUNT_PATH;
    }
    return ADDON_VOL_MOUNT_PATH;
  }

  public String getWorkDir(OSType os) {
    return getStepMountPath(os);
  }

  public static OSType getOS(Infrastructure infrastructure) {
    Infrastructure.Type infraType = infrastructure.getType();
    if (infraType == Infrastructure.Type.HOSTED_VM) {
      HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) infrastructure;
      ParameterField<Platform> platform = hostedVmInfraYaml.getSpec().getPlatform();
      if (platform != null && platform.getValue() != null) {
        return resolveOSType(platform.getValue().getOs());
      }
      return OSType.Linux;
    }

    if (infrastructure.getType() != Infrastructure.Type.VM) {
      throw new CIStageExecutionException(format("Invalid infrastructure type: %s", infrastructure.getType()));
    }

    VmInfraYaml vmInfraYaml = (VmInfraYaml) infrastructure;
    if (vmInfraYaml.getSpec() == null) {
      throw new CIStageExecutionException("Infrastructure spec should not be empty");
    }

    if (vmInfraYaml.getSpec().getType() != VmInfraSpec.Type.POOL) {
      throw new CIStageExecutionException(format("Invalid VM type: %s", vmInfraYaml.getSpec().getType()));
    }

    VmPoolYaml vmPoolYaml = (VmPoolYaml) vmInfraYaml.getSpec();
    return resolveOSType(vmPoolYaml.getSpec().getOs());
  }

  public static ArchType getArchType(Infrastructure infrastructure) {
    if (infrastructure.getType() == Infrastructure.Type.HOSTED_VM) {
      HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) infrastructure;
      ParameterField<Platform> platform = hostedVmInfraYaml.getSpec().getPlatform();
      if (platform != null && platform.getValue() != null) {
        return resolveArchType(platform.getValue().getArch());
      }
    }
    return ArchType.Amd64;
  }

  public Map<String, String> getBuildTags(Ambiance ambiance, StageDetails stageDetails) {
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final String pipelineExecutionID = ambiance.getPlanExecutionId();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    final String stageID = stageDetails.getStageID();
    final String stageRuntimeID = stageDetails.getStageRuntimeID();

    Map<String, String> tags = new HashMap<>();
    tags.put(ACCOUNT_ID_ATTR, accountID);
    tags.put(ORG_ID_ATTR, orgID);
    tags.put(PROJECT_ID_ATTR, projectID);
    tags.put(PIPELINE_ID_ATTR, pipelineID);
    tags.put(PIPELINE_EXECUTION_ID_ATTR, pipelineExecutionID);
    tags.put(STAGE_ID_ATTR, stageID);
    tags.put(STAGE_RUNTIME_ID_ATTR, stageRuntimeID);
    tags.put(BUILD_NUMBER_ATTR, String.valueOf(buildNumber));
    return tags;
  }

  public boolean validateDebug(Infrastructure infrastructure, Ambiance ambiance) {
    OSType os = OSType.Linux;

    try {
      if (ambiance.getMetadata().getIsDebug()) {
        if (infrastructure.getType() == Infrastructure.Type.VM) {
          VmInfraYaml vmInfraYaml = (VmInfraYaml) infrastructure;
          VmPoolYaml poolYaml = (VmPoolYaml) vmInfraYaml.getSpec();
          os = resolveOSType(poolYaml.getSpec().getOs());
        }

        if (infrastructure.getType() == Infrastructure.Type.HOSTED_VM) {
          HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) infrastructure;
          HostedVmInfraYaml.HostedVmInfraSpec spec = (HostedVmInfraYaml.HostedVmInfraSpec) hostedVmInfraYaml.getSpec();
          os = resolveOSType(spec.getPlatform().getValue().getOs());
        }
      }
    } catch (Exception e) {
      log.error("Error extracting OS type for validating Debug mode", e);
    }

    if (os != OSType.Linux) {
      throw new CIStageExecutionException(
          "Running the pipeline in debug mode is not supported for the selected Operating System:" + os.toString());
    }
    return true;
  }
}
