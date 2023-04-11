## ASG configuration files

AWS does not have a dedicated public resource for ASG configuration file formatting because ASG creation is typically done using the AWS CLI, SDKs, or Management Console, which have their own syntax and methods for specifying the parameters.

However, the AWS CLI [create-auto-scaling-group](https://docs.aws.amazon.com/cli/latest/reference/autoscaling/create-auto-scaling-group.html) command reference documentation provides a detailed description of the parameters that can be used when creating an ASG. 

<details>
<summary>ASG configuration file example</summary>

```json
{
  "AutoScalingGroupName": "my-asg",
  "MinSize": 1,
  "MaxSize": 3,
  "DesiredCapacity": 2,
  "AvailabilityZones": ["us-west-2a", "us-west-2b"],
  "LaunchTemplate": {
    "LaunchTemplateId": "lt-0123456789abcdef0",
    "Version": "1"
  },
  "VPCZoneIdentifier": "subnet-0123456789abcdef0,subnet-0123456789abcdef1",
  "HealthCheckType": "EC2",
  "HealthCheckGracePeriod": 300,
  "Tags": [
    {
      "Key": "Environment",
      "Value": "Development",
      "PropagateAtLaunch": true
    }
  ]
}
```
</details>

