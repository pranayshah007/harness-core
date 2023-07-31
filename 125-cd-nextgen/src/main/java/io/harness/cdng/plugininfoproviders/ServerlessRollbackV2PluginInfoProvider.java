/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaRollbackV2StepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails.Builder;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.container.execution.plugin.StepImageConfig;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class ServerlessRollbackV2PluginInfoProvider implements CDPluginInfoProvider {
  @Inject private ServerlessV2PluginInfoProviderHelper serverlessV2PluginInfoProviderHelper;

  @Inject private PluginExecutionConfig pluginExecutionConfig;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;

    try {
      cdAbstractStepNode = read(stepJsonNode);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    ServerlessAwsLambdaRollbackV2StepInfo serverlessAwsLambdaRollbackV2StepInfo =
        (ServerlessAwsLambdaRollbackV2StepInfo) cdAbstractStepNode.getStepSpecType();

    Builder pluginDetailsBuilder =
        PluginInfoProviderHelper.buildPluginDetails(serverlessAwsLambdaRollbackV2StepInfo.getResources(),
            serverlessAwsLambdaRollbackV2StepInfo.getRunAsUser(), usedPorts);

    final ImageDetails imageDetails;

    if (ParameterField.isNotNull(serverlessAwsLambdaRollbackV2StepInfo.getConnectorRef())
        || isNotEmpty(serverlessAwsLambdaRollbackV2StepInfo.getConnectorRef().getValue())) {
      imageDetails = getImageDetails(serverlessAwsLambdaRollbackV2StepInfo);

    } else {
      // todo: If image is not provided by user, default to an harness provided image
      StepImageConfig stepImageConfig = pluginExecutionConfig.getServerlessAwsLambdaRollbackV2StepImageConfig();
      imageDetails = null;
    }

    pluginDetailsBuilder.setImageDetails(imageDetails);

    pluginDetailsBuilder.putAllEnvVariables(
        serverlessV2PluginInfoProviderHelper.getEnvironmentVariables(ambiance, serverlessAwsLambdaRollbackV2StepInfo));
    PluginCreationResponse response = getPluginCreationResponse(pluginDetailsBuilder);
    StepInfoProto stepInfoProto = StepInfoProto.newBuilder()
                                      .setIdentifier(cdAbstractStepNode.getIdentifier())
                                      .setName(cdAbstractStepNode.getName())
                                      .setUuid(cdAbstractStepNode.getUuid())
                                      .build();
    return getPluginCreationResponseWrapper(response, stepInfoProto);
  }

  public PluginCreationResponseWrapper getPluginCreationResponseWrapper(
      PluginCreationResponse response, StepInfoProto stepInfoProto) {
    return PluginCreationResponseWrapper.newBuilder().setResponse(response).setStepInfo(stepInfoProto).build();
  }

  private PluginCreationResponse getPluginCreationResponse(Builder pluginDetailsBuilder) {
    return PluginCreationResponse.newBuilder().setPluginDetails(pluginDetailsBuilder.build()).build();
  }

  public ImageDetails getImageDetails(ServerlessAwsLambdaRollbackV2StepInfo serverlessAwsLambdaRollbackV2StepInfo) {
    return PluginInfoProviderHelper.getImageDetails(serverlessAwsLambdaRollbackV2StepInfo.getConnectorRef(),
        serverlessAwsLambdaRollbackV2StepInfo.getImage(), serverlessAwsLambdaRollbackV2StepInfo.getImagePullPolicy());
  }

  private Builder getPluginDetailsBuilder(
      ContainerResource resources, ParameterField<Integer> runAsUser, Set<Integer> usedPorts) {
    return PluginInfoProviderHelper.buildPluginDetails(resources, runAsUser, usedPorts);
  }

  public CdAbstractStepNode read(String stepJsonNode) throws IOException {
    return YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
  }

  @Override
  public boolean isSupported(String stepType) {
    return StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK_V2.equals(stepType);
  }
}