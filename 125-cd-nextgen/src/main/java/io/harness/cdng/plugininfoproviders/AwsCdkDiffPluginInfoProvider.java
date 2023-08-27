/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.DIFF;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_ACTION;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_STACK_NAMES;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.provision.awscdk.AwsCdkDiffStepInfo;
import io.harness.cdng.provision.awscdk.AwsCdkHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class AwsCdkDiffPluginInfoProvider extends AbstractPluginInfoProvider {
  @Inject private AwsCdkHelper awsCdkStepHelper;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    CdAbstractStepNode cdAbstractStepNode = getCdAbstractStepNode(request.getType(), request.getStepJsonNode());
    AwsCdkDiffStepInfo awsCdkDiffStepInfo = (AwsCdkDiffStepInfo) cdAbstractStepNode.getStepSpecType();
    ImageDetails imageDetails = PluginInfoProviderHelper.getImageDetails(
        awsCdkDiffStepInfo.getConnectorRef(), awsCdkDiffStepInfo.getImage(), awsCdkDiffStepInfo.getImagePullPolicy());
    PluginDetails pluginDetails =
        getPluginDetails(usedPorts, awsCdkDiffStepInfo.getRunAsUser(), awsCdkDiffStepInfo.getResources(),
            awsCdkDiffStepInfo.getPrivileged(), getEnvironmentVariables(awsCdkDiffStepInfo), imageDetails);
    PluginCreationResponse response = PluginCreationResponse.newBuilder().setPluginDetails(pluginDetails).build();
    StepInfoProto stepInfoProto = getStepInfoProto(cdAbstractStepNode);

    return PluginCreationResponseWrapper.newBuilder().setResponse(response).setStepInfo(stepInfoProto).build();
  }

  @Override
  public boolean isSupported(String stepType) {
    return stepType.equals(StepSpecTypeConstants.AWS_CDK_DIFF);
  }

  private Map<String, String> getEnvironmentVariables(AwsCdkDiffStepInfo awsCdkDiffStepInfo) {
    ParameterField<Map<String, String>> envVariables = awsCdkDiffStepInfo.getEnvVariables();
    HashMap<String, String> environmentVariablesMap =
        awsCdkStepHelper.getCommonEnvVariables(getParameterFieldValue(awsCdkDiffStepInfo.getAppPath()),
            getParameterFieldValue(awsCdkDiffStepInfo.getCommandOptions()), envVariables);
    List<String> stackNames = getParameterFieldValue(awsCdkDiffStepInfo.getStackNames());

    if (isNotEmpty(stackNames)) {
      environmentVariablesMap.put(PLUGIN_AWS_CDK_STACK_NAMES, String.join(" ", stackNames));
    }
    environmentVariablesMap.put(PLUGIN_AWS_CDK_ACTION, DIFF);

    return environmentVariablesMap;
  }
}
