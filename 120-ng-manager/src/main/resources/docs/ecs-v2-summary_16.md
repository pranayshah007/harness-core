# The taskRoleARN property needs to be provided as this is the ECS Instance Role ARN
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
