/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.cf.DeployStackRequest;
import io.harness.aws.cf.DeployStackResult;
import io.harness.aws.cf.Status;
import io.harness.concurrent.HTimeLimiter;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.logging.LogCallback;

import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.ChangeSetStatus;
import com.amazonaws.services.cloudformation.model.CreateChangeSetRequest;
import com.amazonaws.services.cloudformation.model.CreateChangeSetResult;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.CreateStackResult;
import com.amazonaws.services.cloudformation.model.DeleteChangeSetRequest;
import com.amazonaws.services.cloudformation.model.DeleteChangeSetResult;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeChangeSetRequest;
import com.amazonaws.services.cloudformation.model.DescribeChangeSetResult;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsResult;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.ExecuteChangeSetRequest;
import com.amazonaws.services.cloudformation.model.ExecuteChangeSetResult;
import com.amazonaws.services.cloudformation.model.GetTemplateSummaryRequest;
import com.amazonaws.services.cloudformation.model.GetTemplateSummaryResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.ParameterDeclaration;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackEvent;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import com.amazonaws.services.cloudformation.model.UpdateStackResult;
import com.amazonaws.services.cloudformation.waiters.AmazonCloudFormationWaiters;
import com.amazonaws.waiters.WaiterHandler;
import com.amazonaws.waiters.WaiterParameters;
import com.amazonaws.waiters.WaiterTimedOutException;
import com.amazonaws.waiters.WaiterUnrecoverableException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class AWSCloudformationClientImpl implements AWSCloudformationClient {
  public static final List<String> END_STATUS_LIST =
      Arrays.asList(ChangeSetStatus.CREATE_COMPLETE.toString(), ChangeSetStatus.FAILED.toString(),
          ChangeSetStatus.DELETE_COMPLETE.toString(), ChangeSetStatus.DELETE_FAILED.toString());
  @Inject AwsApiHelperService awsApiHelperService;
  @Inject AwsCloudformationPrintHelper awsCloudformationPrintHelper;
  @Inject private AwsCallTracker tracker;
  @Inject private TimeLimiter timeLimiter;

  @Override
  public Optional<Stack> getStack(
      String region, DescribeStacksRequest describeStacksRequest, AwsInternalConfig awsConfig) {
    List<Stack> stacks = getAllStacks(region, describeStacksRequest, awsConfig);
    return isNotEmpty(stacks) ? Optional.of(stacks.get(0)) : Optional.empty();
  }

  @Override
  public List<Stack> getAllStacks(
      String region, DescribeStacksRequest describeStacksRequest, AwsInternalConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      List<Stack> stacks = new ArrayList<>();
      String nextToken = null;
      do {
        describeStacksRequest.withNextToken(nextToken);
        tracker.trackCFCall("Describe Stacks");
        DescribeStacksResult result =
            closeableAmazonCloudFormationClient.getClient().describeStacks(describeStacksRequest);
        nextToken = result.getNextToken();
        stacks.addAll(result.getStacks());
      } while (nextToken != null);
      return stacks;
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception getAllStacks", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return emptyList();
  }

  @Override
  public void deleteStack(String region, DeleteStackRequest deleteStackRequest, AwsInternalConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      String msg = "# Starting to delete stack:" + deleteStackRequest.getStackName();
      tracker.trackCFCall(msg);
      closeableAmazonCloudFormationClient.getClient().deleteStack(deleteStackRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception deleteStack", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public List<StackResource> getAllStackResources(
      String region, DescribeStackResourcesRequest describeStackResourcesRequest, AwsInternalConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCFCall("Describe Stack Resources");
      DescribeStackResourcesResult result =
          closeableAmazonCloudFormationClient.getClient().describeStackResources(describeStackResourcesRequest);
      return result.getStackResources();
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception retrieving StackResources", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return emptyList();
  }

  @Override
  public List<StackEvent> getAllStackEvents(String region, DescribeStackEventsRequest describeStackEventsRequest,
      AwsInternalConfig awsConfig, long lastStackEventsTs) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      List<StackEvent> stacksEvents = new ArrayList<>();
      String nextToken = null;
      boolean oldStackEventExists;
      do {
        describeStackEventsRequest.withNextToken(nextToken);
        tracker.trackCFCall("Describe Stack Events");
        DescribeStackEventsResult result =
            closeableAmazonCloudFormationClient.getClient().describeStackEvents(describeStackEventsRequest);
        nextToken = result.getNextToken();
        stacksEvents.addAll(result.getStackEvents());

        oldStackEventExists =
            result.getStackEvents().stream().anyMatch(event -> event.getTimestamp().getTime() < lastStackEventsTs);
        nextToken = oldStackEventExists ? null : nextToken;

      } while (nextToken != null);
      return stacksEvents;
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception getAllStackEvents", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return emptyList();
  }

  @Override
  public CreateStackResult createStack(
      String region, CreateStackRequest createStackRequest, AwsInternalConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCFCall("Create Stack");
      return closeableAmazonCloudFormationClient.getClient().createStack(createStackRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception createStack", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return new CreateStackResult();
  }

  @Override
  public UpdateStackResult updateStack(
      String region, UpdateStackRequest updateStackRequest, AwsInternalConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCFCall("Update Stack");
      return closeableAmazonCloudFormationClient.getClient().updateStack(updateStackRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception updateStack", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return new UpdateStackResult();
  }

  @Override
  public DeployStackResult deployStack(String region, DeployStackRequest deployStackRequest,
      AwsInternalConfig awsConfig, Duration duration, LogCallback logCallback) {
    String changeSetName = deployStackRequest.getStackName() + "-" + Instant.now().toEpochMilli();
    String changeSetArn =
        createChangeSetAndWait(region, deployStackRequest, awsConfig, changeSetName, duration, logCallback);

    DescribeChangeSetResult describeChangeSetResult =
        describeChangeSet(region, deployStackRequest, awsConfig, changeSetName);

    DeployStackResult result;

    if (ChangeSetStatus.FAILED.toString().equals(describeChangeSetResult.getStatus())) {
      deleteChangeSet(region, deployStackRequest, awsConfig, changeSetName, logCallback);
      if (null != describeChangeSetResult.getStatusReason()
          && (describeChangeSetResult.getStatusReason().contains("The submitted information didn't contain changes.")
              || describeChangeSetResult.getStatusReason().contains("No updates are to be performed"))) {
        logCallback.saveExecutionLog("No updates are to be performed");
        result = DeployStackResult.builder()
                     .noUpdatesToPerform(true)
                     .status(Status.SUCCESS)
                     .statusReason(describeChangeSetResult.getStatusReason())
                     .build();
      } else {
        logCallback.saveExecutionLog(format("Failed to create the Change Set: %s Status: %s. Reason: %s", changeSetName,
            describeChangeSetResult.getStatus(), describeChangeSetResult.getStatusReason()));
        result = DeployStackResult.builder()
                     .noUpdatesToPerform(false)
                     .status(Status.FAILURE)
                     .statusReason(describeChangeSetResult.getStatusReason())
                     .build();
      }
    } else if (ChangeSetStatus.CREATE_COMPLETE.toString().equals(describeChangeSetResult.getStatus())) {
      logCallback.saveExecutionLog(format("Change Set created successfully with arn %s", changeSetArn));
      logCallback.saveExecutionLog(format("Executing Change Set %s", changeSetName));

      executeChangeSet(region,
          new ExecuteChangeSetRequest()
              .withChangeSetName(changeSetName)
              .withStackName(deployStackRequest.getStackName()),
          awsConfig);
      logCallback.saveExecutionLog(
          format("Execute Change Set request submitted for stack: %s", deployStackRequest.getStackName()));
      result = DeployStackResult.builder().noUpdatesToPerform(false).status(Status.SUCCESS).build();
    } else {
      logCallback.saveExecutionLog(
          format("Failed to create the Change Set due to unknown status: %s Status: %s. Reason: %s", changeSetName,
              describeChangeSetResult.getStatus(), describeChangeSetResult.getStatusReason()));
      result = DeployStackResult.builder()
                   .noUpdatesToPerform(false)
                   .status(Status.FAILURE)
                   .statusReason(describeChangeSetResult.getStatusReason())
                   .build();
    }
    return result;
  }

  private DescribeChangeSetResult describeChangeSet(
      String region, DeployStackRequest deployStackRequest, AwsInternalConfig awsConfig, String changeSetName) {
    DescribeChangeSetRequest describeChangeSetRequest = new DescribeChangeSetRequest()
                                                            .withChangeSetName(changeSetName)
                                                            .withStackName(deployStackRequest.getStackName());

    return describeChangeSet(region, describeChangeSetRequest, awsConfig);
  }

  private String createChangeSetAndWait(String region, DeployStackRequest deployStackRequest,
      AwsInternalConfig awsConfig, String changeSetName, Duration duration, LogCallback logCallback) {
    CreateChangeSetResult changeSetResult = createChangeSet(region, deployStackRequest, awsConfig, changeSetName);
    logCallback.saveExecutionLog(format("Create Change Set request submitted for stack: %s. Now polling for status.",
        deployStackRequest.getStackName()));

    String changeSetArn = changeSetResult.getId();

    try {
      HTimeLimiter.callInterruptible(timeLimiter, duration, () -> {
        while (true) {
          DescribeChangeSetResult result = describeChangeSet(region, deployStackRequest, awsConfig, changeSetArn);
          logCallback.saveExecutionLog(format(
              "Change Set [%s]: Status: %s, Reason: %s", changeSetName, result.getStatus(), result.getStatusReason()));
          if (changeSetCreated(result)) {
            break;
          }
          // Poll every 5 seconds. Change Set creation should be fast
          sleep(ofSeconds(5));
        }
        return true;
      });
    } catch (UncheckedTimeoutException e) {
      throw new TimeoutException("Timed out waiting for Change Set to be created", "Timeout", e, WingsException.SRE);
    } catch (RuntimeException ex) {
      String errorMessage = ExceptionUtils.getMessage(ex);
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw ex;
    } catch (Exception e) {
      throw new InvalidRequestException("Error while waiting for Change Set to be created", e);
    }
    return changeSetArn;
  }

  private void deleteChangeSet(String region, DeployStackRequest deployStackRequest, AwsInternalConfig awsConfig,
      String changeSetName, LogCallback logCallback) {
    logCallback.saveExecutionLog(format("Deleting Change Set %s", changeSetName));
    DeleteChangeSetRequest deleteChangeSetRequest =
        new DeleteChangeSetRequest().withChangeSetName(changeSetName).withStackName(deployStackRequest.getStackName());
    deleteChangeSet(region, deleteChangeSetRequest, awsConfig);
  }

  private CreateChangeSetResult createChangeSet(
      String region, DeployStackRequest deployStackRequest, AwsInternalConfig awsConfig, String changeSetName) {
    List<Parameter> parameters = new ArrayList<>();
    List<Tag> tags = new ArrayList<>();
    if (isNotEmpty(deployStackRequest.getParameters())) {
      deployStackRequest.getParameters().forEach(p
          -> parameters.add(new Parameter()
                                .withParameterKey(p.getParameterKey())
                                .withParameterValue(p.getParameterValue())
                                .withUsePreviousValue(p.getUsePreviousValue())
                                .withResolvedValue(p.getResolvedValue())));
    }

    if (isNotEmpty(deployStackRequest.getTags())) {
      deployStackRequest.getTags().forEach(t -> tags.add(new Tag().withKey(t.getKey()).withValue(t.getValue())));
    }

    return createChangeSet(region,
        new CreateChangeSetRequest()
            .withChangeSetName(changeSetName)
            .withStackName(deployStackRequest.getStackName())
            .withParameters(parameters)
            .withCapabilities(deployStackRequest.getCapabilities())
            .withTags(tags)
            .withRoleARN(deployStackRequest.getRoleARN())
            .withTemplateBody(deployStackRequest.getTemplateBody())
            .withTemplateURL(deployStackRequest.getTemplateURL()),
        awsConfig);
  }

  private boolean changeSetCreated(DescribeChangeSetResult describeChangeSet) {
    return END_STATUS_LIST.contains(describeChangeSet.getStatus());
  }

  @VisibleForTesting
  CreateChangeSetResult createChangeSet(
      String region, CreateChangeSetRequest createChangeSetRequest, AwsInternalConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCFCall("Create Change Set");
      return closeableAmazonCloudFormationClient.getClient().createChangeSet(createChangeSetRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception Create Change Set", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return null;
  }

  @VisibleForTesting
  DescribeChangeSetResult describeChangeSet(
      String region, DescribeChangeSetRequest describeChangeSetRequest, AwsInternalConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCFCall("Describe Change Set");
      return closeableAmazonCloudFormationClient.getClient().describeChangeSet(describeChangeSetRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception Describe Change Set", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return null;
  }

  @VisibleForTesting
  ExecuteChangeSetResult executeChangeSet(
      String region, ExecuteChangeSetRequest executeChangeSetRequest, AwsInternalConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCFCall("Execute Change Set");
      return closeableAmazonCloudFormationClient.getClient().executeChangeSet(executeChangeSetRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception Execute Change Set", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return null;
  }

  @VisibleForTesting
  DeleteChangeSetResult deleteChangeSet(
      String region, DeleteChangeSetRequest deleteChangeSetRequest, AwsInternalConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCFCall("Delete Change Set");
      return closeableAmazonCloudFormationClient.getClient().deleteChangeSet(deleteChangeSetRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception Delete Change Set", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return null;
  }

  @Override
  public DescribeStacksResult describeStacks(
      String region, DescribeStacksRequest describeStacksRequest, AwsInternalConfig awsConfig) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      tracker.trackCFCall("Describe Stacks");
      return closeableAmazonCloudFormationClient.getClient().describeStacks(describeStacksRequest);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception describeStacks", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return new DescribeStacksResult();
  }

  @Override
  public void waitForStackDeletionCompleted(DescribeStacksRequest describeStacksRequest, AwsInternalConfig awsConfig,
      String region, LogCallback logCallback, long stackEventsTs) {
    long lastStackEventsTs = stackEventsTs;
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      AmazonCloudFormationWaiters waiter = getAmazonCloudFormationWaiter(closeableAmazonCloudFormationClient);
      WaiterParameters<DescribeStacksRequest> parameters = new WaiterParameters<>(describeStacksRequest);
      parameters = parameters.withRequest(describeStacksRequest);
      Future future = waiter.stackDeleteComplete().runAsync(parameters, new WaiterHandler() {
        @Override
        public void onWaitSuccess(AmazonWebServiceRequest amazonWebServiceRequest) {
          logCallback.saveExecutionLog("Stack deletion completed");
        }
        @Override
        public void onWaitFailure(Exception e) {
          logCallback.saveExecutionLog(format("Stack deletion failed: %s", e.getMessage()));
        }
      });
      while (!future.isDone()) {
        sleep(ofSeconds(10));
        List<StackEvent> stackEvents = getAllStackEvents(region,
            new DescribeStackEventsRequest().withStackName(describeStacksRequest.getStackName()), awsConfig,
            lastStackEventsTs);
        lastStackEventsTs = awsCloudformationPrintHelper.printStackEvents(stackEvents, lastStackEventsTs, logCallback);
      }
      future.get();
      List<StackResource> stackResources = getAllStackResources(
          region, new DescribeStackResourcesRequest().withStackName(describeStacksRequest.getStackName()), awsConfig);
      awsCloudformationPrintHelper.printStackResources(stackResources, logCallback);
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (WaiterUnrecoverableException | WaiterTimedOutException waiterUnrecoverableException) {
      throw new InvalidRequestException(
          ExceptionUtils.getMessage(waiterUnrecoverableException), waiterUnrecoverableException);
    } catch (Exception e) {
      log.error("Exception deleting stack ", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public List<ParameterDeclaration> getParamsData(
      AwsInternalConfig awsConfig, String region, String data, AwsCFTemplatesType awsCFTemplatesType) {
    try (CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient =
             new CloseableAmazonWebServiceClient(getAmazonCloudFormationClient(Regions.fromName(region), awsConfig))) {
      GetTemplateSummaryRequest request = new GetTemplateSummaryRequest();
      if (AwsCFTemplatesType.S3 == awsCFTemplatesType) {
        request.withTemplateURL(normalizeS3TemplatePath(data));
      } else {
        request.withTemplateBody(data);
      }
      tracker.trackCFCall("Get Template Summary");
      GetTemplateSummaryResult result = closeableAmazonCloudFormationClient.getClient().getTemplateSummary(request);
      List<ParameterDeclaration> parameters = result.getParameters();
      if (isNotEmpty(parameters)) {
        return parameters;
      }
    } catch (AmazonServiceException amazonServiceException) {
      awsApiHelperService.handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      awsApiHelperService.handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      log.error("Exception getParamsData", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return emptyList();
  }

  @VisibleForTesting
  AmazonCloudFormationClient getAmazonCloudFormationClient(Regions region, AwsInternalConfig awsConfig) {
    AmazonCloudFormationClientBuilder builder = AmazonCloudFormationClientBuilder.standard().withRegion(region);
    awsApiHelperService.attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonCloudFormationClient) builder.build();
  }

  @VisibleForTesting
  AmazonCloudFormationWaiters getAmazonCloudFormationWaiter(
      CloseableAmazonWebServiceClient<AmazonCloudFormationClient> closeableAmazonCloudFormationClient) {
    return new AmazonCloudFormationWaiters(closeableAmazonCloudFormationClient.getClient());
  }

  private String normalizeS3TemplatePath(String s3Path) {
    String normalizedS3TemplatePath = s3Path;
    if (isNotEmpty(normalizedS3TemplatePath) && normalizedS3TemplatePath.contains("+")) {
      normalizedS3TemplatePath = s3Path.replaceAll("\\+", "%20");
    }
    return normalizedS3TemplatePath;
  }
}
