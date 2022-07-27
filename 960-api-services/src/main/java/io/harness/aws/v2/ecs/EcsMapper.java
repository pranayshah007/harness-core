package io.harness.aws.v2.ecs;

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

}
