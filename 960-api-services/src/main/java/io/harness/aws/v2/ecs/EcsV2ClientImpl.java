package io.harness.aws.v2.ecs;


import com.google.inject.Inject;
import io.harness.aws.beans.AwsInternalConfig;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
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
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;
import software.amazon.awssdk.services.ecs.waiters.EcsWaiter;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;


public class EcsV2ClientImpl implements EcsV2Client {

    @Inject private AwsApiV2HelperService awsApiV2HelperService;
    @Inject private AwsApiV2ExceptionHandler awsApiV2ExceptionHandler;

    @Override
    public CreateServiceResponse createService(AwsInternalConfig awsConfig, CreateServiceRequest createServiceRequest, String region) {
        try(EcsClient ecsClient = getEcsClient(awsConfig, region)) {
            return ecsClient.createService(createServiceRequest);
        }
        catch(AwsServiceException awsServiceException) {
            awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
        }
        catch(SdkException sdkException) {
            awsApiV2ExceptionHandler.handleSdkException(sdkException);
        }
        catch(Exception exception) {
            awsApiV2ExceptionHandler.handleException(exception);
        }
        return CreateServiceResponse.builder().build();
    }

    @Override
    public UpdateServiceResponse updateService(AwsInternalConfig awsConfig, UpdateServiceRequest updateServiceRequest, String region) {
        try(EcsClient ecsClient = getEcsClient(awsConfig, region)) {
            return ecsClient.updateService(updateServiceRequest);
        }
        catch(AwsServiceException awsServiceException) {
            awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
        }
        catch(SdkException sdkException) {
            awsApiV2ExceptionHandler.handleSdkException(sdkException);
        }
        catch(Exception exception) {
            awsApiV2ExceptionHandler.handleException(exception);
        }
        return UpdateServiceResponse.builder().build();
    }

    @Override
    public RegisterTaskDefinitionResponse createTask(AwsInternalConfig awsConfig, RegisterTaskDefinitionRequest registerTaskDefinitionRequest, String region) {
        try(EcsClient ecsClient = getEcsClient(awsConfig, region)) {
            return ecsClient.registerTaskDefinition(registerTaskDefinitionRequest);
        }
        catch(AwsServiceException awsServiceException) {
            awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
        }
        catch(SdkException sdkException) {
            awsApiV2ExceptionHandler.handleSdkException(sdkException);
        }
        catch(Exception exception) {
            awsApiV2ExceptionHandler.handleException(exception);
        }
        return RegisterTaskDefinitionResponse.builder().build();
    }

    @Override
    public  WaiterResponse<DescribeServicesResponse> ecsServiceSteadyStateCheck(AwsInternalConfig awsConfig,
                                           DescribeServicesRequest describeServicesRequest, String region,
                                           int serviceSteadyStateTimeout) {
        // Polling interval of 10 sec with total waiting done till a timeout of <serviceSteadyStateTimeout> min
        int delayInSeconds=10;
        int maxAttempts = (int) TimeUnit.MINUTES.toSeconds(serviceSteadyStateTimeout) / delayInSeconds;
        try(EcsClient ecsClient = getEcsClient(awsConfig, region);
            EcsWaiter ecsWaiter = getEcsWaiter(ecsClient, delayInSeconds, maxAttempts)){
            return ecsWaiter.waitUntilServicesStable(describeServicesRequest);
        }
        catch(AwsServiceException awsServiceException) {
            awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
        }
        catch(SdkException sdkException) {
            awsApiV2ExceptionHandler.handleSdkException(sdkException);
        }
        catch(Exception exception) {
            awsApiV2ExceptionHandler.handleException(exception);
        }
        return null;
    }

    @Override
    public RegisterScalableTargetResponse registerScalableTarget(AwsInternalConfig awsConfig,
                                                                 RegisterScalableTargetRequest registerScalableTargetRequest, String region) {
       try(ApplicationAutoScalingClient applicationAutoScalingClient =
                   getApplicationAutoScalingClient(awsConfig, region)) {
           return applicationAutoScalingClient.registerScalableTarget(registerScalableTargetRequest);
       }
       catch(AwsServiceException awsServiceException) {
           awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
       }
       catch(SdkException sdkException) {
           awsApiV2ExceptionHandler.handleSdkException(sdkException);
       }
       catch(Exception exception) {
           awsApiV2ExceptionHandler.handleException(exception);
       }
       return RegisterScalableTargetResponse.builder().build();
    }

    @Override
    public DeregisterScalableTargetResponse deregisterScalableTarget(AwsInternalConfig awsConfig,
                                                                     DeregisterScalableTargetRequest deregisterScalableTargetRequest, String region) {
        try(ApplicationAutoScalingClient applicationAutoScalingClient =
                    getApplicationAutoScalingClient(awsConfig, region)) {
            return applicationAutoScalingClient.deregisterScalableTarget(deregisterScalableTargetRequest);
        }
        catch(AwsServiceException awsServiceException) {
            awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
        }
        catch(SdkException sdkException) {
            awsApiV2ExceptionHandler.handleSdkException(sdkException);
        }
        catch(Exception exception) {
            awsApiV2ExceptionHandler.handleException(exception);
        }
        return DeregisterScalableTargetResponse.builder().build();
    }

    @Override
    public PutScalingPolicyResponse attachScalingPolicy(AwsInternalConfig awsConfig, PutScalingPolicyRequest putScalingPolicyRequest, String region) {
        try(ApplicationAutoScalingClient applicationAutoScalingClient =
                    getApplicationAutoScalingClient(awsConfig, region)) {
            return applicationAutoScalingClient.putScalingPolicy(putScalingPolicyRequest);
        }
        catch(AwsServiceException awsServiceException) {
            awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
        }
        catch(SdkException sdkException) {
            awsApiV2ExceptionHandler.handleSdkException(sdkException);
        }
        catch(Exception exception) {
            awsApiV2ExceptionHandler.handleException(exception);
        }
        return PutScalingPolicyResponse.builder().build();
    }

    @Override
    public DeleteScalingPolicyResponse deleteScalingPolicy(AwsInternalConfig awsConfig, DeleteScalingPolicyRequest deleteScalingPolicyRequest, String region) {
        try(ApplicationAutoScalingClient applicationAutoScalingClient =
                    getApplicationAutoScalingClient(awsConfig, region)) {
            return applicationAutoScalingClient.deleteScalingPolicy(deleteScalingPolicyRequest);
        }
        catch(AwsServiceException awsServiceException) {
            awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
        }
        catch(SdkException sdkException) {
            awsApiV2ExceptionHandler.handleSdkException(sdkException);
        }
        catch(Exception exception) {
            awsApiV2ExceptionHandler.handleException(exception);
        }
        return DeleteScalingPolicyResponse.builder().build();
    }

    @Override
    public DescribeScalableTargetsResponse listScalableTargets(AwsInternalConfig awsConfig,
                                                               DescribeScalableTargetsRequest describeScalableTargetsRequest, String region) {
        try(ApplicationAutoScalingClient applicationAutoScalingClient =
                    getApplicationAutoScalingClient(awsConfig, region)) {
            return applicationAutoScalingClient.describeScalableTargets(describeScalableTargetsRequest);
        }
        catch(AwsServiceException awsServiceException) {
            awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
        }
        catch(SdkException sdkException) {
            awsApiV2ExceptionHandler.handleSdkException(sdkException);
        }
        catch(Exception exception) {
            awsApiV2ExceptionHandler.handleException(exception);
        }
        return DescribeScalableTargetsResponse.builder().build();
    }

    @Override
    public DescribeScalingPoliciesResponse listScalingPolicies(AwsInternalConfig awsConfig,
                                                               DescribeScalingPoliciesRequest describeScalingPoliciesRequest, String region) {
        try(ApplicationAutoScalingClient applicationAutoScalingClient =
                    getApplicationAutoScalingClient(awsConfig, region)) {
            return applicationAutoScalingClient.describeScalingPolicies(describeScalingPoliciesRequest);
        }
        catch(AwsServiceException awsServiceException) {
            awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
        }
        catch(SdkException sdkException) {
            awsApiV2ExceptionHandler.handleSdkException(sdkException);
        }
        catch(Exception exception) {
            awsApiV2ExceptionHandler.handleException(exception);
        }
        return DescribeScalingPoliciesResponse.builder().build();
    }

    @Override
    public Optional<Service> getService(AwsInternalConfig awsConfig, String clusterName, String serviceName, String region) {
        DescribeServicesRequest describeServicesRequest = DescribeServicesRequest.builder()
                .services(Collections.singletonList(serviceName))
                .cluster(clusterName)
                .build();
        try(EcsClient ecsClient = getEcsClient(awsConfig, region)) {
            List<Service> services =  ecsClient.describeServices(describeServicesRequest).services();
            return (services.isEmpty())? Optional.empty() :  Optional.of(services.get(0));
        }
        catch(AwsServiceException awsServiceException) {
            awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
        }
        catch(SdkException sdkException) {
            awsApiV2ExceptionHandler.handleSdkException(sdkException);
        }
        catch(Exception exception) {
            awsApiV2ExceptionHandler.handleException(exception);
        }
        return Optional.empty();
    }

    @Override
    public TaskDefinition getTaskDefinitionForService(AwsInternalConfig awsConfig, Service service, String region) {
        DescribeTaskDefinitionRequest describeTaskDefinitionRequest = DescribeTaskDefinitionRequest.builder()
                .taskDefinition(service.taskDefinition())
                .build();
        try(EcsClient ecsClient = getEcsClient(awsConfig, region)) {
            return ecsClient.describeTaskDefinition(describeTaskDefinitionRequest).taskDefinition();
        }
        catch(AwsServiceException awsServiceException) {
            awsApiV2ExceptionHandler.handleAwsServiceException(awsServiceException);
        }
        catch(SdkException sdkException) {
            awsApiV2ExceptionHandler.handleSdkException(sdkException);
        }
        catch(Exception exception) {
            awsApiV2ExceptionHandler.handleException(exception);
        }
        return TaskDefinition.builder().build();
    }

    private EcsClient getEcsClient(AwsInternalConfig awsConfig, String region) {
        return EcsClient.builder()
                .credentialsProvider(awsApiV2HelperService.getAwsCredentialsProvider(awsConfig))
                .region(Region.of(region))
                .overrideConfiguration(awsApiV2HelperService.getClientOverrideConfiguration(awsConfig))
                .build();
    }

    private EcsWaiter getEcsWaiter(EcsClient ecsClient, int delayInSeconds, int maxAttempts) {
        return EcsWaiter.builder()
                .client(ecsClient)
                .overrideConfiguration(WaiterOverrideConfiguration.builder()
                        .backoffStrategy(FixedDelayBackoffStrategy.create(Duration.ofSeconds(delayInSeconds)))
                        .maxAttempts(maxAttempts)
                        .build())
                .build();
    }

    private ApplicationAutoScalingClient getApplicationAutoScalingClient(AwsInternalConfig awsConfig, String region) {
        return ApplicationAutoScalingClient.builder()
                .credentialsProvider(awsApiV2HelperService.getAwsCredentialsProvider(awsConfig))
                .region(Region.of(region))
                .overrideConfiguration(awsApiV2HelperService.getClientOverrideConfiguration(awsConfig))
                .build();
    }

}
