/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.custom;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.DelegateTaskRequest.DelegateTaskRequestBuilder;
import io.harness.cdng.expressionEvaluator.CustomScriptSecretExpressionEvaluator;
import io.harness.common.NGTaskType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Slf4j
public class CustomResourceServiceImpl implements CustomResourceService {
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Inject @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;
  @Inject ExceptionManager exceptionManager;
  @VisibleForTesting static final int timeoutInSecs = 30;

  @Override
  public List<BuildDetails> getBuilds(String script, String versionPath, String arrayPath, Map<String, String> inputs,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, int secretFunctor,
      List<TaskSelectorYaml> delegateSelector) {
    BaseNGAccess baseNGAccess = getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier);
    CustomScriptSecretExpressionEvaluator customScriptSecretExpressionEvaluator =
        new CustomScriptSecretExpressionEvaluator(script, secretFunctor);
    script = customScriptSecretExpressionEvaluator.renderExpression(
        script, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    CustomArtifactDelegateRequest customArtifactDelegateRequest = ArtifactDelegateRequestUtils.getCustomDelegateRequest(
        arrayPath, null, "Inline", ArtifactSourceType.CUSTOM_ARTIFACT, versionPath, script, Collections.emptyMap(),
        inputs, null, null, timeoutInSecs, accountIdentifier);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
          executeSyncTask(customArtifactDelegateRequest, ArtifactTaskType.GET_BUILDS, baseNGAccess,
              "Custom Get Build task failure due to error", secretFunctor, delegateSelector);
      return artifactTaskExecutionResponse.getBuildDetails();
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_CUSTOM_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  private ArtifactTaskExecutionResponse executeSyncTask(CustomArtifactDelegateRequest customArtifactDelegateRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage, int secretFunctor,
      List<TaskSelectorYaml> delegateSelector) {
    DelegateResponseData responseData =
        getResponseData(ngAccess, customArtifactDelegateRequest, taskType, secretFunctor, delegateSelector);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(BaseNGAccess ngAccess, CustomArtifactDelegateRequest delegateRequest,
      ArtifactTaskType artifactTaskType, int secretFunctor, List<TaskSelectorYaml> delegateSelector) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(delegateRequest)
                                                        .build();
    List<TaskSelector> delegateSelectors = EmptyPredicate.isNotEmpty(delegateSelector)
        ? delegateSelector.stream().map(TaskSelectorYaml::toTaskSelector).collect(Collectors.toList())
        : Collections.EMPTY_LIST;
    DelegateTaskRequestBuilder delegateTaskRequestBuilder =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.CUSTOM_ARTIFACT_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .expressionFunctorToken(secretFunctor)
            .taskSetupAbstraction("ng", "true")
            .taskSelectors(EmptyPredicate.isNotEmpty(delegateSelector)
                    ? delegateSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toList())
                    : Collections.EMPTY_LIST);
    if (EmptyPredicate.isEmpty(ngAccess.getOrgIdentifier())
        && EmptyPredicate.isEmpty(ngAccess.getProjectIdentifier())) {
      delegateTaskRequestBuilder.taskSetupAbstraction("owner", ngAccess.getAccountIdentifier());
    } else if (EmptyPredicate.isEmpty(ngAccess.getProjectIdentifier())
        && EmptyPredicate.isNotEmpty(ngAccess.getOrgIdentifier())) {
      delegateTaskRequestBuilder.taskSetupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
          .taskSetupAbstraction("owner", ngAccess.getOrgIdentifier());
    } else {
      delegateTaskRequestBuilder.taskSetupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
          .taskSetupAbstraction("projectIdentifier", ngAccess.getProjectIdentifier())
          .taskSetupAbstraction("owner", ngAccess.getOrgIdentifier() + "/" + ngAccess.getProjectIdentifier());
    }
    final DelegateTaskRequest delegateTaskRequest = delegateTaskRequestBuilder.build();
    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  private ArtifactTaskExecutionResponse getTaskExecutionResponse(
      DelegateResponseData responseData, String ifFailedMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactServerException(ifFailedMessage + " - " + errorNotifyResponseData.getErrorMessage());
    } else if (responseData instanceof RemoteMethodReturnValueData) {
      RemoteMethodReturnValueData remoteMethodReturnValueData = (RemoteMethodReturnValueData) responseData;
      if (remoteMethodReturnValueData.getException() instanceof InvalidRequestException) {
        throw(InvalidRequestException)(remoteMethodReturnValueData.getException());
      } else {
        throw new ArtifactServerException(
            "Unexpected error during authentication to docker server " + remoteMethodReturnValueData.getReturnValue(),
            USER);
      }
    }
    ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) responseData;
    if (artifactTaskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactServerException(ifFailedMessage + " - " + artifactTaskResponse.getErrorMessage()
          + " with error code: " + artifactTaskResponse.getErrorCode());
    }
    return artifactTaskResponse.getArtifactTaskExecutionResponse();
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
