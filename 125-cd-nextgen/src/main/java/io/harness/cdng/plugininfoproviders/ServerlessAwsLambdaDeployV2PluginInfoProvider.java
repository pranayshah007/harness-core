/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaDeployV2StepInfo;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaV2BaseStepInfo;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ECS, HarnessModuleComponent.CDS_SERVERLESS})
@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaDeployV2PluginInfoProvider implements CDPluginInfoProvider {
  @Inject private ServerlessV2PluginInfoProviderHelper serverlessV2PluginInfoProviderHelper;
  @Inject PluginExecutionConfig pluginExecutionConfig;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(

      // todo: Merge this with SamPluginInfoProvider

      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;

    try {
      cdAbstractStepNode = getRead(stepJsonNode);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    ServerlessAwsLambdaDeployV2StepInfo serverlessAwsLambdaDeployV2StepInfo =
        (ServerlessAwsLambdaDeployV2StepInfo) cdAbstractStepNode.getStepSpecType();

    Builder pluginDetailsBuilder = getPluginDetailsBuilder(serverlessAwsLambdaDeployV2StepInfo.getResources(),
        serverlessAwsLambdaDeployV2StepInfo.getRunAsUser(), usedPorts);

    ImageDetails imageDetails = null;

    if (ParameterField.isNotNull(serverlessAwsLambdaDeployV2StepInfo.getConnectorRef())
        || isNotEmpty(serverlessAwsLambdaDeployV2StepInfo.getConnectorRef().getValue())) {
      imageDetails = getImageDetails(serverlessAwsLambdaDeployV2StepInfo);

    } else {
      // todo: If image is not provided by user, default to an harness provided image
      StepImageConfig stepImageConfig = pluginExecutionConfig.getServerlessAwsLambdaDeployV2StepImageConfig();
    }

    pluginDetailsBuilder.setImageDetails(imageDetails);

    pluginDetailsBuilder.putAllEnvVariables(
        serverlessV2PluginInfoProviderHelper.getEnvironmentVariables(ambiance, serverlessAwsLambdaDeployV2StepInfo));
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

  public PluginCreationResponse getPluginCreationResponse(Builder pluginDetailsBuilder) {
    return PluginCreationResponse.newBuilder().setPluginDetails(pluginDetailsBuilder.build()).build();
  }

  public ImageDetails getImageDetails(ServerlessAwsLambdaV2BaseStepInfo serverlessAwsLambdaV2BaseStepInfo) {
    return PluginInfoProviderHelper.getImageDetails(serverlessAwsLambdaV2BaseStepInfo.getConnectorRef(),
        serverlessAwsLambdaV2BaseStepInfo.getImage(), serverlessAwsLambdaV2BaseStepInfo.getImagePullPolicy());
  }

  public Builder getPluginDetailsBuilder(
      ContainerResource resources, ParameterField<Integer> runAsUser, Set<Integer> usedPorts) {
    return PluginInfoProviderHelper.buildPluginDetails(resources, runAsUser, usedPorts);
  }

  public CdAbstractStepNode getRead(String stepJsonNode) throws IOException {
    return YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
  }

  @Override
  public boolean isSupported(String stepType) {
    if (stepType.equals(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY_V2)) {
      return true;
    }
    return false;
  }
}
