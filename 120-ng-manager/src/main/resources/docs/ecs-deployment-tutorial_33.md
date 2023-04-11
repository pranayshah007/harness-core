### Scaling Policy

The Scaling Policy setting in the Harness Service defines a scaling policy that Application Auto Scaling uses to adjust your application resources. For more information, see [ScalingPolicy](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-applicationautoscaling-scalingpolicy.html) from AWS.

Example:

```yaml
scalableDimension: ecs:service:DesiredCount  
serviceNamespace: ecs  
policyName: P1  
policyType: TargetTrackingScaling  
targetTrackingScalingPolicyConfiguration:  
  targetValue: 60  
  predefinedMetricSpecification:  
    predefinedMetricType: ECSServiceAverageCPUUtilization  
  scaleOutCooldown: 300  
  scaleInCooldown: 300
```
