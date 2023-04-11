## ECS Canary deployments

In an ECS Canary deployment, Harness deploys to 1 instance, deletes it, and then performs a Rolling deployment of the new version with full desired count.

![](./static/ecs-deployment-tutorial-52.png)

The deployment follows this process:

1. ECS Canary Deploy step.
	1. Harness deploys the new version of the service to 1 instance.
	2. Harness names the service with a Canary suffix when it deploys it as part of this step. For example, if the service name is myapp, it will be called myappCanary.

	```
	Creating Task Definition with family fargate-task-definition   
	Created Task Definition fargate-task-definition:9 with Arn arn:aws:ecs:us-west-2:1234567890:task-definition/fargate-task-definition:9..  
	Creating Service myappCanary with task definition arn:aws:ecs:us-west-2:1234567890:task-definition/fargate-task-definition:9 and desired count 1   
	Waiting for pending tasks to finish. 0/1 running ...  
	Waiting for pending tasks to finish. 0/1 running ...  
	# AWS Event: 2022-10-07 00:05:14.665 (service myappCanary) has started 1 tasks: (task ab694b189d204d15950b0466c0e5bd10).  
	Waiting for pending tasks to finish. 0/1 running ...  
	Waiting for pending tasks to finish. 1/1 running ...  
	Waiting for Service myappCanary to reach steady state   
	Service myappCanary reached steady state   
	Created Service myappCanary with Arn arn:aws:ecs:us-west-2:1234567890:service/ecs-canary/myappCanary   
	  
	 Deployment Successful.
	```

2. You can add any verification or testing steps after the ECS Canary Deploy step.
3. ECS Canary Delete step.
	1. Harness deletes the service deployed with the preceding ECS Canary Deploy step.
	```
	Deleting service myappCanary..  
	Waiting for existing Service myappCanary to reach inactive state   
	Existing Service myappCanary reached inactive state   
	Canary service myappCanary deleted
	```
4. ECS Rolling Deploy.
	1. Harness performs a standard rolling deployment of the service to the desired count in the ECS Service Definition you added.
