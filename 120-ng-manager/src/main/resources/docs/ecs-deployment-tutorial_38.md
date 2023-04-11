## ECS Blue Green deployments

ECS Blue/Green deployments use old and new versions of your service running behind the load balancer. Your ELB uses two listeners, Prod and Stage, each forwarding to a different target group where ECS services are run.

Blue/Green deployments are achieved by swapping listeners between the target groups, always pointing the Prod listener to the target group running the latest version.

In Harness, you identify which listeners are the Prod and Stage listeners. When Harness deploys, it uses the target group for the Stage listener (for example, **target1**) to test the deployment, verifies the deployment, and then swaps the Prod listener to use that target group. Next, the Stage listener now uses the old target group (**target2**).

When a new version of the service is deployed, the Stage listener and its target group (**target2**) are first used, then, after verification, the swap happens and the Prod listener forwards to **target2** and the Stage listener now forwards to **target1**.

To use ELB for Blue/Green deployment, you must have the following set up in AWS:

* **ELB** **Load Balancer** - An application load balancer must be set up in your AWS VPC. The VPC used by the ELB must have two subnets, each in a separate availability zone, which the ELB will use.
* **Two Listeners** - A listener checks for connection requests from clients, using the protocol and port that you configure, and forwards requests to one or more target groups, based on the rules that you define. Your load balancer must have two listeners set up: One listener for the production traffic (Prod) that points to one target group, and one listener for the stage traffic (Stage) that points to another target group.

You do not need to register instances for the target groups. Harness will perform that step during deployment.

For more information on ELB Application Load Balancers, see [What Is an Application Load Balancer?](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/introduction.html) from AWS.

Application Load Balancer (ALB) and Network Load Balancer (NLB) are supported.##### Ports Used in Blue/Green Using ELB

There are three places where ports are configured in this deployment:

* Harness ECS Service's **Service Specification:** you will specify ports in the [Port Mappings](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html#container_definition_portmappings) in the specification.  
The port number used here must also be used in the ELB Target Groups you use for Blue/Green.
* **Target Group** - You will create two target groups, and Harness will swap them to perform Blue/Green. When you create a target group, you will specify the same port number as the **Port Mappings** in the Container Specification in Service:

  ![](./static/ecs-deployment-tutorial-53.png)
  Both target groups must use the same port number, which is also the same number as the **Port Mappings** in the Container Specification in Service.
* **ELB Listener** - In your ELB, you create a listener for each target group. Listeners also use port numbers, but these are simply entry points for the ELB. For example, one listener uses port 80, and the other listener uses 8080.

If the port number used in the Service Specification does not match the port number used in the target groups, you will see this error:


```
Error: No container definition has port mapping that matches the target port: 80 for target group:   
arn:aws:elasticloadbalancing:us-west-1:4xxxxxxx5317:targetgroup/target1/ac96xxxxxx1d16
```

Simply correct the port numbers and rerun the deployment.
