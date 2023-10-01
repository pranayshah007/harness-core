/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.BOOTSTRAP;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_ACTION;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.provision.awscdk.AwsCdkBootstrapStepInfo;
import io.harness.cdng.provision.awscdk.AwsCdkHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.StepInfoProto;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(HarnessTeam.CDP)
public class AwsCdkBootstrapPluginInfoProvider extends AbstractPluginInfoProvider {
  @Inject private AwsCdkHelper awsCdkStepHelper;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    CdAbstractStepNode cdAbstractStepNode = getCdAbstractStepNode(request.getType(), request.getStepJsonNode());
    AwsCdkBootstrapStepInfo awsCdkBootstrapStepInfo = (AwsCdkBootstrapStepInfo) cdAbstractStepNode.getStepSpecType();
    Map<String, String> envVariables = awsCdkStepHelper.getCommonEnvVariables(
        getParameterFieldValue(awsCdkBootstrapStepInfo.getAppPath()),
        getParameterFieldValue(awsCdkBootstrapStepInfo.getCommandOptions()), awsCdkBootstrapStepInfo.getEnvVariables());
    envVariables.put(PLUGIN_AWS_CDK_ACTION, BOOTSTRAP);
    ImageDetails imageDetails = PluginInfoProviderHelper.getImageDetails(awsCdkBootstrapStepInfo.getConnectorRef(),
        awsCdkBootstrapStepInfo.getImage(), awsCdkBootstrapStepInfo.getImagePullPolicy());
    PluginDetails pluginDetails =
        getPluginDetails(usedPorts, awsCdkBootstrapStepInfo.getRunAsUser(), awsCdkBootstrapStepInfo.getResources(),
            awsCdkBootstrapStepInfo.getPrivileged(), envVariables, imageDetails, false);
    PluginCreationResponse response = PluginCreationResponse.newBuilder().setPluginDetails(pluginDetails).build();
    StepInfoProto stepInfoProto = getStepInfoProto(cdAbstractStepNode);

    return PluginCreationResponseWrapper.newBuilder().setResponse(response).setStepInfo(stepInfoProto).build();
  }

  @Override
  public boolean isSupported(String stepType) {
    return stepType.equals(StepSpecTypeConstants.AWS_CDK_BOOTSTRAP);
  }
}
