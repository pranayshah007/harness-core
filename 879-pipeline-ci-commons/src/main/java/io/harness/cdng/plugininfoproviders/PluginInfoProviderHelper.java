/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.quantity.unit.DecimalQuantityUnit;
import io.harness.beans.quantity.unit.StorageQuantityUnit;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.ci.utils.PortFinder;
import io.harness.ci.utils.QuantityUtils;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.ImageInformation;
import io.harness.pms.contracts.plan.PluginContainerResources;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.PortDetails;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.protobuf.StringValue;
import java.util.Set;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class PluginInfoProviderHelper {
  private final Integer DEFAULT_CPU_LIMIT = 100;
  private final Integer DEFAULT_MEMORY_LIMIT = 256;

  public Integer getCPU(ContainerResource containerResource) {
    if (containerResource != null && ParameterField.isNotNull(containerResource.getLimits().getCpu())) {
      return QuantityUtils.getCpuQuantityValueInUnit(
          containerResource.getLimits().getCpu().getValue(), DecimalQuantityUnit.m);
    }
    return DEFAULT_CPU_LIMIT;
  }

  public Integer getMemory(ContainerResource containerResource) {
    if (containerResource != null && ParameterField.isNotNull(containerResource.getLimits().getMemory())) {
      return QuantityUtils.getStorageQuantityValueInUnit(
          containerResource.getLimits().getMemory().getValue(), StorageQuantityUnit.Mi);
    }
    return DEFAULT_MEMORY_LIMIT;
  }

  /**
   * Set used ports and available ports information for Pod specification
   * @param usedPorts
   * @param pluginDetailsBuilder PluginDetailsBuilder object to set port information.
   */
  protected void setPortDetails(Set<Integer> usedPorts, PluginDetails.Builder pluginDetailsBuilder) {
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();
    Integer nextPort = portFinder.getNextPort();
    pluginDetailsBuilder.addPortUsed(nextPort);

    usedPorts.add(nextPort);

    pluginDetailsBuilder.setTotalPortUsedDetails(PortDetails.newBuilder().addAllUsedPorts(usedPorts).build());
  }

  protected PluginDetails.Builder buildPluginDetails(
      ContainerResource resources, ParameterField<Integer> runAsUser, Set<Integer> usedPorts) {
    PluginDetails.Builder pluginDetailsBuilder = PluginDetails.newBuilder();

    PluginContainerResources pluginContainerResources = PluginContainerResources.newBuilder()
                                                            .setCpu(PluginInfoProviderHelper.getCPU(resources))
                                                            .setMemory(PluginInfoProviderHelper.getMemory(resources))
                                                            .build();

    pluginDetailsBuilder.setResource(pluginContainerResources);

    if (runAsUser != null && runAsUser.getValue() != null) {
      pluginDetailsBuilder.setRunAsUser(runAsUser.getValue());
    }

    // Set used port and available port information
    PluginInfoProviderHelper.setPortDetails(usedPorts, pluginDetailsBuilder);

    return pluginDetailsBuilder;
  }

  protected ImageDetails getImageDetails(ParameterField<String> connectorRef, ParameterField<String> image,
      ParameterField<ImagePullPolicy> imagePullPolicy) {
    StringValue imagePullPolicyStr;
    if (ParameterField.isNull(imagePullPolicy) || isEmpty(imagePullPolicy.getValue().toString())) {
      imagePullPolicyStr = StringValue.of(ImagePullPolicy.ALWAYS.toString());
    } else {
      imagePullPolicyStr = StringValue.of(imagePullPolicy.getValue().toString());
    }

    return ImageDetails.newBuilder()
        .setConnectorDetails(ConnectorDetails.newBuilder().setConnectorRef(connectorRef.getValue()).build())
        .setImageInformation(ImageInformation.newBuilder()
                                 .setImageName(StringValue.of(image.getValue()))
                                 .setImagePullPolicy(imagePullPolicyStr)
                                 .build())

        .build();
  }
}
