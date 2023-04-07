/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.sam.AwsSamDeployStepInfo;
import io.harness.cdng.aws.sam.AwsSamDeployStepNode;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.ImagePullPolicy;
import io.harness.pms.contracts.plan.PluginContainerResources;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.tools.StringUtils;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamDeployPluginInfoProvider extends CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Override
  public PluginCreationResponse getPluginInfo(PluginCreationRequest request) {
    String stepJsonNode = request.getStepJsonNode();
    AwsSamDeployStepNode awsSamDeployStepNode;
    CdAbstractStepNode cdAbstractStepNode;

    try {
      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    awsSamDeployStepNode = (AwsSamDeployStepNode) cdAbstractStepNode.getStepSpecType();
    AwsSamDeployStepInfo awsSamDeployStepInfo = awsSamDeployStepNode.getAwsSamDeployStepInfo();

    return PluginCreationResponse.newBuilder()
        .setPluginDetails(
            PluginDetails.newBuilder()
                .setResource(PluginContainerResources.newBuilder()
                                 .setCpu(awsSamDeployStepInfo.getResources().getRequests().getCpu().getValue())
                                 .setMemory(awsSamDeployStepInfo.getResources().getRequests().getMemory().getValue())
                                 .build())
                .setRunAsUser(awsSamDeployStepInfo.getRunAsUser().getValue())
                .putAllEnvVariables(
                    getEnvironmentVariables(request.getAmbiance(), awsSamDeployStepInfo.getDeployCommandOptions()))
                .setImageDetails(
                    ImageDetails.newBuilder()
                        .setConnectorDetails(ConnectorDetails.newBuilder()
                                                 .setConnectorRef(awsSamDeployStepInfo.getConnectorRef().getValue())
                                                 .build())
                        .setImageName(awsSamDeployStepInfo.getImage().getValue())
                        .setImagePullPolicy(
                            getImagePullPolicyEnum(awsSamDeployStepInfo.getImagePullPolicy().getValue()))
                        .build())
                .build())
        .build();
  }

  @Override
  public boolean isSupported(String stepType) {
    return true;
  }

  private ImagePullPolicy getImagePullPolicyEnum(io.harness.beans.yaml.extended.ImagePullPolicy imagePullPolicy) {
    if (imagePullPolicy.equals(ImagePullPolicy.ALWAYS.name())) {
      return ImagePullPolicy.ALWAYS;
    } else if (imagePullPolicy.equals(ImagePullPolicy.NEVER.name())) {
      return ImagePullPolicy.NEVER;
    } else if (imagePullPolicy.equals(ImagePullPolicy.IF_NOT_PRESENT.name())) {
      return ImagePullPolicy.IF_NOT_PRESENT;
    } else {
      throw new InvalidRequestException("ImagePolicy Not Supported");
    }
  }

  private Map<String, String> getEnvironmentVariables(
      Ambiance ambiance, ParameterField<List<String>> deployCommandOptions) {
    // Resolve Expressions
    cdExpressionResolver.updateExpressions(ambiance, deployCommandOptions);

    HashMap<String, String> deployCommandOptionsMap = new HashMap<>();
    deployCommandOptionsMap.put("DEPLOY_COMMAND_OPTIONS", StringUtils.join(deployCommandOptions.getValue(), " "));

    return deployCommandOptionsMap;
  }
}
