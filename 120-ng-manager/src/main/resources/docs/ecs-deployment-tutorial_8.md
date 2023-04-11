# Add the Task Definition

Harness has full support for ECS task definitions. You simply provide Harness with a task definition and it will implement it.

There are two ways to add the ECS task definition to the Harness service:

- **Task Definition**: Add a connection to the task definition file in a remote Git repository, local [Harness File Store](https://developer.harness.io/docs/continuous-delivery/cd-services/cd-services-general/add-inline-manifests-using-file-store/), or object storage (AWS S3).
- **Task Definition ARN**: Add the task definition ARN. 
  - The task definition ARN points to an existing task created and available in the AWS cluster with the required definition.
  - The task definition will be fetched using the task ARN provided and added to the ECS service configuration provided in the Harness ECS service **Service Definition**.
  - During deployment, the required task is deployed with the desired count provided in the **Service Definition**. 

If you are new to ECS, review the AWS documentation on [ECS Task Definitions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definitions.html).

Let's look at an example using a task definition file in the [Harness File Store](https://developer.harness.io/docs/continuous-delivery/cd-services/cd-services-general/add-inline-manifests-using-file-store/).

1. In **Task Definition**, click **Add Task Definition**.  
  You specify what Task Definition to use in the **ECS Task Definition Store**.

  ![](./static/ecs-deployment-tutorial-40.png)

  You can use a remote repo, but for this tutorial we'll use the built-in Harness file manager, [Harness File Store](../../cd-services/cd-services-general/add-inline-manifests-using-file-store.md).
2. Select **Harness**, and then select **Continue**.
3. In **Task Definition**, select **Add Task Definition**.
4. In **Specify ECS Task Definition Store**, select **Harness**, and select **Continue**.
5. In **Manifest Details**, enter a name for the task definition.
6. In **File/Folder Path**, select **Select**. The Harness File Store appears.
7. Create a new folder named **ECS Tutorial**.
8. In the new folder, create a new file named **RegisterTaskDefinitionRequest.yaml**.
9. Paste the following Task Definition into the file, select **Save**, and then select **Apply Selected**.
   1. Replace the two `<ecsInstanceRole Role ARN>` with the ARN for the **ecsInstanceRole** used for your cluster. See [Amazon ECS Instance Role](https://docs.aws.amazon.com/batch/latest/userguide/instance_IAM_role.html) from AWS. 
   2. When you are done, in **Manifest Details**, select **Submit**. 

JSON Example:
```json
{  
   "ipcMode": null,  
   "executionRoleArn": "<ecsInstanceRole Role ARN>",  
   "containerDefinitions": [  
       {  
           "dnsSearchDomains": null,  
           "environmentFiles": null,  
           "entryPoint": null,  
           "portMappings": [  
               {  
                   "hostPort": 80,  
                   "protocol": "tcp",  
                   "containerPort": 80  
               }  
           ],  
           "command": null,  
           "linuxParameters": null,  
           "cpu": 0,  
           "environment": [],  
           "resourceRequirements": null,  
           "ulimits": null,  
           "dnsServers": null,  
           "mountPoints": [],  
           "workingDirectory": null,  
           "secrets": null,  
           "dockerSecurityOptions": null,  
           "memory": null,  
           "memoryReservation": 128,  
           "volumesFrom": [],  
           "stopTimeout": null,  
           "image": "<+artifact.image>",  
           "startTimeout": null,  
           "firelensConfiguration": null,  
           "dependsOn": null,  
           "disableNetworking": null,  
           "interactive": null,  
           "healthCheck": null,  
           "essential": true,  
           "links": null,  
           "hostname": null,  
           "extraHosts": null,  
           "pseudoTerminal": null,  
           "user": null,  
           "readonlyRootFilesystem": null,  
           "dockerLabels": null,  
           "systemControls": null,  
           "privileged": null,  
           "name": "nginx"  
       }  
   ],  
   "placementConstraints": [],  
   "memory": "512",  
   "taskRoleArn": "<ecsInstanceRole Role ARN>",  
   "family": "fargate-task-definition",  
   "pidMode": null,  
   "requiresCompatibilities": [  
       "FARGATE"  
   ],  
   "networkMode": "awsvpc",  
   "runtimePlatform": null,  
   "cpu": "256",  
   "inferenceAccelerators": null,  
   "proxyConfiguration": null,  
   "volumes": []  
}
```
YAML Example:
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

The `image: <+artifact.image>` setting instructs Harness to pull the image you add to the Service **Artifacts** section and use it for deployment. You do not have to add an image in **Artifacts** and reference it using `<+artifact.image>`. You can hardcode the `image` instead or use a [Harness variable](../../../platform/12_Variables-and-Expressions/harness-variables.md) for the value that resolves to an image name at runtime. For this tutorial, we will use `image: <+artifact.image>` and an artifact.

The Task Definition is added to the Service.

![](./static/ecs-deployment-tutorial-41.png)
