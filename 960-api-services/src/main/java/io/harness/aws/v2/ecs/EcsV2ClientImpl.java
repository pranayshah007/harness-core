package io.harness.aws.v2.ecs;


import com.google.inject.Singleton;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.v2.AwsClientHelper;
import io.harness.aws.beans.AwsInternalConfig;
import lombok.extern.slf4j.Slf4j;
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
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;
import software.amazon.awssdk.services.ecs.waiters.EcsWaiter;
import software.amazon.awssdk.services.ecs.EcsClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class EcsV2ClientImpl extends AwsClientHelper implements EcsV2Client {

    @Override
    public CreateServiceResponse createService(AwsInternalConfig awsConfig, CreateServiceRequest createServiceRequest, String region) {
        try(EcsClient ecsClient = (EcsClient)getClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            return ecsClient.createService(createServiceRequest);
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return CreateServiceResponse.builder().build();
    }

    @Override
    public UpdateServiceResponse updateService(AwsInternalConfig awsConfig, UpdateServiceRequest updateServiceRequest, String region) {
        try(EcsClient ecsClient = (EcsClient)getClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            return ecsClient.updateService(updateServiceRequest);
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return UpdateServiceResponse.builder().build();
    }

    @Override
    public RegisterTaskDefinitionResponse createTask(AwsInternalConfig awsConfig, RegisterTaskDefinitionRequest registerTaskDefinitionRequest, String region) {
        try(EcsClient ecsClient = (EcsClient)getClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            return ecsClient.registerTaskDefinition(registerTaskDefinitionRequest);
        }
        catch(Exception exception) {
            super.handleException(exception);
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
        try(EcsClient ecsClient = (EcsClient)getClient(awsConfig, region);
            EcsWaiter ecsWaiter = getEcsWaiter(ecsClient, delayInSeconds, maxAttempts)){
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            return ecsWaiter.waitUntilServicesStable(describeServicesRequest);
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return null;
    }

    @Override
    public RegisterScalableTargetResponse registerScalableTarget(AwsInternalConfig awsConfig,
                                                                 RegisterScalableTargetRequest registerScalableTargetRequest, String region) {
       try(ApplicationAutoScalingClient applicationAutoScalingClient =
                   getApplicationAutoScalingClient(awsConfig, region)) {
           super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
           return applicationAutoScalingClient.registerScalableTarget(registerScalableTargetRequest);
       }
       catch(Exception exception) {
           super.handleException(exception);
       }
       return RegisterScalableTargetResponse.builder().build();
    }

    @Override
    public DeregisterScalableTargetResponse deregisterScalableTarget(AwsInternalConfig awsConfig,
                                                                     DeregisterScalableTargetRequest deregisterScalableTargetRequest, String region) {
        try(ApplicationAutoScalingClient applicationAutoScalingClient =
                    getApplicationAutoScalingClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            return applicationAutoScalingClient.deregisterScalableTarget(deregisterScalableTargetRequest);
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return DeregisterScalableTargetResponse.builder().build();
    }

    @Override
    public PutScalingPolicyResponse attachScalingPolicy(AwsInternalConfig awsConfig, PutScalingPolicyRequest putScalingPolicyRequest, String region) {
        try(ApplicationAutoScalingClient applicationAutoScalingClient =
                    getApplicationAutoScalingClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            return applicationAutoScalingClient.putScalingPolicy(putScalingPolicyRequest);
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return PutScalingPolicyResponse.builder().build();
    }

    @Override
    public DeleteScalingPolicyResponse deleteScalingPolicy(AwsInternalConfig awsConfig, DeleteScalingPolicyRequest deleteScalingPolicyRequest, String region) {
        try(ApplicationAutoScalingClient applicationAutoScalingClient =
                    getApplicationAutoScalingClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            return applicationAutoScalingClient.deleteScalingPolicy(deleteScalingPolicyRequest);
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return DeleteScalingPolicyResponse.builder().build();
    }

    @Override
    public DescribeScalableTargetsResponse listScalableTargets(AwsInternalConfig awsConfig,
                                                               DescribeScalableTargetsRequest describeScalableTargetsRequest, String region) {
        try(ApplicationAutoScalingClient applicationAutoScalingClient =
                    getApplicationAutoScalingClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            return applicationAutoScalingClient.describeScalableTargets(describeScalableTargetsRequest);
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return DescribeScalableTargetsResponse.builder().build();
    }

    @Override
    public DescribeScalingPoliciesResponse listScalingPolicies(AwsInternalConfig awsConfig,
                                                               DescribeScalingPoliciesRequest describeScalingPoliciesRequest, String region) {
        try(ApplicationAutoScalingClient applicationAutoScalingClient =
                    getApplicationAutoScalingClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            return applicationAutoScalingClient.describeScalingPolicies(describeScalingPoliciesRequest);
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return DescribeScalingPoliciesResponse.builder().build();
    }

    @Override
    public Optional<Service> getService(AwsInternalConfig awsConfig, String clusterName, String serviceName, String region) {
        DescribeServicesRequest describeServicesRequest = DescribeServicesRequest.builder()
                .services(Collections.singletonList(serviceName))
                .cluster(clusterName)
                .build();
        try(EcsClient ecsClient = (EcsClient)getClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            List<Service> services =  ecsClient.describeServices(describeServicesRequest).services();
            return (services.isEmpty())? Optional.empty() :  Optional.of(services.get(0));
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return Optional.empty();
    }

    @Override
    public TaskDefinition getTaskDefinitionForService(AwsInternalConfig awsConfig, Service service, String region) {
        DescribeTaskDefinitionRequest describeTaskDefinitionRequest = DescribeTaskDefinitionRequest.builder()
                .taskDefinition(service.taskDefinition())
                .build();
        try(EcsClient ecsClient = (EcsClient)getClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            return ecsClient.describeTaskDefinition(describeTaskDefinitionRequest).taskDefinition();
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return TaskDefinition.builder().build();
    }

    @Override
    public ListTasksResponse listTaskArns(AwsInternalConfig awsConfig, String clusterName, String serviceName, String region) {
       ListTasksRequest listTasksRequest = ListTasksRequest.builder()
               .cluster(clusterName)
               .serviceName(serviceName).build();
       //todo: other filters
        ListTasksResponse response=null;
        try(EcsClient ecsClient = (EcsClient)getClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            response = ecsClient.listTasks(listTasksRequest);
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return response;
    }

    @Override
    public DescribeTasksResponse getTasks(AwsInternalConfig awsConfig, String clusterName, List<String> taskArns, String region) {
        DescribeTasksRequest describeTasksRequest = DescribeTasksRequest.builder()
                .cluster(clusterName)
                .tasks(taskArns)
                .build();
        DescribeTasksResponse response=null;
        try(EcsClient ecsClient = (EcsClient)getClient(awsConfig, region)) {
            super.logCall(client(), EcsV2ClientImpl.class.getEnclosingMethod().getName());
            response = ecsClient.describeTasks(describeTasksRequest);
        }
        catch(Exception exception) {
            super.handleException(exception);
        }
        return response;
    }

    private EcsWaiter getEcsWaiter(software.amazon.awssdk.services.ecs.EcsClient ecsClient, int delayInSeconds, int maxAttempts) {
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
                .credentialsProvider(getAwsCredentialsProvider(awsConfig))
                .region(Region.of(region))
                .overrideConfiguration(getClientOverrideConfiguration(awsConfig))
                .build();
    }

    @Override
    public SdkClient getClient(AwsInternalConfig awsConfig, String region) {
        return software.amazon.awssdk.services.ecs.EcsClient.builder()
                .credentialsProvider(getAwsCredentialsProvider(awsConfig))
                .region(Region.of(region))
                .overrideConfiguration(getClientOverrideConfiguration(awsConfig))
                .build();
    }

    @Override
    public String client() {
        return "ECS";
    }
}
