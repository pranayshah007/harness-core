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

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.execution.buildstate.PluginSettingUtils;
import io.harness.ci.execution.integrationstage.K8InitializeStepUtils;
import io.harness.ci.utils.PortFinder;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginContainerResources;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.PortDetails;
import io.harness.pms.contracts.plan.SecretVariable;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.plugin.ImageDetailsUtils;
import io.harness.pms.sdk.core.plugin.PluginInfoProvider;
import io.harness.pms.sdk.core.plugin.SecretNgVariableUtils;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import com.google.protobuf.BoolValue;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_COMMON_STEPS, HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class CiPluginStepInfoProvider implements PluginInfoProvider {
  @Inject K8InitializeStepUtils k8InitializeStepUtils;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String stepJsonNode = request.getStepJsonNode();
    CIAbstractStepNode ciAbstractStepNode;
    try {
      ciAbstractStepNode = YamlUtils.read(stepJsonNode, CIAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CI step for step type [%s]", request.getType()), e);
    }
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();
    ContainerDefinitionInfo containerDefinitionInfo =
        k8InitializeStepUtils.createStepContainerDefinition(ciAbstractStepNode, null, null, portFinder, 0,
            request.getAccountId(), OSType.fromString(request.getOsType()), ambiance, 0, 0);
    List<SecretVariable> secretVariables = containerDefinitionInfo.getSecretVariables()
                                               .stream()
                                               .map(SecretNgVariableUtils::getSecretVariable)
                                               .collect(Collectors.toList());
    HashSet<Integer> ports = new HashSet<>(portFinder.getUsedPorts());
    ports.addAll(containerDefinitionInfo.getPorts());

    PluginDetails.Builder pluginDetailsBuilder =
        PluginDetails.newBuilder()
            .putAllEnvVariables(containerDefinitionInfo.getEnvVars())
            .setIsHarnessManaged(BoolValue.of(containerDefinitionInfo.isHarnessManagedImage()))
            .setImageDetails(
                ImageDetails.newBuilder()
                    .setImageInformation(ImageDetailsUtils.getImageDetails(
                        containerDefinitionInfo.getContainerImageDetails().getImageDetails(),
                        containerDefinitionInfo.getImagePullPolicy()))
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

    if ((ciAbstractStepNode.getStepSpecType() instanceof PluginCompatibleStep)
        && (ciAbstractStepNode.getStepSpecType() instanceof WithConnectorRef)) {
      PluginCompatibleStep step = (PluginCompatibleStep) ciAbstractStepNode.getStepSpecType();
      Map<String, String> connectorSecretEnvMap = new HashMap<>();
      PluginSettingUtils.getConnectorSecretEnvMap(step.getNonYamlInfo().getStepInfoType())
          .forEach((key, value) -> connectorSecretEnvMap.put(key.name(), value));
      String connectorRef = PluginSettingUtils.getConnectorRef(step);
      if (isNotEmpty(connectorRef)) {
        pluginDetailsBuilder.addConnectorsForStep(ConnectorDetails.newBuilder()
                                                      .setConnectorRef(connectorRef)
                                                      .putAllConnectorSecretEnvMap(connectorSecretEnvMap)
                                                      .build());
      }
    }

    PluginCreationResponse response =
        PluginCreationResponse.newBuilder().setPluginDetails(pluginDetailsBuilder.build()).build();
    StepInfoProto stepInfoProto = StepInfoProto.newBuilder()
                                      .setIdentifier(ciAbstractStepNode.getIdentifier())
                                      .setName(ciAbstractStepNode.getName())
                                      .setUuid(ciAbstractStepNode.getUuid())
                                      .build();
    return PluginCreationResponseWrapper.newBuilder().setResponse(response).setStepInfo(stepInfoProto).build();
  }

  private PluginContainerResources getPluginContainerResources(ContainerDefinitionInfo containerDefinitionInfo) {
    return PluginContainerResources.newBuilder()
        .setCpu(containerDefinitionInfo.getContainerResourceParams().getResourceLimitMilliCpu())
        .setMemory(containerDefinitionInfo.getContainerResourceParams().getResourceLimitMemoryMiB())
        .build();
  }

  @Override
  public boolean isSupported(String stepType) {
    return true;
  }
}
