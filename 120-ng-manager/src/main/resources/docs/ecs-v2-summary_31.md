## Rolling deployments

To achieve phased rollout of ECS deployments, we recommend using the `deploymentConfiguration` field in the ECS service definition.

For example:

```yaml
deploymentConfiguration:
  maximumPercent: 100
  minimumHealthyPercent: 80
```

To understand how this configuration works, go to [Service definition parameters](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service_definition_parameters.html) from AWS.

