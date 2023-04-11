### Sample service definition and supported parameters

Here is a JSON example highlighting the changes. Harness supports standard AWS ECS YAML and JSON.

```json
{
    "launchType": "FARGATE",
    "serviceName": "myapp",
    "desiredCount": 1,
    "networkConfiguration": {
        "awsvpcConfiguration": {
            "securityGroups": [
                "<Security Group Id>"
            ],
            "subnets": [
                "<Subnet Id>"
            ],
            "assignPublicIp": "ENABLED"
        }
    },
    "deploymentConfiguration": {
        "maximumPercent": 100,
        "minimumHealthyPercent": 0
    }
}
```
