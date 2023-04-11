## ECS Run Task step

In addition to deploying tasks as part of your standard ECS deployment, you can use the **ECS Run Task** step to run individual tasks separately as a step in your ECS stage.

The ECS Run Task step is available in all ECS strategy types.

An example of when you run a task separately is a one-time or periodic batch job that does not need to keep running or restart when it finishes.

For more information, see [Running tasks](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs_run_task.html) from AWS.

1. In your Harness ECS stage, in **Execution**, click **Add Step**, and then click **ECS Run Task**.   Using the **ECS Run Task** step is the same as running a task in the AWS console.
2. In **ECS Run Task Definition**, add a Task Definition to run. Here's an example:
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
  family: fargate-task-definition  
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
3. In **ECS Run Task Request Definition**, you can customize how Amazon ECS places tasks using placement constraints and placement strategies just like using a **Capacity provider strategy** in the ECS console.

![](./static/ecs-deployment-tutorial-64.png)

You can add the Task Definition and Task Request Definition using remote repos or the Harness File Store.

Here's an example (replace `<Security Group Id>` and `<Subnet Id>` with the Ids from the ECS instances for your target ECS cluster):

```yaml
launchType: FARGATE  
networkConfiguration:  
  awsvpcConfiguration:  
    securityGroups:  
    - <Security Group Id>  
    subnets:  
    - <Subnet Id>  
    assignPublicIp: ENABLED  
count: 1
```
For information on running ECS Tasks, go to the AWS docs [Run a standalone task in the classic Amazon ECS console](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs_run_task.html), [RunTask](https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_RunTask.html), and [Amazon ECS capacity providers](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/cluster-capacity-providers.html).
