### Sample Task Definition and supported parameters

Here is a JSON example highlighting the changes. Harness supports standard AWS ECS YAML and JSON.

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
