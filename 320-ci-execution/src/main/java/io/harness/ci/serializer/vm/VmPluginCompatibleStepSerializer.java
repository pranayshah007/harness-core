package io.harness.ci.serializer.vm;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfoUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.pms.yaml.ParameterField;
import io.harness.stateutils.buildstate.PluginSettingUtils;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@Singleton
public class VmPluginCompatibleStepSerializer {
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;

  public VmPluginStep serialize(PluginCompatibleStep pluginCompatibleStep, String identifier,
      ParameterField<Timeout> parameterFieldTimeout, String stepName) {
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, pluginCompatibleStep.getDefaultTimeout());
    Map<String, String> envVars =
        PluginSettingUtils.getPluginCompatibleEnvVariables(pluginCompatibleStep, identifier, timeout);
    String image = CIStepInfoUtils.getPluginCustomStepImage(pluginCompatibleStep, ciExecutionServiceConfig);
    return VmPluginStep.builder().image(image).envVariables(envVars).timeoutSecs(timeout).build();
  }
}
