## Support for circuit breaker configurations

:::note

Circuit breaker configuration can be applied to Harness ECS rolling and canary deployments only.

:::


Harness ECS rolling and canary deployments support AWS [ECS circuit breaker configurations](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-circuit-breaker.html).

AWS ECS circuit breaker logic determines whether the deployment will fail if the service can't reach steady state. During deployment, the failure state is identified based on a threshold. Circuit breaker creates the threshold configuration with the desired instance count configuration internally.

Circuit breaker configuration is implemented in the Harness ECS service **Service Definition**. 

<details>
<summary>Circuit breaker configuration example</summary>

See the `deployment-configuration` setting in the following example:

```json
"service": {
        "serviceArn": "arn:aws:ecs:us-east-1:1234567890:service/servicediscoverytest/ecs-service-discovery",
        "serviceName": "ecs-service-discovery",
        "clusterArn": "arn:aws:ecs:us-east-1:1234567890:cluster/servicediscoverytest",
        "loadBalancers": [],
        "serviceRegistries": [
            {
                "registryArn": "arn:aws:servicediscovery:us-east-1:1234567890:service/srv-xbnxncsqdovyuztm"
            }
        ],
        "status": "ACTIVE",
        "desiredCount": 1,
        "runningCount": 0,
        "pendingCount": 0,
        "launchType": "FARGATE",
        "platformVersion": "LATEST",
        "platformFamily": "Linux",
        "taskDefinition": "arn:aws:ecs:us-east-1:1234567890:task-definition/tutorial-task-def:1",
        "deploymentConfiguration": {
            "deploymentCircuitBreaker": {
                "enable": false,
                "rollback": false
            },
            "maximumPercent": 200,
            "minimumHealthyPercent": 100
        },
        "deployments": [
            {
                "id": "ecs-svc/0410909316449095426",
                "status": "PRIMARY",
                "taskDefinition": "arn:aws:ecs:us-east-1:1234567890:task-definition/tutorial-task-def:1",
                "desiredCount": 1,
                "deployment-configuration": "deploymentCircuitBreaker={enable=true,rollback=true}" ,
                "pendingCount": 0,
                "runningCount": 0,
...
```
</details>

Harness deploys the tasks in the above ECS service definition containing the circuit breaker configuration. Once deployed, the circuit breaker is activated. 

During failure scenarios, ECS circuit breaker performs a rollback automatically based on the threshold configuration.

