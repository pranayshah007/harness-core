### Collecting Auto Scaling Resources

Before you set up Auto Scaling for the ECS service in Harness, you need to obtain the JSON for the Scalable Target and Scalable Policy resources from AWS.

The JSON format used in the **Auto Scaler Configurations** settings should match the AWS standards as described in [ScalableTarget](https://docs.aws.amazon.com/autoscaling/application/APIReference/API_ScalableTarget.html) and [ScalablePolicy](https://docs.aws.amazon.com/autoscaling/application/APIReference/API_ScalingPolicy.html).

To obtain the Scalable Target, connect to an EC2 instance in your VPC and enter the following:

`aws application-autoscaling describe-scalable-targets --service-namespace ecs`

For more information, see [describe-scalable-targets](https://docs.aws.amazon.com/cli/latest/reference/application-autoscaling/describe-scalable-targets.html) from AWS.

To obtain the Scalable Policy, enter the following:

`aws application-autoscaling describe-scaling-policies --service-namespace ecs`

For more information, see [describe-scaling-policies](https://docs.aws.amazon.com/cli/latest/reference/application-autoscaling/describe-scaling-policies.html) from AWS.

To create the Scalable Target and Scalable Policy resources, see the [register-scalable-target](https://docs.aws.amazon.com/cli/latest/reference/application-autoscaling/register-scalable-target.html) and [put-scaling-policy](https://docs.aws.amazon.com/cli/latest/reference/application-autoscaling/put-scaling-policy.html) commands from AWS.
