package io.harness.delegate.beans.ecs;

import io.harness.annotations.dev.OwnedBy;
import lombok.experimental.UtilityClass;
import software.amazon.awssdk.services.ecs.model.Container;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;

import java.util.stream.Collectors;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@UtilityClass
public class EcsMapper {
    public UpdateServiceRequest createServiceRequestToUpdateServiceRequest(CreateServiceRequest createServiceRequest) {
        return UpdateServiceRequest.builder()
                .service(createServiceRequest.serviceName())
                .cluster(createServiceRequest.cluster())
                .desiredCount(createServiceRequest.desiredCount())
                .taskDefinition(createServiceRequest.taskDefinition())
                .capacityProviderStrategy(createServiceRequest.capacityProviderStrategy())
                .deploymentConfiguration(createServiceRequest.deploymentConfiguration())
                .networkConfiguration(createServiceRequest.networkConfiguration())
                .placementConstraints(createServiceRequest.placementConstraints())
                .placementStrategy(createServiceRequest.placementStrategy())
                .platformVersion(createServiceRequest.platformVersion())
                .forceNewDeployment(false) // need to confirm with Sainath
                .healthCheckGracePeriodSeconds(createServiceRequest.healthCheckGracePeriodSeconds())
                .enableExecuteCommand(createServiceRequest.enableExecuteCommand())
                .enableECSManagedTags(createServiceRequest.enableECSManagedTags())
                .loadBalancers(createServiceRequest.loadBalancers())
                .propagateTags(createServiceRequest.propagateTags())
                .serviceRegistries(createServiceRequest.serviceRegistries())
                .build();

    }

    public EcsTask toEcsTask(Task task, String service) {
        return EcsTask.builder()
                .clusterArn(task.clusterArn())
                .serviceName(service)
                .launchType(task.launchTypeAsString())
                .taskArn(task.taskArn())
                .taskDefinitionArn(task.taskDefinitionArn())
                .startedAt(task.startedAt().getEpochSecond())
                .startedBy(task.startedBy())
                .version(task.version())
                .containers(task.containers()
                        .stream()
                        .map(EcsMapper::toEcsContainer)
                        .collect(Collectors.toList()))
                .build();
    }

    public EcsContainer toEcsContainer(Container container) {
        return EcsContainer.builder()
                .containerArn(container.containerArn())
                .image(container.image())
                .name(container.name())
                .runtimeId(container.runtimeId())
                .build();
    }
}
