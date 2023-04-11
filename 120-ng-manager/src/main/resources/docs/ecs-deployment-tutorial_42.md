### ECS Blue Green Create Service step

Configure the following settings:

* **Elastic Load Balancer:** Click here and select the AWS load balancer you added. Harness uses the Delegate to locate the load balancers and list them in **Elastic Load Balancer**. If you do not see your load balancer, ensure that the Delegate can connect to the load balancers. Once the load balancer is selected, Harness will populate the Prod and Stage Listener drop-downs.
* **Prod Listener:** Select the ELB listener that you want to use as the Prod Listener.
* **Stage Listener:** Select the ELB listener that you want to use as the Stage Listener.
* **Prod Listener Rule ARN** and **Stage Listener Rule ARN**: If you are using [Listener Rules](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html#listener-rules) in your target groups, you can select them in **Production Listener Rule ARN** and **Stage Listener Rule ARN**.
	+ You must select a listener rule.
	+ Ensure the traffic that will use the rules matches the conditions you have set in the rules. For example, if you have a path condition on a rule to enable [path-based routing](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/tutorial-load-balancer-routing.html), ensure that traffic uses that path.

Here's an example of the output of the step:


```
Creating Task Definition with family johndoe-fargate   
Created Task Definition johndoe-fargate:498 with Arn arn:aws:ecs:us-east-1:1234567890:task-definition/johndoe-fargate:498..  
  
Creating Stage Service abc__1 with task definition arn:aws:ecs:us-east-1:1234567890:task-definition/johndoe-fargate:498 and desired count 1   
Waiting for pending tasks to finish. 0/1 running ...  
Waiting for pending tasks to finish. 0/1 running ...  
Waiting for pending tasks to finish. 0/1 running ...  