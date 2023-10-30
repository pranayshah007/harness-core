/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.provision.awscdk.AwsCdkConfigDAL;
import io.harness.cdng.provision.awscdk.AwsCdkRollbackStepInfo;
import io.harness.cdng.provision.awscdk.beans.AwsCdkConfig;
import io.harness.cdng.provision.awscdk.beans.ContainerResourceConfig;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(HarnessTeam.CDP)
public class AwsCdkRollbackPluginInfoProvider extends AbstractPluginInfoProvider {
  @Inject private AwsCdkConfigDAL awsCdkConfigDAL;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode = getCdAbstractStepNode(request.getType(), stepJsonNode);
    AwsCdkRollbackStepInfo awsCdkRollbackStepInfo = (AwsCdkRollbackStepInfo) cdAbstractStepNode.getStepSpecType();
    AwsCdkConfig awsCdkConfig = awsCdkConfigDAL.getRollbackAwsCdkConfig(
        ambiance, getParameterFieldValue(awsCdkRollbackStepInfo.getProvisionerIdentifier()));

    if (awsCdkConfig == null) {
      return PluginCreationResponseWrapper.newBuilder().setShouldSkip(true).build();
    }

    ImageDetails imageDetails = PluginInfoProviderHelper.getImageDetails(
        ParameterField.<String>builder().value(awsCdkConfig.getConnectorRef()).build(),
        ParameterField.<String>builder().value(awsCdkConfig.getImage()).build(),
        ParameterField.<ImagePullPolicy>builder().value(awsCdkConfig.getImagePullPolicy()).build());
    Map<String, String> envVariables = getEnvironmentVariables(awsCdkConfig, awsCdkRollbackStepInfo);
    PluginDetails pluginDetails =
        getPluginDetails(usedPorts, ParameterField.<Integer>builder().value(awsCdkConfig.getRunAsUser()).build(),
            getResources(awsCdkConfig), ParameterField.<Boolean>builder().value(awsCdkConfig.getPrivileged()).build(),
            envVariables, imageDetails, false);

    PluginCreationResponse response = PluginCreationResponse.newBuilder().setPluginDetails(pluginDetails).build();
    StepInfoProto stepInfoProto = getStepInfoProto(cdAbstractStepNode);

    return PluginCreationResponseWrapper.newBuilder().setResponse(response).setStepInfo(stepInfoProto).build();
  }

  private ContainerResource getResources(AwsCdkConfig awsCdkConfig) {
    ContainerResourceConfig containerResource = awsCdkConfig.getResources();
    if (containerResource != null) {
      ContainerResource.Limits requests = null;
      ContainerResource.Limits limits = null;
      if (containerResource.getRequests() != null) {
        requests =
            ContainerResource.Limits.builder()
                .memory(ParameterField.<String>builder().value(containerResource.getRequests().getMemory()).build())
                .cpu(ParameterField.<String>builder().value(containerResource.getRequests().getCpu()).build())
                .build();
      }
      if (containerResource.getLimits() != null) {
        limits = ContainerResource.Limits.builder()
                     .memory(ParameterField.<String>builder().value(containerResource.getLimits().getMemory()).build())
                     .cpu(ParameterField.<String>builder().value(containerResource.getLimits().getCpu()).build())
                     .build();
      }
      return ContainerResource.builder().requests(requests).limits(limits).build();
    }
    return null;
  }

  @Override
  public boolean isSupported(String stepType) {
    return StepSpecTypeConstants.AWS_CDK_ROLLBACK.equals(stepType);
  }

  private Map<String, String> getEnvironmentVariables(
      AwsCdkConfig awsCdkConfig, AwsCdkRollbackStepInfo awsCdkRollbackStepInfo) {
    Map<String, String> envVariables = awsCdkConfig.getEnvVariables();
    Map<String, String> rollbackEnvVariables = getParameterFieldValue(awsCdkRollbackStepInfo.getEnvVariables());
    if (isNotEmpty(rollbackEnvVariables)) {
      envVariables.putAll(rollbackEnvVariables);
    }

    return envVariables;
  }
}
