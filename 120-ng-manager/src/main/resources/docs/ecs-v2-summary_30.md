## ECS environment configuration

If Harness service **Configuration Parameters** need to be overridden based on Infrastructure, Harness recommends using Harness service variables and overriding them at the environment level.

For example, if the AWS Security Group in the ECS service definition needs to be overridden for a Harness environment, we recommend creating a service variable `securityGroup` in the Harness service and using it in the ECS service definition Manifest as `<+serviceVariables.securityGroup>`.

The variable `securityGroup` value can be overridden at the environment level. For more information, go to [Services and environments overview](../../onboard-cd/cd-concepts/services-and-environments-overview.md). 
