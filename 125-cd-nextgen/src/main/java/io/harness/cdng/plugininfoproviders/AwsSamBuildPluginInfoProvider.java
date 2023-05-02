/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.sam.AwsSamBuildStepInfo;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.container.execution.plugin.StepImageConfig;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamBuildPluginInfoProvider implements CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject PluginExecutionConfig pluginExecutionConfig;
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

    PluginDetails.Builder pluginDetailsBuilder = PluginInfoProviderHelper.buildPluginDetails(
        request, awsSamBuildStepInfo.getResources(), awsSamBuildStepInfo.getRunAsUser());

    ImageDetails imageDetails = null;

    if (ParameterField.isNotNull(awsSamBuildStepInfo.getConnectorRef())
        || isNotEmpty(awsSamBuildStepInfo.getConnectorRef().getValue())) {
      imageDetails = PluginInfoProviderHelper.getImageDetails(awsSamBuildStepInfo.getConnectorRef(),
          awsSamBuildStepInfo.getImage(), awsSamBuildStepInfo.getImagePullPolicy());

    } else {
      // todo: If image is not provided by user, default to an harness provided image
      StepImageConfig stepImageConfig = pluginExecutionConfig.getSamBuildStepImageConfig();
    }

    pluginDetailsBuilder.setImageDetails(imageDetails);

    pluginDetailsBuilder.putAllEnvVariables(getEnvironmentVariables(
        request.getAmbiance(), awsSamBuildStepInfo.getBuildCommandOptions(), awsSamBuildStepInfo.getEnvVariables()));

    return PluginCreationResponse.newBuilder().setPluginDetails(pluginDetailsBuilder.build()).build();
  }

  @Override
  public boolean isSupported(String stepType) {
    if (stepType.equals(StepSpecTypeConstants.AWS_SAM_BUILD)) {
      return true;
    }
    return false;
  }

  private Map<String, String> getEnvironmentVariables(Ambiance ambiance,
      ParameterField<List<String>> buildCommandOptions, ParameterField<Map<String, String>> envVariables) {
    // Resolve Expressions
    cdExpressionResolver.updateExpressions(ambiance, buildCommandOptions);
    HashMap<String, String> buildCommandOptionsMap = new HashMap<>();

    if (ParameterField.isNotNull(envVariables)) {
      Map<String, String> envVariablesValue = envVariables.getValue();
      buildCommandOptionsMap.put("PLUGIN_SAM_DIR", envVariablesValue.get("sam_dir"));
    } else {
      throw new InvalidRequestException("SAM Directory must be provided");
    }

    if (ParameterField.isNotNull(buildCommandOptions)) {
      buildCommandOptionsMap.put("PLUGIN_BUILD_COMMAND_OPTIONS", String.join(" ", buildCommandOptions.getValue()));
    } else {
      buildCommandOptionsMap.put("PLUGIN_BUILD_COMMAND_OPTIONS", "--use-container");
    }

    // todo: Fetch from private registry variable once connectordetails is available.
    buildCommandOptionsMap.put("PLUGIN_PRIVATE_REGISTRY_URL", "");
    buildCommandOptionsMap.put("PLUGIN_PRIVATE_REGISTRY_USERNAME", "");
    buildCommandOptionsMap.put("PLUGIN_PRIVATE_REGISTRY_PASSWORD", "");
    buildCommandOptionsMap.put("DRONE_OUTPUT", "/harness/output.json");

    return buildCommandOptionsMap;
  }
}
