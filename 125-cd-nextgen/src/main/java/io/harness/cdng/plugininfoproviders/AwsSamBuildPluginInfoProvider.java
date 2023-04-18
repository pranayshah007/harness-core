/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.cdng.aws.sam.AwsSamBuildStepInfo;
import io.harness.cdng.aws.sam.AwsSamBuildStepNode;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.ImageInformation;
import io.harness.pms.contracts.plan.PluginContainerResources;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.tools.StringUtils;

public class AwsSamBuildPluginInfoProvider extends CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Override
  public PluginCreationResponse getPluginInfo(PluginCreationRequest request) {
    String stepJsonNode = request.getStepJsonNode();
    AwsSamBuildStepNode awsSamBuildStepNode;
    CdAbstractStepNode cdAbstractStepNode;

    try {
      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    awsSamBuildStepNode = (AwsSamBuildStepNode) cdAbstractStepNode.getStepSpecType();
    AwsSamBuildStepInfo awsSamBuildStepInfo = awsSamBuildStepNode.getAwsSamBuildStepInfo();

    return PluginCreationResponse.newBuilder()
        .setPluginDetails(
            PluginDetails.newBuilder()
                .setResource(
                    PluginContainerResources.newBuilder()
                        .setCpu(Integer.parseInt(awsSamBuildStepInfo.getResources().getRequests().getCpu().getValue()))
                        .setMemory(
                            Integer.parseInt(awsSamBuildStepInfo.getResources().getRequests().getMemory().getValue()))
                        .build())
                .setRunAsUser(awsSamBuildStepInfo.getRunAsUser().getValue())
                .putAllEnvVariables(
                    getEnvironmentVariables(request.getAmbiance(), awsSamBuildStepInfo.getBuildCommandOptions()))
                .setImageDetails(
                    ImageDetails.newBuilder()
                        .setConnectorDetails(ConnectorDetails.newBuilder()
                                                 .setConnectorRef(awsSamBuildStepInfo.getConnectorRef().getValue())
                                                 .build())
                        .setImageInformation(
                            ImageInformation.newBuilder()
                                .setImageName(StringValue.of(awsSamBuildStepInfo.getImage().getValue()))
                                .setImagePullPolicy(
                                    getImagePullPolicyEnum(awsSamBuildStepInfo.getImagePullPolicy().getValue()))
                                .build())

                        .build())
                .build())
        .build();
  }

  @Override
  public boolean isSupported(String stepType) {
    return true;
  }

  private StringValue getImagePullPolicyEnum(ImagePullPolicy imagePullPolicy) {
    if (imagePullPolicy.equals(ImagePullPolicy.ALWAYS.name())) {
      return StringValue.of(ImagePullPolicy.ALWAYS.toString());
    } else if (imagePullPolicy.equals(ImagePullPolicy.NEVER.name())) {
      return StringValue.of(ImagePullPolicy.NEVER.name());
    } else if (imagePullPolicy.equals(ImagePullPolicy.IFNOTPRESENT.name())) {
      return StringValue.of(ImagePullPolicy.IFNOTPRESENT.name());
    } else {
      throw new InvalidRequestException("ImagePolicy Not Supported");
    }
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
