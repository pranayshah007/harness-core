/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import io.harness.pms.contracts.plan.PluginContainerResources;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.plugin.PluginStepV2;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DefaultPluginInfoProvider implements PluginInfoProvider {
  @Inject YamlUtils yamlUtils;

  public PluginCreationResponse getPluginInfo(PluginCreationRequest request) {
    PluginStepV2 pluginStep;
    try {
      pluginStep = yamlUtils.read(request.getStepJsonNode(), PluginStepV2.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException("Error parsing plugin step", e);
    }
    ContainerResource.Limits resources = pluginStep.getResources().getLimits();
    // todo: check for variable issues
    return PluginCreationResponse.newBuilder()
        .setPluginDetails(PluginDetails.newBuilder()
                              .setPrivileged(pluginStep.getPrivileged())
                              .setResource(PluginContainerResources.newBuilder()
                                               .setCpu(resources.getCpu().getValue())
                                               .setMemory(resources.getMemory().getValue())
                                               .build())
                              .setStepUuid(pluginStep.getUuid())
                              .setStepIdentifier(pluginStep.getIdentifier())
                              .putAllEnvVariables(pluginStep.getEnvVariables() == null ? Collections.emptyMap()
                                                                                       : pluginStep.getEnvVariables())
                              .setImageDetails(PluginImageUtils.getImageDetails(pluginStep.getImageDetails()))
                              .build())
        .build();
  }
}
