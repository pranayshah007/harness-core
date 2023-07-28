/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_APP_PATH;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_COMMAND_OPTIONS;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_STACK_NAMES;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.provision.awscdk.AwsCdkDiffStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AwsCdkDiffPluginInfoProvider implements CDPluginInfoProvider {
  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;

    cdAbstractStepNode = getCdAbstractStepNode(request, stepJsonNode);

    AwsCdkDiffStepInfo awsCdkDiffStepInfo = (AwsCdkDiffStepInfo) cdAbstractStepNode.getStepSpecType();

    PluginDetails.Builder pluginDetailsBuilder = PluginInfoProviderHelper.buildPluginDetails(
        awsCdkDiffStepInfo.getResources(), awsCdkDiffStepInfo.getRunAsUser(), usedPorts);

    ImageDetails imageDetails = null;

    if (ParameterField.isNotNull(awsCdkDiffStepInfo.getConnectorRef())
        || isNotEmpty(awsCdkDiffStepInfo.getConnectorRef().getValue())) {
      imageDetails = PluginInfoProviderHelper.getImageDetails(
          awsCdkDiffStepInfo.getConnectorRef(), awsCdkDiffStepInfo.getImage(), awsCdkDiffStepInfo.getImagePullPolicy());
    }

    pluginDetailsBuilder.setImageDetails(imageDetails);

    pluginDetailsBuilder.putAllEnvVariables(getEnvironmentVariables(awsCdkDiffStepInfo));

    PluginCreationResponse response =
        PluginCreationResponse.newBuilder().setPluginDetails(pluginDetailsBuilder.build()).build();
    StepInfoProto stepInfoProto = StepInfoProto.newBuilder()
                                      .setIdentifier(cdAbstractStepNode.getIdentifier())
                                      .setName(cdAbstractStepNode.getName())
                                      .setUuid(cdAbstractStepNode.getUuid())
                                      .build();
    return PluginCreationResponseWrapper.newBuilder().setResponse(response).setStepInfo(stepInfoProto).build();
  }

  @VisibleForTesting
  protected CdAbstractStepNode getCdAbstractStepNode(PluginCreationRequest request, String stepJsonNode) {
    CdAbstractStepNode cdAbstractStepNode;
    try {
      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }
    return cdAbstractStepNode;
  }

  @Override
  public boolean isSupported(String stepType) {
    return stepType.equals(StepSpecTypeConstants.AWS_CDK_DIFF);
  }

  private Map<String, String> getEnvironmentVariables(AwsCdkDiffStepInfo awsCdkDiffStepInfo) {
    ParameterField<Map<String, String>> envVariables = awsCdkDiffStepInfo.getEnvVariables();

    HashMap<String, String> environmentVariablesMap = new HashMap<>();

    environmentVariablesMap.put(PLUGIN_AWS_CDK_APP_PATH, getParameterFieldValue(awsCdkDiffStepInfo.getAppPath()));
    List<String> commandOptions = getParameterFieldValue(awsCdkDiffStepInfo.getCommandOptions());
    List<String> stackNames = getParameterFieldValue(awsCdkDiffStepInfo.getStackNames());
    if (isNotEmpty(commandOptions)) {
      environmentVariablesMap.put(PLUGIN_AWS_CDK_COMMAND_OPTIONS, String.join(" ", commandOptions));
    }
    if (isNotEmpty(getParameterFieldValue(awsCdkDiffStepInfo.getStackNames()))) {
      environmentVariablesMap.put(PLUGIN_AWS_CDK_STACK_NAMES, String.join(" ", stackNames));
    }

    if (envVariables != null && envVariables.getValue() != null) {
      environmentVariablesMap.putAll(envVariables.getValue());
    }

    return environmentVariablesMap;
  }
}
