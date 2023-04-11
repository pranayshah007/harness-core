# Set up your ECS cluster

Create the target cluster for this tutorial.

The cluster must meet the following specs:

* EC2 Linux + Networking cluster.
* The ECS Cluster must have a minimum of 8GB memory for the Delegate. A m5.xlarge minimum is suggested.
* 2 registered container instances.
* The standard **ecsInstance** role for the **Container instance IAM role** (described above).

![](./static/ecs-deployment-tutorial-37.png)

You will select this cluster later when your define the target Infrastructure Definition for the CD stage in your Harness Pipeline.
