/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.manager;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.INIT_TIMEOUT;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.WingsException.USER;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.service.impl.AwsHelperService.AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL;
import static software.wings.service.impl.aws.model.AwsConstants.BASE_DELAY_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.MAX_BACKOFF_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.MAX_ERROR_RETRY_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.NULL_STR;
import static software.wings.service.impl.aws.model.AwsConstants.THROTTLED_BASE_DELAY_ACCOUNT_VARIABLE;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.util.AwsCallTracker;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AmazonClientSDKDefaultBackoffStrategy;
import software.wings.beans.AwsConfig;
import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.states.ManagerExecutionLogCallback;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.SetDesiredCapacityRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AwsHelperServiceManager {
  @Inject private EncryptionService encryptionService;
  @Inject private TimeLimiter timeLimiter;
  @Inject private AwsCallTracker tracker;
  @Inject private AwsApiHelperService awsApiHelperService;
  @Inject private AwsHelperService awsHelperService;

  public void setAutoScalingGroupCapacityAndWaitForInstancesReadyState(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String autoScalingGroupName, Integer desiredCapacity,
      ManagerExecutionLogCallback executionLogCallback, Integer autoScalingSteadyStateTimeout) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    AmazonAutoScalingClient amazonAutoScalingClient = null;
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(
                 awsHelperService.getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      amazonAutoScalingClient = closeableAmazonAutoScalingClient.getClient();
      executionLogCallback.saveExecutionLog(
          format("Set AutoScaling Group: [%s] desired capacity to [%s]", autoScalingGroupName, desiredCapacity));
      tracker.trackASGCall("Set ASG Desired Capacity");
      amazonAutoScalingClient.setDesiredCapacity(new SetDesiredCapacityRequest()
                                                     .withAutoScalingGroupName(autoScalingGroupName)
                                                     .withDesiredCapacity(desiredCapacity));
      executionLogCallback.saveExecutionLog("Successfully set desired capacity");
      waitForAllInstancesToBeReady(awsConfig, encryptionDetails, region, autoScalingGroupName, desiredCapacity,
          executionLogCallback, autoScalingSteadyStateTimeout);
    } catch (AmazonServiceException amazonServiceException) {
      awsHelperService.describeAutoScalingGroupActivities(
          amazonAutoScalingClient, autoScalingGroupName, new HashSet<>(), executionLogCallback, true);
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception setAutoScalingGroupCapacityAndWaitForInstancesReadyState", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  public void setAutoScalingGroupCapacityAndWaitForInstancesReadyState(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String autoScalingGroupName, Integer desiredCapacity,
      ManagerExecutionLogCallback executionLogCallback) {
    setAutoScalingGroupCapacityAndWaitForInstancesReadyState(
        awsConfig, encryptionDetails, region, autoScalingGroupName, desiredCapacity, executionLogCallback, 10);
  }

  private void waitForAllInstancesToBeReady(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String autoScalingGroupName, Integer desiredCount,
      ManagerExecutionLogCallback executionLogCallback, Integer autoScalingSteadyStateTimeout) {
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(
                 awsHelperService.getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(autoScalingSteadyStateTimeout), () -> {
        Set<String> completedActivities = new HashSet<>();
        while (true) {
          List<String> instanceIds = awsHelperService.listInstanceIdsFromAutoScalingGroup(
              awsConfig, encryptionDetails, region, autoScalingGroupName);
          awsHelperService.describeAutoScalingGroupActivities(closeableAmazonAutoScalingClient.getClient(),
              autoScalingGroupName, completedActivities, executionLogCallback, false);

          if (instanceIds.size() == desiredCount
              && allInstanceInReadyState(awsConfig, encryptionDetails, region, instanceIds, executionLogCallback)) {
            return true;
          }
          sleep(ofSeconds(AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL));
        }
      });
    } catch (UncheckedTimeoutException e) {
      executionLogCallback.saveExecutionLog(
          "Request timeout. AutoScaling group couldn't reach steady state", CommandExecutionStatus.FAILURE);
      throw new WingsException(INIT_TIMEOUT)
          .addParam("message", "Timed out waiting for all instances to be in running state");
    } catch (WingsException e) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      throw new InvalidRequestException("Error while waiting for all instances to be in running state",
          ExceptionMessageSanitizer.sanitizeException(e));
    }
    executionLogCallback.saveExecutionLog("AutoScaling group reached steady state");
  }

  private boolean allInstanceInReadyState(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, List<String> instanceIds, ManagerExecutionLogCallback executionLogCallback) {
    DescribeInstancesResult describeInstancesResult = awsHelperService.describeEc2Instances(
        awsConfig, encryptionDetails, region, new DescribeInstancesRequest().withInstanceIds(instanceIds));
    boolean allRunning = instanceIds.isEmpty()
        || describeInstancesResult.getReservations()
               .stream()
               .flatMap(reservation -> reservation.getInstances().stream())
               .allMatch(instance -> instance.getState().getName().equals("running"));
    if (!allRunning) {
      Map<String, Long> instanceStateCountMap =
          describeInstancesResult.getReservations()
              .stream()
              .flatMap(reservation -> reservation.getInstances().stream())
              .collect(groupingBy(instance -> instance.getState().getName(), counting()));
      executionLogCallback.saveExecutionLog("Waiting for instances to be in running state. "
          + Joiner.on(",").withKeyValueSeparator("=").join(instanceStateCountMap));
    }
    return allRunning;
  }

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
    log.info("Using Amazon SDK default backoff strategy with account level values: {}", sdkDefaultBackoffStrategy);
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
