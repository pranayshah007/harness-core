### Scalable Target

The Scalable Target setting in the Harness Service specifies a resource that AWS Application Auto Scaling can scale. For more information, see [ScalableTarget](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-applicationautoscaling-scalabletarget.html) from AWS.

Example:

```yaml
serviceNamespace: ecs  
scalableDimension: ecs:service:DesiredCount  
minCapacity: 1  
maxCapacity: 3
```
