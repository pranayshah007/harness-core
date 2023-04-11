### ECS Blue Green Swap Target step

The **ECS Blue Green Swap Target** step performs the Blue/Green route swap once the deployment is verified.

Typically, you will want to add one or more steps between the **ECS Blue Green Create Service** step and **ECS Blue Green Swap Target** step for verification.

When you deploy, Harness will use the target group for the **Stage Listener** from the **ECS Blue Green Create Service** step for deployment. After verifying the success of the deployment, the **ECS Blue Green Swap Target** step simply swaps the target groups between the listeners. Now, the target group with the latest version receives production traffic. The target group with the old version receives the stage traffic.

Here's an example of the output of the step:

```
Modifying ELB Prod Listener to Forward requests to Target group associated with new Service  
,TargetGroup: arn:aws:elasticloadbalancing:us-east-1:1234567890:targetgroup/example-tg-ip-2/34b77f72b13e45f4  
Modifying the default Listener: arn:aws:elasticloadbalancing:us-east-1:1234567890:listener/app/example-alb/8c164c70eb817f6a/83cbd24bc4f6d349   
 with listener rule: arn:aws:elasticloadbalancing:us-east-1:1234567890:listener-rule/app/example-alb/8c164c70eb817f6a/83cbd24bc4f6d349/a249570503765d2d   
 to forward traffic to TargetGroup: arn:aws:elasticloadbalancing:us-east-1:1234567890:targetgroup/example-tg-ip-2/34b77f72b13e45f4  
Successfully updated Prod Listener   
  
Modifying ELB Stage Listener to Forward requests to Target group associated with old Service  
,TargetGroup: arn:aws:elasticloadbalancing:us-east-1:1234567890:targetgroup/example-tg-ip-1/52b1f157d3240800  
Modifying the default Listener: arn:aws:elasticloadbalancing:us-east-1:1234567890:listener/app/example-alb/8c164c70eb817f6a/cfbb98e593af641b   
 with listener rule: arn:aws:elasticloadbalancing:us-east-1:1234567890:listener-rule/app/example-alb/8c164c70eb817f6a/cfbb98e593af641b/30983f6b6338ce10   
 to forward traffic to TargetGroup: arn:aws:elasticloadbalancing:us-east-1:1234567890:targetgroup/example-tg-ip-1/52b1f157d3240800  
Successfully updated Stage Listener   
  
Updating tag of new service: abc__1  
Updating service: abc__1 with tag: [BG_VERSION, BLUE]  
Successfully updated tag   
  
Swapping Successful. 
```

You can see the ELB **Prod Listener** is now forwarding requests to the Target Group used with the new service version deployed in the previous step (ECS Blue Green Create Service).

Also, the ELB **Stage** Listener is forwarding to the Target Group for the previous service version.

Lastly, the new ECS service is tagged with `BG_VERSION`, `BLUE`.

**Do not downsize old service:** Use this setting to choose whether to downsize the older, previous version of the service.

By default, the previous service is downsized to 0. The service is downsized, but not deleted. If the older service needs to be brought back up again, it is still available.
