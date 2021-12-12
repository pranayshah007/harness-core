package io.harness.ci.serializer.vm;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class VmStepSerializer {
  @Inject VmPluginCompatibleStepSerializer vmPluginCompatibleStepSerializer;

  public VmStepInfo serialize(CIStepInfo stepInfo, String identifier, ParameterField<Timeout> parameterFieldTimeout) {
    String stepName = stepInfo.getNonYamlInfo().getStepInfoType().getDisplayName();
    switch (stepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return VmRunStepSerializer.serialize((RunStepInfo) stepInfo, identifier, parameterFieldTimeout, stepName);
      case RUN_TESTS:
        return VmRunTestStepSerializer.serialize(
            (RunTestsStepInfo) stepInfo, identifier, parameterFieldTimeout, stepName);
      case PLUGIN:
        return VmPluginStepSerializer.serialize((PluginStepInfo) stepInfo, identifier, parameterFieldTimeout, stepName);
      case GCR:
      case DOCKER:
      case ECR:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_GCS:
      case UPLOAD_S3:
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
      case SAVE_CACHE_S3:
      case RESTORE_CACHE_S3:
        return vmPluginCompatibleStepSerializer.serialize(
            (PluginCompatibleStep) stepInfo, identifier, parameterFieldTimeout, stepName);
      case CLEANUP:
      case TEST:
      case BUILD:
      case SETUP_ENV:
      case GIT_CLONE:
      case INITIALIZE_TASK:
      default:
        //                log.info("serialisation is not implemented");
        return null;
    }
  }
}
