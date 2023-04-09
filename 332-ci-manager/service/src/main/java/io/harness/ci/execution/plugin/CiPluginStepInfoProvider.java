/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plugin;

import static io.harness.beans.steps.CIStepInfoType.GIT_CLONE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PORT_STARTING_RANGE;

import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.integrationstage.K8InitializeStepUtils;
import io.harness.ci.utils.PortFinder;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.plugin.PluginInfoProvider;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.TimeoutUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class CiPluginStepInfoProvider implements PluginInfoProvider {
  @Inject K8InitializeStepUtils k8InitializeStepUtils;

  @Override
  public PluginCreationResponse getPluginInfo(PluginCreationRequest request) {
    String stepJsonNode = request.getStepJsonNode();
    io.harness.beans.plugin.compatible.PluginCompatibleStep pluginCompatibleStep;
    CIAbstractStepNode ciAbstractStepNode;
    try {
      ciAbstractStepNode = YamlUtils.read(stepJsonNode, CIAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CI step for step type [%s]", request.getType()), e);
    }
    // todo(abhinav): get used ports from request
    pluginCompatibleStep = (PluginCompatibleStep) ciAbstractStepNode.getStepSpecType();
    Set<Integer> usedPorts = new HashSet<>();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();
    long timeout =
        TimeoutUtils.getTimeoutInSeconds(ciAbstractStepNode.getTimeout(), pluginCompatibleStep.getDefaultTimeout());

    ContainerDefinitionInfo pluginCompatibleStepContainerDefinition =
        k8InitializeStepUtils.createPluginCompatibleStepContainerDefinition(pluginCompatibleStep, null, null,
            portFinder, 0, ciAbstractStepNode.getIdentifier(), ciAbstractStepNode.getName(), request.getType(), timeout,
            request.getAccountId(), OSType.fromString(request.getOsType()), request.getAmbiance(), 0, 0);
    return PluginCreationResponse.newBuilder()
        .setPluginDetails(
            PluginDetails.newBuilder().putAllEnvVariables(pluginCompatibleStepContainerDefinition.getEnvVars()))
        .build();
  }

  @Override
  public boolean isSupported(String stepType) {
    if (GIT_CLONE.getDisplayName().equals(stepType)) {
      return true;
    }
    return false;
  }
}
