### Sample Task Definition and supported parameters

Here is a YAML example highlighting the changes. Harness supports standard AWS ECS YAML and JSON.

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