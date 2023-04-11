## AWS Auto Scaling with ECS

The ECS service(s) you deploy with Harness can be configured to use AWS Service Auto Scaling to adjust its desired ECS service count up or down in response to CloudWatch alarms. For more information on using Auto Scaling with ECS, see [Target Tracking Scaling Policies](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/service-autoscaling-targettracking.html) from AWS.

To configure Auto Scaling in Harness, you use **Scalable Target** and **Scaling Policy** settings in the Harness Service.
