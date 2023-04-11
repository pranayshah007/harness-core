## Force new deployment

This is the same as [forceNewDeployment](https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_UpdateService.html#ECS-UpdateService-request-forceNewDeployment) from AWS ECS:


> Determines whether to force a new deployment of the service. By default, deployments aren't forced. You can use this option to start a new deployment with no service definition changes. For example, you can update a service's tasks to use a newer Docker image with the same image/tag combination (`my_image:latest`) or to roll Fargate tasks onto a newer platform version.
