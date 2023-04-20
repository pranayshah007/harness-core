/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.v2.ecs;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.AWS_ACCESS_DENIED;
import static io.harness.eraro.ErrorCode.AWS_CLUSTER_NOT_FOUND;
import static io.harness.eraro.ErrorCode.AWS_ECS_CLIENT_ERROR;
import static io.harness.eraro.ErrorCode.AWS_ECS_ERROR;
import static io.harness.eraro.ErrorCode.AWS_ECS_SERVICE_NOT_ACTIVE;
import static io.harness.eraro.ErrorCode.AWS_SERVICE_NOT_FOUND;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.util.EcsNGUtils;
import io.harness.aws.v2.AwsClientHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;

import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.retry.backoff.FixedDelayBackoffStrategy;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.applicationautoscaling.model.DeleteScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DeleteScalingPolicyResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DeregisterScalableTargetResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetResponse;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AccessDeniedException;
import software.amazon.awssdk.services.ecs.model.ClientException;
import software.amazon.awssdk.services.ecs.model.ClusterNotFoundException;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.DeleteServiceRequest;
import software.amazon.awssdk.services.ecs.model.DeleteServiceResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.EcsException;
import software.amazon.awssdk.services.ecs.model.ListServicesRequest;
import software.amazon.awssdk.services.ecs.model.ListServicesResponse;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.ServiceNotActiveException;
import software.amazon.awssdk.services.ecs.model.ServiceNotFoundException;
import software.amazon.awssdk.services.ecs.model.TagResourceRequest;
import software.amazon.awssdk.services.ecs.model.TagResourceResponse;
import software.amazon.awssdk.services.ecs.model.UntagResourceRequest;
import software.amazon.awssdk.services.ecs.model.UntagResourceResponse;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;
import software.amazon.awssdk.services.ecs.waiters.EcsWaiter;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class EcsV2ClientImpl extends AwsClientHelper implements EcsV2Client {
  @Override
  public CreateServiceResponse createService(
      AwsInternalConfig awsConfig, CreateServiceRequest createServiceRequest, String region) {
    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.createService(createServiceRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return CreateServiceResponse.builder().build();
  }

  @Override
  public UpdateServiceResponse updateService(
      AwsInternalConfig awsConfig, UpdateServiceRequest updateServiceRequest, String region) {
    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.updateService(updateServiceRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return UpdateServiceResponse.builder().build();
  }

  @Override
  public DeleteServiceResponse deleteService(
      AwsInternalConfig awsConfig, DeleteServiceRequest deleteServiceRequest, String region) {
    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.deleteService(deleteServiceRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DeleteServiceResponse.builder().build();
  }

  @Override
  public RegisterTaskDefinitionResponse createTask(
      AwsInternalConfig awsConfig, RegisterTaskDefinitionRequest registerTaskDefinitionRequest, String region) {
    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.registerTaskDefinition(registerTaskDefinitionRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return RegisterTaskDefinitionResponse.builder().build();
  }

  @Override
  public DescribeTaskDefinitionResponse describeTaskDefinition(
      AwsInternalConfig awsConfig, DescribeTaskDefinitionRequest describeTaskDefinitionRequest, String region) {
    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.describeTaskDefinition(describeTaskDefinitionRequest);
    } catch (Exception exception) {
      if (exception instanceof ClientException) {
        if (((ClientException) exception)
                .awsErrorDetails()
                .errorMessage()
                .equals("Unable to describe task definition.")) {
          throw NestedExceptionUtils.hintWithExplanationException(format("Please check the following inputs\n"
                                                                      + " Task Definition\n"
                                                                      + " Region\n"),
              format("Invalid Ecs Task Definition [%s] in region [%s] ", describeTaskDefinitionRequest.taskDefinition(),
                  region),
              new InvalidRequestException(format("Invalid Ecs Task Definition [%s] in region [%s] ",
                                              describeTaskDefinitionRequest.taskDefinition(), region),
                  exception));
        }
      }
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DescribeTaskDefinitionResponse.builder().build();
  }

  @Override
  public WaiterResponse<DescribeServicesResponse> ecsServiceInactiveStateCheck(AwsInternalConfig awsConfig,
      DescribeServicesRequest describeServicesRequest, String region, int serviceInactiveStateTimeout) {
    // Polling interval of 10 sec with total waiting done till a timeout of <serviceSteadyStateTimeout> min
    int delayInSeconds = 10;
    int maxAttempts = (int) TimeUnit.MINUTES.toSeconds(serviceInactiveStateTimeout) / delayInSeconds;
    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region);
         EcsWaiter ecsWaiter = getEcsWaiter(ecsClient, delayInSeconds, maxAttempts)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsWaiter.waitUntilServicesInactive(describeServicesRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return null;
  }

  @Override
  public RegisterScalableTargetResponse registerScalableTarget(
      AwsInternalConfig awsConfig, RegisterScalableTargetRequest registerScalableTargetRequest, String region) {
    try (ApplicationAutoScalingClient applicationAutoScalingClient =
             getApplicationAutoScalingClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return applicationAutoScalingClient.registerScalableTarget(registerScalableTargetRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return RegisterScalableTargetResponse.builder().build();
  }

  @Override
  public DeregisterScalableTargetResponse deregisterScalableTarget(
      AwsInternalConfig awsConfig, DeregisterScalableTargetRequest deregisterScalableTargetRequest, String region) {
    try (ApplicationAutoScalingClient applicationAutoScalingClient =
             getApplicationAutoScalingClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return applicationAutoScalingClient.deregisterScalableTarget(deregisterScalableTargetRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DeregisterScalableTargetResponse.builder().build();
  }

  @Override
  public PutScalingPolicyResponse attachScalingPolicy(
      AwsInternalConfig awsConfig, PutScalingPolicyRequest putScalingPolicyRequest, String region) {
    try (ApplicationAutoScalingClient applicationAutoScalingClient =
             getApplicationAutoScalingClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return applicationAutoScalingClient.putScalingPolicy(putScalingPolicyRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return PutScalingPolicyResponse.builder().build();
  }

  @Override
  public DeleteScalingPolicyResponse deleteScalingPolicy(
      AwsInternalConfig awsConfig, DeleteScalingPolicyRequest deleteScalingPolicyRequest, String region) {
    try (ApplicationAutoScalingClient applicationAutoScalingClient =
             getApplicationAutoScalingClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return applicationAutoScalingClient.deleteScalingPolicy(deleteScalingPolicyRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DeleteScalingPolicyResponse.builder().build();
  }

  @Override
  public DescribeScalableTargetsResponse listScalableTargets(
      AwsInternalConfig awsConfig, DescribeScalableTargetsRequest describeScalableTargetsRequest, String region) {
    try (ApplicationAutoScalingClient applicationAutoScalingClient =
             getApplicationAutoScalingClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return applicationAutoScalingClient.describeScalableTargets(describeScalableTargetsRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DescribeScalableTargetsResponse.builder().build();
  }

  @Override
  public DescribeScalingPoliciesResponse listScalingPolicies(
      AwsInternalConfig awsConfig, DescribeScalingPoliciesRequest describeScalingPoliciesRequest, String region) {
    try (ApplicationAutoScalingClient applicationAutoScalingClient =
             getApplicationAutoScalingClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return applicationAutoScalingClient.describeScalingPolicies(describeScalingPoliciesRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DescribeScalingPoliciesResponse.builder().build();
  }

  @Override
  public DescribeServicesResponse describeService(
      AwsInternalConfig awsConfig, String clusterName, String serviceName, String region) {
    DescribeServicesRequest describeServicesRequest =
        DescribeServicesRequest.builder().services(Collections.singletonList(serviceName)).cluster(clusterName).build();

    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.describeServices(describeServicesRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DescribeServicesResponse.builder().build();
  }

  @Override
  public ListTasksResponse listTaskArns(AwsInternalConfig awsConfig, ListTasksRequest listTasksRequest, String region) {
    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.listTasks(listTasksRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return ListTasksResponse.builder().build();
  }

  @Override
  public DescribeTasksResponse getTasks(
      AwsInternalConfig awsConfig, String clusterName, List<String> taskArns, String region) {
    DescribeTasksRequest describeTasksRequest =
        DescribeTasksRequest.builder().cluster(clusterName).tasks(taskArns).build();
    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.describeTasks(describeTasksRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DescribeTasksResponse.builder().build();
  }

  @Override
  public RunTaskResponse runTask(AwsInternalConfig awsConfig, RunTaskRequest runTaskRequest, String region) {
    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.runTask(runTaskRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return RunTaskResponse.builder().build();
  }

  private EcsWaiter getEcsWaiter(
      software.amazon.awssdk.services.ecs.EcsClient ecsClient, int delayInSeconds, int maxAttempts) {
    return EcsWaiter.builder()
        .client(ecsClient)
        .overrideConfiguration(
            WaiterOverrideConfiguration.builder()
                .backoffStrategy(FixedDelayBackoffStrategy.create(Duration.ofSeconds(delayInSeconds)))
                .maxAttempts(maxAttempts)
                .build())
        .build();
  }

  private ApplicationAutoScalingClient getApplicationAutoScalingClient(AwsInternalConfig awsConfig, String region) {
    return ApplicationAutoScalingClient.builder()
        .credentialsProvider(getAwsCredentialsProvider(awsConfig))
        .region(Region.of(region))
        .overrideConfiguration(getClientOverrideFromBackoffOverride(awsConfig))
        .build();
  }

  @Override
  public SdkClient getClient(AwsInternalConfig awsConfig, String region) {
    return software.amazon.awssdk.services.ecs.EcsClient.builder()
        .credentialsProvider(getAwsCredentialsProvider(awsConfig))
        .region(Region.of(region))
        .overrideConfiguration(getClientOverrideFromBackoffOverride(awsConfig))
        .build();
  }

  @Override
  public String client() {
    return "ECS";
  }

  @Override
  public void handleClientServiceException(AwsServiceException awsServiceException) {
    if (awsServiceException instanceof ClusterNotFoundException) {
      throw new InvalidRequestException(awsServiceException.getMessage(), AWS_CLUSTER_NOT_FOUND, USER);
    } else if (awsServiceException instanceof ServiceNotFoundException) {
      String errorMessage = awsServiceException.getMessage();
      errorMessage = StringUtils.replace(errorMessage, "null", EcsNGUtils.ECS_SERVICE_NOT_FOUND_ERROR_MESSAGE);
      throw new InvalidRequestException(errorMessage, awsServiceException, AWS_SERVICE_NOT_FOUND, USER);
    } else if (awsServiceException instanceof ServiceNotActiveException) {
      throw new InvalidRequestException(awsServiceException.getMessage(), AWS_ECS_SERVICE_NOT_ACTIVE, USER);
    } else if (awsServiceException instanceof AccessDeniedException) {
      throw new InvalidRequestException(awsServiceException.getMessage(), AWS_ACCESS_DENIED, USER);
    } else if (awsServiceException instanceof ClientException) {
      throw new InvalidRequestException(awsServiceException.getMessage(), AWS_ECS_CLIENT_ERROR, USER);
    } else if (awsServiceException instanceof EcsException) {
      throw new InvalidRequestException(awsServiceException.getMessage(), AWS_ECS_ERROR, USER);
    }
    throw new InvalidRequestException(awsServiceException.getMessage(), awsServiceException, USER);
  }

  @Override
  public ListServicesResponse listServices(
      AwsInternalConfig awsConfig, ListServicesRequest listServicesRequest, String region) {
    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.listServices(listServicesRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return ListServicesResponse.builder().build();
  }

  @Override
  public DescribeServicesResponse describeServices(
      AwsInternalConfig awsConfig, DescribeServicesRequest describeServicesRequest, String region) {
    try (EcsClient ecsClient = (EcsClient) getClient(awsConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.describeServices(describeServicesRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return DescribeServicesResponse.builder().build();
  }
  // todo: refactor it

  @Override
  public UntagResourceResponse untagService(
      AwsInternalConfig awsInternalConfig, UntagResourceRequest untagResourceRequest, String region) {
    try (EcsClient ecsClient = (EcsClient) getClient(awsInternalConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.untagResource(untagResourceRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return UntagResourceResponse.builder().build();
  }

  @Override
  public TagResourceResponse tagService(
      AwsInternalConfig awsInternalConfig, TagResourceRequest tagResourceRequest, String region) {
    try (EcsClient ecsClient = (EcsClient) getClient(awsInternalConfig, region)) {
      super.logCall(client(), Thread.currentThread().getStackTrace()[1].getMethodName());
      return ecsClient.tagResource(tagResourceRequest);
    } catch (Exception exception) {
      super.logError(client(), Thread.currentThread().getStackTrace()[1].getMethodName(), exception.getMessage());
      super.handleException(exception);
    }
    return TagResourceResponse.builder().build();
  }
}
