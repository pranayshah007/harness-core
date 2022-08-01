package io.harness.delegate.task.ecs;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.v2.ecs.EcsV2Client;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static java.lang.String.format;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class EcsCommandTaskNGHelper {
  @Inject private EcsV2Client ecsV2Client;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;

  public RegisterTaskDefinitionResponse createTaskDefinition(RegisterTaskDefinitionRequest registerTaskDefinitionRequest, String region, AwsConnectorDTO awsConnectorDTO) {
    return ecsV2Client.createTask(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), registerTaskDefinitionRequest, region);
  }

  public CreateServiceResponse createService(CreateServiceRequest createServiceRequest, String region, AwsConnectorDTO awsConnectorDTO) {
    return ecsV2Client.createService(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), createServiceRequest, region);
  }

  public UpdateServiceResponse updateService(UpdateServiceRequest updateServiceRequest, String region, AwsConnectorDTO awsConnectorDTO) {
    return ecsV2Client.updateService(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), updateServiceRequest, region);
  }

  public boolean serviceExists(String cluster, String serviceName, String region, AwsConnectorDTO awsConnectorDTO) {
    Optional<Service> serviceOptional = ecsV2Client.getService(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), cluster, serviceName, region);
    return serviceOptional.isPresent();
  }

  public UpdateServiceRequest convertCreateServiceRequestToUpdateServiceRequest(CreateServiceRequest createServiceRequest) {
    UpdateServiceRequest updateServiceRequest = UpdateServiceRequest.builder()
            .service(createServiceRequest.serviceName())
            .serviceRegistries(createServiceRequest.serviceRegistries())
            .capacityProviderStrategy(createServiceRequest.capacityProviderStrategy())
            .cluster(createServiceRequest.cluster())
            .deploymentConfiguration(createServiceRequest.deploymentConfiguration())
            .desiredCount(createServiceRequest.desiredCount())
            .enableECSManagedTags(createServiceRequest.enableECSManagedTags())
            .healthCheckGracePeriodSeconds(createServiceRequest.healthCheckGracePeriodSeconds())
            .loadBalancers(createServiceRequest.loadBalancers())
            .enableExecuteCommand(createServiceRequest.enableExecuteCommand())
            .networkConfiguration(createServiceRequest.networkConfiguration())
            .overrideConfiguration(createServiceRequest.overrideConfiguration().isPresent() ? createServiceRequest.overrideConfiguration().get() : null)
            .placementConstraints(createServiceRequest.placementConstraints())
            .placementStrategy(createServiceRequest.placementStrategy())
            .platformVersion(createServiceRequest.platformVersion())
            .propagateTags(createServiceRequest.propagateTags())
            .taskDefinition(createServiceRequest.taskDefinition())
            .build();
    return updateServiceRequest;
  }

  public WaiterResponse<DescribeServicesResponse> ecsServiceSteadyStateCheck(LogCallback deployLogCallback, AwsConnectorDTO awsConnectorDTO,
                                                                             String cluster, String serviceName, String region, int serviceSteadyStateTimeout) {
    deployLogCallback.saveExecutionLog(format("Waiting for Service %s to reach steady state %n", serviceName), LogLevel.INFO);

    DescribeServicesRequest describeServicesRequest = DescribeServicesRequest.builder()
            .services(Collections.singletonList(serviceName))
            .cluster(cluster)
            .build();
    deployLogCallback.saveExecutionLog(format("Service %s reached steady state %n", serviceName), LogLevel.INFO);

    WaiterResponse<DescribeServicesResponse> describeServicesResponseWaiterResponse = ecsV2Client.ecsServiceSteadyStateCheck(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), describeServicesRequest, region, serviceSteadyStateTimeout);

    return  describeServicesResponseWaiterResponse;
  }

  public List<EcsTask> getEcsTasks(AwsConnectorDTO awsConnectorDTO,
                                   String cluster, String serviceName, String region) {
    String nextToken = null;
    List<EcsTask> response = new ArrayList<>();
    do {
      ListTasksResponse listTasksResponse = ecsV2Client.listTaskArns(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO),
              cluster,serviceName, region, null);
      nextToken = listTasksResponse.nextToken();
      if(listTasksResponse.hasTaskArns()) {
        DescribeTasksResponse describeTasksResponse = ecsV2Client.getTasks(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO),
                cluster, listTasksResponse.taskArns(), region);
        response.addAll( describeTasksResponse.tasks()
                .stream()
                .map(task -> EcsMapper.toEcsTask(task, serviceName))
                .collect(Collectors.toList()));
      }
    }
    while (nextToken != null);
    return response;
  }

}
