/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.PORT_STARTING_RANGE;

import io.harness.cdng.aws.sam.AwsSamBuildStepInfo;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.ci.utils.PortFinder;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.ImageInformation;
import io.harness.pms.contracts.plan.PluginContainerResources;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.PortDetails;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.tools.StringUtils;

public class AwsSamBuildPluginInfoProvider extends CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Override
  public PluginCreationResponse getPluginInfo(PluginCreationRequest request) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;

    try {
      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    AwsSamBuildStepInfo awsSamBuildStepInfo = (AwsSamBuildStepInfo) cdAbstractStepNode.getStepSpecType();

    PluginContainerResources pluginContainerResources =
        PluginContainerResources.newBuilder()
            .setCpu(PluginInfoProviderHelper.getCPU(awsSamBuildStepInfo.getResources()))
            .setMemory(PluginInfoProviderHelper.getMemory(awsSamBuildStepInfo.getResources()))
            .build();

    ImageDetails imageDetails =
        ImageDetails.newBuilder()
            .setConnectorDetails(
                ConnectorDetails.newBuilder().setConnectorRef(awsSamBuildStepInfo.getConnectorRef().getValue()).build())
            .setImageInformation(
                ImageInformation.newBuilder()
                    .setImageName(StringValue.of(awsSamBuildStepInfo.getImage().getValue()))
                    .setImagePullPolicy(StringValue.of(awsSamBuildStepInfo.getImagePullPolicy().getValue().toString()))
                    .build())

            .build();
    Integer runAsUser =
        awsSamBuildStepInfo.getRunAsUser() != null ? awsSamBuildStepInfo.getRunAsUser().getValue() : 1000;

    Set<Integer> usedPorts = new HashSet<>(request.getUsedPortDetails().getUsedPortsList());
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE + 1).usedPorts(usedPorts).build();
    Integer nextPort = portFinder.getNextPort();
    HashSet<Integer> ports = new HashSet<>(portFinder.getUsedPorts());
    HashSet<Integer> nextports = new HashSet<>(nextPort);

    return PluginCreationResponse.newBuilder()
        .setPluginDetails(PluginDetails.newBuilder()
                              .setResource(pluginContainerResources)
                              .setRunAsUser(runAsUser)
                              .putAllEnvVariables(getEnvironmentVariables(
                                  request.getAmbiance(), awsSamBuildStepInfo.getBuildCommandOptions()))
                              .setImageDetails(imageDetails)
                              .addPortUsed(nextPort)
                              .setTotalPortUsedDetails(PortDetails.newBuilder().addAllUsedPorts(ports).build())
                              .build())
        .build();
  }

  @Override
  public boolean isSupported(String stepType) {
    if (stepType.equals(StepSpecTypeConstants.AWS_SAM_BUILD)) {
      return true;
    }
    return false;
  }

  private Map<String, String> getEnvironmentVariables(
      Ambiance ambiance, ParameterField<List<String>> buildCommandOptions) {
    // Resolve Expressions
    cdExpressionResolver.updateExpressions(ambiance, buildCommandOptions);

    HashMap<String, String> deployCommandOptionsMap = new HashMap<>();
    deployCommandOptionsMap.put("BUILD_COMMAND_OPTIONS", StringUtils.join(buildCommandOptions.getValue(), " "));

    return deployCommandOptionsMap;
  }
}
