package io.harness.delegate.task.ecs;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.v2.ecs.EcsV2Client;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Optional;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class EcsCommandTaskNGHelper {
  @Inject
  EcsV2Client ecsV2Client;
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
}
