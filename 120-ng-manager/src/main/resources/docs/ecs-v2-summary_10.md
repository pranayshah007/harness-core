## Harness ECS service

The Harness ECS service now has more parameters in the Task Definition and service definition settings.

ECS Scaling Policies have moved to the ECS service from the ECS service Setup step and are now configurable as YAML or JSON files in the service.

Scalable Targets have moved from the ECS service Setup step and are now configurable as YAML or JSON param files in the Harness service.

The AWS VPC, Security Group, Subnets, and Execution Role ARN have moved out of the Harness infrastructure definition and are now part of the Harness service definition configuration.

The service definition requires more configuration:
  - `serviceName`
  - `loadbaBancer` properties
  - `networkConfiguration`
  - `desiredCount`

You can manipulate the deployment behavior via the new `deploymentConfiguration` properties `maximumPercent` and `minimumHealthyPercent`. See [DeploymentConfiguration](https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_DeploymentConfiguration.html) from AWS.
