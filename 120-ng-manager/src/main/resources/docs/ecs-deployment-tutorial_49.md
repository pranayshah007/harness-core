## Support for ECS Deployments with AWS Service Mesh Configuration

Harness ECS Deployments supports deploying of ECS Services with AWS Service Discovery Configuration

AWS Service Discovery is a cloud service provided by Amazon Web Services (AWS) that makes it easy for microservices applications to discover and communicate with each other. It enables you to manage and discover the addresses of the services within your microservices application without the need for hard-coded IP addresses or hostnames.

It is possible to provide AWS Service Discovery as part of ECS Service Definiton and deploy using Harness.

Following are the steps required to configure a Service Discovery and deploy

1. Create a Namespace in AWS Cloud Map.
2. Create Service Discovery with above namespace,generate ARN.
3. Provide the Service Discovery ARN in Service Definition.

```
launchType: FARGATE
serviceName: ecs-svc-discovery
desiredCount: 1
networkConfiguration:
  awsvpcConfiguration:
    securityGroups:
    - sg-afc848e7 
    subnets:
    - subnet-9757dc98
    assignPublicIp: ENABLED 
deploymentConfiguration:
  maximumPercent: 100
  minimumHealthyPercent: 0
**serviceRegistries:**
  ** - registryArn: arn:aws:servicediscovery:us-east-1:1234567890:service/srv-xeycgshb42ydmokf**
```

With the above Service Registry ARN specified in ECS Service Definition ,deployed services are marked with Service Discovery capability
