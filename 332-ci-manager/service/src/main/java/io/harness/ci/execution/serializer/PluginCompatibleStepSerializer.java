/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.callback.DelegateCallbackToken;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.plugin.service.BasePluginCompatibleSerializer;

import com.google.inject.Inject;
import java.util.List;
import java.util.function.Supplier;

@OwnedBy(CI)
public class PluginCompatibleStepSerializer extends BasePluginCompatibleSerializer {
  @Inject private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject private CIExecutionConfigService ciExecutionConfigService;

  @Override
  public String getImageName(PluginCompatibleStep pluginCompatibleStep, String accountId) {
    return CIStepInfoUtils.getPluginCustomStepImage(pluginCompatibleStep, ciExecutionConfigService, Type.K8, accountId);
  }

  @Override
  public List<String> getEntryPoint(PluginCompatibleStep pluginCompatibleStep, String accountId, OSType os) {
    return CIStepInfoUtils.getK8PluginCustomStepEntrypoint(
        pluginCompatibleStep, ciExecutionConfigService, accountId, os);
  }

  @Override
  public String getDelegateCallbackToken() {
    return delegateCallbackTokenSupplier.get().getToken();
  }

  @Override
  public List<String> getOutputVariables(PluginCompatibleStep pluginCompatibleStep) {
    return CIStepInfoUtils.getOutputVariables(pluginCompatibleStep);
  }
}
