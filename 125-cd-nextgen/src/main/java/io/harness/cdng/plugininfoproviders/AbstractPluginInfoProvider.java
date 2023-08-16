/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.extended.ci.container.ContainerResource;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public abstract class AbstractPluginInfoProvider implements CDPluginInfoProvider {
  protected CdAbstractStepNode getCdAbstractStepNode(String requestType, String stepJsonNode) {
    CdAbstractStepNode cdAbstractStepNode;
    try {
      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", requestType), e);
    }
    return cdAbstractStepNode;
  }

  protected StepInfoProto getStepInfoProto(CdAbstractStepNode cdAbstractStepNode) {
    return StepInfoProto.newBuilder()
        .setIdentifier(cdAbstractStepNode.getIdentifier())
        .setName(cdAbstractStepNode.getName())
        .setUuid(cdAbstractStepNode.getUuid())
        .build();
  }

  protected PluginDetails getPluginDetails(Set<Integer> usedPorts, ParameterField<Integer> runAsUser,
      ContainerResource resources, ParameterField<Boolean> privileged, Map<String, String> envVariables,
      ImageDetails imageDetails) {
    PluginDetails.Builder pluginDetailsBuilder =
        PluginInfoProviderHelper.buildPluginDetails(resources, runAsUser, usedPorts);
    pluginDetailsBuilder.setImageDetails(imageDetails);

    if (getParameterFieldValue(privileged) != null) {
      pluginDetailsBuilder.setPrivileged(getParameterFieldValue(privileged));
    }
    return pluginDetailsBuilder.putAllEnvVariables(envVariables).build();
  }
}
