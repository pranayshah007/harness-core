# Add the Service Definition

:::note

Ensure that the Service Definition `services` array (`{"services": []}`) is removed from the Service Definition you use in Harness. When you copy a Service Definition from ECS the `services` array is typically included.

:::

Harness has full support for ECS Service definitions. You simply provide Harness with a service definition and it will implement it.

If you are new to ECS, please review the AWS documentation on [ECS Service Definitions](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service_definition_parameters.html).

1. In **Service Definition**, click **Add Service Definition**.
2. In **Specify ECS Service Definition Store**, click **Harness**, and click **Continue**.
3. In **Manifest Name**, enter **Service Definition**.
4. In **File/Folder Path**, click **Select**.
5. In the same **ECS Tutorial** folder you used earlier, add a file named **CreateServiceRequest.yaml** and paste in the following YAML, then click **Save**, and then click **Apply Selected**:

Replace `<Security Group Id>` and `<Subnet Id>` with the Ids from the ECS instances for your target ECS cluster.

JSON Example:

```json
{  
    "launchType": "FARGATE",  
    "serviceName": myapp,  
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

YAML Example:

```yaml
launchType: FARGATE  
serviceName: myapp  
desiredCount: 1  
networkConfiguration:  
  awsvpcConfiguration:  
    securityGroups:  
    - <Security Group Id>  
    subnets:  
    - <Subnet Id>  
    assignPublicIp: ENABLED   
deploymentConfiguration:  
  maximumPercent: 200  
  minimumHealthyPercent: 100
```

The ECS Service Definition is now added to the Service.

![](./static/ecs-deployment-tutorial-42.png)

Next, we'll add the Docker image artifact for deployment.
