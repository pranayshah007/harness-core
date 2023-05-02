/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plugin;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.integrationstage.K8InitializeStepUtils;
import io.harness.ci.utils.PortFinder;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginContainerResources;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.PortDetails;
import io.harness.pms.contracts.plan.SecretVariable;
import io.harness.pms.expression.ExpressionResolverUtils;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.plugin.ImageDetailsUtils;
import io.harness.pms.sdk.core.plugin.PluginInfoProvider;
import io.harness.pms.sdk.core.plugin.SecretNgVariableUtils;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
    Set<Integer> usedPorts = new HashSet<>(request.getUsedPortDetails().getUsedPortsList());
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();
    ContainerDefinitionInfo containerDefinitionInfo =
        k8InitializeStepUtils.createStepContainerDefinition(ciAbstractStepNode, null, null, portFinder, 0,
            request.getAccountId(), OSType.fromString(request.getOsType()), request.getAmbiance(), 0, 0);
    List<SecretVariable> secretVariables = containerDefinitionInfo.getSecretVariables()
                                               .stream()
                                               .map(SecretNgVariableUtils::getSecretVariable)
                                               .collect(Collectors.toList());
    HashSet<Integer> ports = new HashSet<>(portFinder.getUsedPorts());
    ports.addAll(containerDefinitionInfo.getPorts());

    PluginDetails.Builder pluginDetailsBuilder =
        PluginDetails.newBuilder()
            .putAllEnvVariables(containerDefinitionInfo.getEnvVars())
            .setImageDetails(
                ImageDetails.newBuilder()
                    .setImageInformation(ImageDetailsUtils.getImageDetails(
                        containerDefinitionInfo.getContainerImageDetails().getImageDetails()))
                    .setConnectorDetails(
                        ConnectorDetails.newBuilder()
                            .setConnectorRef(emptyIfNull(
                                containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier()))
                            .build())
                    .build())
            .setPrivileged(containerDefinitionInfo.getPrivileged() == null || containerDefinitionInfo.getPrivileged())
            .addAllPortUsed(containerDefinitionInfo.getPorts())
            .setTotalPortUsedDetails(PortDetails.newBuilder().addAllUsedPorts(ports).build())
            .setResource(getPluginContainerResources(containerDefinitionInfo))
            .addAllSecretVariable(secretVariables);

    if (containerDefinitionInfo.getRunAsUser() != null) {
      pluginDetailsBuilder.setRunAsUser(containerDefinitionInfo.getRunAsUser());
    }

    if (!(CIStepInfoType.BACKGROUND_V1.getDisplayName().equals(ciAbstractStepNode.getType())
            || CIStepInfoType.BACKGROUND.getDisplayName().equals(ciAbstractStepNode.getType()))) {
      pluginCompatibleStep =
          (io.harness.beans.plugin.compatible.PluginCompatibleStep) ciAbstractStepNode.getStepSpecType();

      String stepConnectorRef =
          ExpressionResolverUtils.resolveStringParameter("connectorRef", pluginCompatibleStep.getStepType().toString(),
              pluginCompatibleStep.getIdentifier(), pluginCompatibleStep.getConnectorRef(), false);
      if (isNotEmpty(stepConnectorRef)) {
        // todo: if we need to support more steps we need to add connector env conversion map too.
        pluginDetailsBuilder.addConnectorsForStep(
            ConnectorDetails.newBuilder().setConnectorRef(stepConnectorRef).build());
      }
    }
    return PluginCreationResponse.newBuilder().setPluginDetails(pluginDetailsBuilder.build()).build();
  }

  private PluginContainerResources getPluginContainerResources(ContainerDefinitionInfo containerDefinitionInfo) {
    return PluginContainerResources.newBuilder()
        .setCpu(containerDefinitionInfo.getContainerResourceParams().getResourceLimitMilliCpu())
        .setMemory(containerDefinitionInfo.getContainerResourceParams().getResourceLimitMemoryMiB())
        .build();
  }

  @Override
  public boolean isSupported(String stepType) {
    return CIStepInfoType.BACKGROUND.getDisplayName().equals(stepType)
        || CIStepInfoType.BACKGROUND_V1.getDisplayName().equals(stepType)
        || CIStepInfoType.GIT_CLONE.getDisplayName().equals(stepType);
  }
}
