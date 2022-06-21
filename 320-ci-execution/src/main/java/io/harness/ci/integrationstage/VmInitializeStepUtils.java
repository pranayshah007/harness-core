/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveOSType;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.common.CIExecutionConstants.OSX_STEP_MOUNT_PATH;
import static io.harness.common.CIExecutionConstants.SHARED_VOLUME_PREFIX;
import static io.harness.common.CIExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.common.CIExecutionConstants.STEP_VOLUME;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.environment.BuildJobEnvInfo;
import io.harness.beans.environment.VmBuildJobInfo;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.ci.utils.ValidationUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ff.CIFeatureFlagService;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.stateutils.buildstate.PluginSettingUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
@Deprecated
public class VmInitializeStepUtils {
  @Inject CIFeatureFlagService featureFlagService;

  public BuildJobEnvInfo getInitializeStepInfoBuilder(StageElementConfig stageElementConfig,
      Infrastructure infrastructure, CIExecutionArgs ciExecutionArgs, List<ExecutionWrapperConfig> steps,
      Ambiance ambiance) {
    ArrayList<String> connectorIdentifiers = new ArrayList<>();
    for (ExecutionWrapperConfig executionWrapper : steps) {
      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
        validateStepConfig(stepElementConfig);
        String identifier = getConnectorIdentifier(stepElementConfig);
        if (identifier != null) {
          connectorIdentifiers.add(identifier);
        }
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig =
            IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
        if (isNotEmpty(parallelStepElementConfig.getSections())) {
          for (ExecutionWrapperConfig executionWrapperInParallel : parallelStepElementConfig.getSections()) {
            if (executionWrapperInParallel.getStep() == null || executionWrapperInParallel.getStep().isNull()) {
              continue;
            }
            StepElementConfig stepElementConfig =
                IntegrationStageUtils.getStepElementConfig(executionWrapperInParallel);
            validateStepConfig(stepElementConfig);
            String identifier = getConnectorIdentifier(stepElementConfig);
            if (identifier != null) {
              connectorIdentifiers.add(identifier);
            }
          }
        }
      }
    }
    IntegrationStageConfig integrationStageConfig = IntegrationStageUtils.getIntegrationStageConfig(stageElementConfig);
    validateStageConfig(integrationStageConfig, AmbianceUtils.getAccountId(ambiance));
    List<DependencyElement> serviceDependencies = null;
    if (integrationStageConfig.getServiceDependencies() != null
        && integrationStageConfig.getServiceDependencies().getValue() != null) {
      serviceDependencies = integrationStageConfig.getServiceDependencies().getValue();
      ValidationUtils.validateVmInfraDependencies(serviceDependencies);
    }

    OSType os = getOS(infrastructure);
    Map<String, String> volumeToMountPath = getVolumeToMountPath(integrationStageConfig.getSharedPaths(), os);
    return VmBuildJobInfo.builder()
        .ciExecutionArgs(ciExecutionArgs)
        .workDir(getStepMountPath(os))
        .connectorRefs(connectorIdentifiers)
        .stageVars(stageElementConfig.getVariables())
        .volToMountPath(volumeToMountPath)
        .serviceDependencies(serviceDependencies)
        .build();
  }

  private String getStepMountPath(OSType os) {
    if (os.equals(OSType.MacOS)) {
      return OSX_STEP_MOUNT_PATH;
    }
    return STEP_MOUNT_PATH;
  }

  private OSType getOS(Infrastructure infrastructure) {
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

  private void validateStageConfig(IntegrationStageConfig integrationStageConfig, String accountId) {
    if (!featureFlagService.isEnabled(FeatureName.CI_VM_INFRASTRUCTURE, accountId)) {
      throw new CIStageExecutionException("infrastructure VM is not allowed");
    }
  }

  private void validateStepConfig(StepElementConfig stepElementConfig) {
    if (stepElementConfig.getStepSpecType() instanceof CIStepInfo) {
      CIStepInfo ciStepInfo = (CIStepInfo) stepElementConfig.getStepSpecType();
      switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
        case RUN:
          validateRunStepConnector((RunStepInfo) ciStepInfo);
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

  private void validatePluginStepConnector(PluginCompatibleStep pluginStepInfo) {
    List<String> baseImageConnectorRefs = PluginSettingUtils.getBaseImageConnectorRefs(pluginStepInfo);
    if (baseImageConnectorRefs != null) {
      throw new CIStageExecutionException("Base image connector is not allowed for VM Infrastructure.");
    }
  }

  private String getConnectorIdentifier(StepElementConfig stepElementConfig) {
    if (stepElementConfig.getStepSpecType() instanceof CIStepInfo) {
      CIStepInfo ciStepInfo = (CIStepInfo) stepElementConfig.getStepSpecType();
      switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
        case RUN:
          return resolveConnectorIdentifier(((RunStepInfo) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        case PLUGIN:
          return resolveConnectorIdentifier(
              ((PluginStepInfo) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        case RUN_TESTS:
          return resolveConnectorIdentifier(
              ((RunTestsStepInfo) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
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
          return resolveConnectorIdentifier(
              ((PluginCompatibleStep) ciStepInfo).getConnectorRef(), ciStepInfo.getIdentifier());
        default:
          return null;
      }
    }
    return null;
  }

  private String resolveConnectorIdentifier(ParameterField<String> connectorRef, String stepIdentifier) {
    if (connectorRef != null) {
      String connectorIdentifier = resolveStringParameter("connectorRef", "Run", stepIdentifier, connectorRef, false);
      if (!StringUtils.isEmpty(connectorIdentifier)) {
        return connectorIdentifier;
      }
    }
    return null;
  }

  private Map<String, String> getVolumeToMountPath(ParameterField<List<String>> parameterSharedPaths, OSType os) {
    Map<String, String> volumeToMountPath = new HashMap<>();
    String stepMountPath = getStepMountPath(os);
    volumeToMountPath.put(STEP_VOLUME, stepMountPath);

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
}
