/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.serverless.container.steps.ServerlessDownloadManifestsStepHelper;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plugin.GitCloneStep;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class DownloadManifestsStepHelper {
  @Inject private OutcomeService outcomeService;
  @Inject private AwsSamDownloadManifestsStepHelper awsSamDownloadManifestsStepHelper;

  @Inject private ServerlessDownloadManifestsStepHelper serverlessDownloadManifestsStepHelper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  private static final String DEPLOYMENT_TYPE_NOT_SUPPORTED_EXCEPTION_MESSAGE =
      "Invalid kind of deployment type for Download Manifests Step";

  public AsyncExecutableResponse handleExecuteAsyncAfterRbac(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, GitCloneStep gitCloneStep) {
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    if (ServiceSpecType.AWS_SAM.equals(serviceOutcome.getType())) {
      checkSamFeatureFlagEnabled(ambiance);
      return awsSamDownloadManifestsStepHelper.executeAsyncAfterRbac(ambiance, inputPackage, gitCloneStep);
    } else if (ServiceSpecType.SERVERLESS_AWS_LAMBDA.equals(serviceOutcome.getType())) {
      checkServerlessV2FeatureFlagEnabled(ambiance);
      return serverlessDownloadManifestsStepHelper.executeAsyncAfterRbac(ambiance, inputPackage, gitCloneStep);
    } else {
      throw new InvalidRequestException(DEPLOYMENT_TYPE_NOT_SUPPORTED_EXCEPTION_MESSAGE, USER);
    }
  }

  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    if (ServiceSpecType.AWS_SAM.equals(serviceOutcome.getType())) {
      checkSamFeatureFlagEnabled(ambiance);
      return awsSamDownloadManifestsStepHelper.handleAsyncResponse(ambiance, responseDataMap);
    } else if (ServiceSpecType.SERVERLESS_AWS_LAMBDA.equals(serviceOutcome.getType())) {
      checkServerlessV2FeatureFlagEnabled(ambiance);
      return serverlessDownloadManifestsStepHelper.handleAsyncResponse(ambiance, responseDataMap);
    } else {
      throw new InvalidRequestException(DEPLOYMENT_TYPE_NOT_SUPPORTED_EXCEPTION_MESSAGE, USER);
    }
  }

  public PluginCreationResponseList getPluginInfoList(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    if (ServiceSpecType.AWS_SAM.equals(serviceOutcome.getType())) {
      checkSamFeatureFlagEnabled(ambiance);
      return awsSamDownloadManifestsStepHelper.getPluginInfoList(request, usedPorts, ambiance);
    } else if (ServiceSpecType.SERVERLESS_AWS_LAMBDA.equals(serviceOutcome.getType())) {
      checkServerlessV2FeatureFlagEnabled(ambiance);
      return serverlessDownloadManifestsStepHelper.getPluginInfoList(request, usedPorts, ambiance);
    } else {
      throw new InvalidRequestException(DEPLOYMENT_TYPE_NOT_SUPPORTED_EXCEPTION_MESSAGE, USER);
    }
  }

  public void checkSamFeatureFlagEnabled(Ambiance ambiance) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDP_AWS_SAM)) {
      throw new AccessDeniedException(
          "CDP_AWS_SAM FF is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }

  public void checkServerlessV2FeatureFlagEnabled(Ambiance ambiance) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_SERVERLESS_V2)) {
      throw new AccessDeniedException(
          "CDS_SERVERLESS_V2 FF is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }
}
