## Infrastructure definitions

The following changes were made to infrastructure definitions in NextGen:

The ECS infrastructure definitions in NextGen do not have AWS VPC, Security Group, or Network Policies. This configuration was moved to the Harness ECS service.

The ECS cluster can be a Harness Runtime Input. This makes the infrastructure definition reusable for other clusters in a given environment.

Infrastructure definition YAML has changed for ECS. Below is a YAML example, although both YAML and JSON are supported.

```yaml
infrastructureDefinition:
  name: ecs-dev-cluster
  identifier: ecsDevCluster
  description: "Sandbox Development Cluster"
  tags: {}
  orgIdentifier: default
  projectIdentifier: cdProductManagement
  environmentRef: devEcs
  deploymentType: ECS
  type: ECS
  spec:
    connectorRef: account.awsEcs
    region: us-east-1
    cluster: staging
  allowSimultaneousDeployments: false

```
