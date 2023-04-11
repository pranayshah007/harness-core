## ECS Blue Green Specification Examples

Here are some examples of the ECS Task Definition and ECS Service Definition for an ECS deployment.

ECS Task Definition:

Replace the two `<ecsInstanceRole Role ARN>` with the ARN for the **ecsInstanceRole** used for your cluster. See [Amazon ECS Instance Role](https://docs.aws.amazon.com/batch/latest/userguide/instance_IAM_role.html) from AWS.

```yaml
ipcMode:  
executionRoleArn: <ecsInstanceRole Role ARN>  
containerDefinitions:  
- dnsSearchDomains:  
  environmentFiles:  
  entryPoint:  
  portMappings:  
  - hostPort: 80  
    protocol: tcp  
    containerPort: 80  
  command:  
  linuxParameters:  
  cpu: 0  
  environment: []  
  resourceRequirements:  
  ulimits:  
  dnsServers:  
  mountPoints: []  
  workingDirectory:  
  secrets:  
  dockerSecurityOptions:  
  memory:  
  memoryReservation: 128  
  volumesFrom: []  
  stopTimeout:  
  image: <+artifact.image>  
  startTimeout:  
  firelensConfiguration:  
  dependsOn:  
  disableNetworking:  
  interactive:  
  healthCheck:  
  essential: true  
  links:  
  hostname:  
  extraHosts:  
  pseudoTerminal:  
  user:  
  readonlyRootFilesystem:  
  dockerLabels:  
  systemControls:  
  privileged:  
  name: nginx  
placementConstraints: []  
memory: '512'  
taskRoleArn: <ecsInstanceRole Role ARN>  
family: sainath-fargate  
pidMode:  
requiresCompatibilities:  
- FARGATE  
networkMode: awsvpc  
runtimePlatform:  
cpu: '256'  
inferenceAccelerators:  
proxyConfiguration:  
volumes: []
```

The `image: <+artifact.image>` setting instructs Harness to pull the image you add to the Service **Artifacts** section and use it for deployment. You do not have to add an image in **Artifacts** and reference it using `<+artifact.image>`. You can hardcode the `image` instead or use a [Harness variable](../../../platform/12_Variables-and-Expressions/harness-variables.md) for the value that resolves to an image name at runtime.ECS Service Definition:

Replace `<Security Group Id>` and `<Subnet Id>` with the Ids from the ECS instances for your target ECS cluster.


```yaml
launchType: FARGATE  
serviceName: myapp  
desiredCount: 2  
networkConfiguration:  
  awsvpcConfiguration:  
    securityGroups:  
    - <Security Group Id>  
    subnets:  
    - <Subnet Id>  
    assignPublicIp: ENABLED   
deploymentConfiguration:  
  maximumPercent: 200  
  minimumHealthyPercent: 100  
loadBalancers:  
- targetGroupArn: <+targetGroupArn>  
  containerName: nginx  
  containerPort: 80    
```
