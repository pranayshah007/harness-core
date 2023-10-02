/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.plugin.PluginStepV2;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_COMMON_STEPS, HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
public class DefaultPluginInfoProvider implements PluginInfoProvider {
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    PluginStepV2 pluginStep;
    try {
      pluginStep = YamlUtils.read(request.getStepJsonNode(), PluginStepV2.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException("Error parsing plugin step", e);
    }
    ContainerResource.Limits resources = pluginStep.getResources().getLimits();

    // todo: check for variable issues
    // todo: not implemented fully, do when required
    PluginCreationResponse response =
        PluginCreationResponse.newBuilder()
            .setPluginDetails(
                PluginDetails.newBuilder()
                    .setPrivileged(pluginStep.getPrivileged())
                    //                              .setResource(PluginContainerResources.newBuilder()
                    //                                               .setCpu(resources.getCpu().getValue())
                    //                                               .setMemory(resources.getMemory().getValue())
                    //                                               .build())
                    .putAllEnvVariables(
                        pluginStep.getEnvVariables() == null ? Collections.emptyMap() : pluginStep.getEnvVariables())
                    .setImageDetails(PluginImageUtils.getImageDetails(pluginStep.getImageDetails()))
                    .build())
            .build();

    StepInfoProto stepInfoProto = StepInfoProto.newBuilder()
                                      .setIdentifier(pluginStep.getIdentifier())
                                      .setName(pluginStep.getIdentifier())
                                      .setUuid(pluginStep.getUuid())
                                      .build();
    return PluginCreationResponseWrapper.newBuilder().setResponse(response).setStepInfo(stepInfoProto).build();
  }

  @Override
  public boolean isSupported(String stepType) {
    return true;
  }
}
