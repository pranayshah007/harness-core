/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.callback.DelegateCallbackToken;
import io.harness.ci.execution.execution.CIExecutionConfigService;
import io.harness.ci.execution.utils.CIStepInfoUtils;
import io.harness.plugin.service.BasePluginCompatibleSerializer;
import io.harness.plugin.service.PluginService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.ssca.beans.stepinfo.ProvenanceStepInfo;
import io.harness.ssca.execution.ProvenancePluginHelper;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@OwnedBy(CI)
public class PluginCompatibleStepSerializer extends BasePluginCompatibleSerializer {
  @Inject private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject private PluginService pluginService;
  @Inject private ProvenancePluginHelper provenancePluginHelper;

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

  @Override
  protected Map<String, String> getPluginCompatibleEnvVariables(PluginCompatibleStep stepInfo, String identifier,
      long timeout, Ambiance ambiance, StageInfraDetails.Type infraType, boolean isMandatory,
      boolean isContainerizedPlugin) {
    if (stepInfo.getNonYamlInfo().getStepInfoType() == CIStepInfoType.PROVENANCE && infraType == Type.K8) {
      return provenancePluginHelper.getProvenanceStepEnvVariablesAtRuntime(
          (ProvenanceStepInfo) stepInfo, identifier, ambiance);
    }

    return pluginService.getPluginCompatibleEnvVariables(
        stepInfo, identifier, timeout, ambiance, StageInfraDetails.Type.K8, true, true);
  }
}
