/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.manager;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.exception.InvalidRequestException;

import software.wings.service.impl.aws.model.AwsResponse;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import software.wings.sm.ExecutionContext;

import static software.wings.service.impl.aws.model.AwsConstants.BASE_DELAY_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.MAX_BACKOFF_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.MAX_ERROR_RETRY_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.NULL_STR;
import static software.wings.service.impl.aws.model.AwsConstants.THROTTLED_BASE_DELAY_ACCOUNT_VARIABLE;

import static org.apache.commons.lang3.StringUtils.isBlank;

import software.wings.beans.AmazonClientSDKDefaultBackoffStrategy;

import com.google.common.annotations.VisibleForTesting;

import software.wings.beans.AwsConfig;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AwsHelperServiceManager {
  void validateDelegateSuccessForSyncTask(DelegateResponseData notifyResponseData) {
    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      throw new InvalidRequestException(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage(), USER);
    } else if (notifyResponseData instanceof RemoteMethodReturnValueData) {
      throw new InvalidRequestException(
              getMessage(((RemoteMethodReturnValueData) notifyResponseData).getException()), USER);
    } else if (!(notifyResponseData instanceof AwsResponse)) {
      throw new InvalidRequestException(
              format("Unknown response from delegate: [%s]", notifyResponseData.getClass().getSimpleName()), USER);
    }
  }


  @VisibleForTesting
  public static void setAmazonClientSDKDefaultBackoffStrategyIfExists(ExecutionContext context, AwsConfig awsConfig) {
    if (!validateSDKDefaultBackoffStrategyAccountVariables(context)) {
      return;
    }

    AmazonClientSDKDefaultBackoffStrategy sdkDefaultBackoffStrategy =
            AmazonClientSDKDefaultBackoffStrategy.builder()
                    .baseDelayInMs(resolveAccountVariable(context, BASE_DELAY_ACCOUNT_VARIABLE))
                    .throttledBaseDelayInMs(resolveAccountVariable(context, THROTTLED_BASE_DELAY_ACCOUNT_VARIABLE))
                    .maxBackoffInMs(resolveAccountVariable(context, MAX_BACKOFF_ACCOUNT_VARIABLE))
                    .maxErrorRetry(resolveAccountVariable(context, MAX_ERROR_RETRY_ACCOUNT_VARIABLE))
                    .build();
    awsConfig.setAmazonClientSDKDefaultBackoffStrategy(sdkDefaultBackoffStrategy);
    log.info("Using Amazon SDK default backoff strategy with account level values: {}",
            sdkDefaultBackoffStrategy);
  }

  private static boolean validateSDKDefaultBackoffStrategyAccountVariables(ExecutionContext context) {
    if (isRenderedExpressionBlank(context, BASE_DELAY_ACCOUNT_VARIABLE)
            || isRenderedExpressionBlank(context, THROTTLED_BASE_DELAY_ACCOUNT_VARIABLE)
            || isRenderedExpressionBlank(context, MAX_BACKOFF_ACCOUNT_VARIABLE)
            || isRenderedExpressionBlank(context, MAX_ERROR_RETRY_ACCOUNT_VARIABLE)) {
      return false;
    }

    try {
      resolveAccountVariable(context, BASE_DELAY_ACCOUNT_VARIABLE);
      resolveAccountVariable(context, THROTTLED_BASE_DELAY_ACCOUNT_VARIABLE);
      resolveAccountVariable(context, MAX_BACKOFF_ACCOUNT_VARIABLE);
      resolveAccountVariable(context, MAX_ERROR_RETRY_ACCOUNT_VARIABLE);
    } catch (Exception ex) {
      log.error("Not valid account level backoff strategy variables, msg: {}", ex.getMessage());
      return false;
    }
    return true;
  }

  private static boolean isRenderedExpressionBlank(ExecutionContext context, final String expression) {
    String renderedExpression = context.renderExpression(expression);
    return isBlank(renderedExpression) || NULL_STR.equals(renderedExpression);
  }

  private static int resolveAccountVariable(ExecutionContext context, final String expression) {
    return Integer.parseInt(context.renderExpression(expression));
  }
}
