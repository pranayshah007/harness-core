/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIRegistry;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.ActionStepInfo;
import io.harness.beans.steps.stepinfo.BackgroundStepInfo;
import io.harness.beans.steps.stepinfo.BitriseStepInfo;
import io.harness.beans.steps.stepinfo.IACMTerraformPlanInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.utils.CIVmSecretEvaluator;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class VmStepSerializer {
  @Inject VmPluginCompatibleStepSerializer vmPluginCompatibleStepSerializer;
  @Inject VmPluginStepSerializer vmPluginStepSerializer;
  @Inject VmRunStepSerializer vmRunStepSerializer;
  @Inject VmRunTestStepSerializer vmRunTestStepSerializer;
  @Inject VmBackgroundStepSerializer vmBackgroundStepSerializer;
  @Inject VmActionStepSerializer vmActionStepSerializer;
  @Inject VmBitriseStepSerializer vmBitriseStepSerializer;
  @Inject VmIACMStepSerializer vmIACMPluginCompatibleStepSerializer;

  public Set<String> getStepSecrets(VmStepInfo vmStepInfo, Ambiance ambiance) {
    CIVmSecretEvaluator ciVmSecretEvaluator = CIVmSecretEvaluator.builder().build();
    return ciVmSecretEvaluator.resolve(
        vmStepInfo, AmbianceUtils.getNgAccess(ambiance), ambiance.getExpressionFunctorToken());
  }

  public VmStepInfo serialize(Ambiance ambiance, CIStepInfo stepInfo, StageInfraDetails stageInfraDetails,
      String identifier, ParameterField<Timeout> parameterFieldTimeout, List<CIRegistry> registries,
      ExecutionSource executionSource) {
    String stepName = stepInfo.getNonYamlInfo().getStepInfoType().getDisplayName();
    switch (stepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return vmRunStepSerializer.serialize(
            (RunStepInfo) stepInfo, ambiance, identifier, parameterFieldTimeout, stepName, registries);
      case BACKGROUND:
        return vmBackgroundStepSerializer.serialize((BackgroundStepInfo) stepInfo, ambiance, identifier, registries);
      case RUN_TESTS:
        return vmRunTestStepSerializer.serialize(
            (RunTestsStepInfo) stepInfo, identifier, parameterFieldTimeout, stepName, ambiance, registries);
      case PLUGIN:
        return vmPluginStepSerializer.serialize((PluginStepInfo) stepInfo, stageInfraDetails, identifier,
            parameterFieldTimeout, stepName, ambiance, registries, executionSource);
      case GCR:
      case DOCKER:
      case ECR:
      case ACR:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_GCS:
      case UPLOAD_S3:
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
      case SAVE_CACHE_S3:
      case SECURITY:
      case RESTORE_CACHE_S3:
      case GIT_CLONE:
      case SSCA_ORCHESTRATION:
        return vmPluginCompatibleStepSerializer.serialize(
            ambiance, (PluginCompatibleStep) stepInfo, stageInfraDetails, identifier, parameterFieldTimeout, stepName);
      case IACM_TERRAFORM:
        return vmIACMPluginCompatibleStepSerializer.serialize(
            ambiance, (IACMTerraformPlanInfo) stepInfo, stageInfraDetails, identifier, parameterFieldTimeout);
      case ACTION:
        return vmActionStepSerializer.serialize((ActionStepInfo) stepInfo, identifier, stageInfraDetails);
      case BITRISE:
        return vmBitriseStepSerializer.serialize((BitriseStepInfo) stepInfo, identifier, stageInfraDetails);
      case CLEANUP:
      case TEST:
      case BUILD:
      case SETUP_ENV:
      case INITIALIZE_TASK:
      default:
        //                log.info("serialisation is not implemented");
        return null;
    }
  }

  public Set<String> preProcessStep(
      Ambiance ambiance, CIStepInfo stepInfo, StageInfraDetails stageInfraDetails, String identifier) {
    switch (stepInfo.getNonYamlInfo().getStepInfoType()) {
      case DOCKER:
      case ECR:
        return vmPluginCompatibleStepSerializer.preProcessStep(
            ambiance, (PluginCompatibleStep) stepInfo, stageInfraDetails, identifier);
      case GCR:
      case ACR:
      default:
        return new HashSet<>();
    }
  }
}
