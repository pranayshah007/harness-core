## Deploy

Harness performs the deployment and checks to make sure the ECS service reaches steady state.

```
Creating Task Definition with family fargate-task-definition   
Created Task Definition fargate-task-definition:6 with Arn arn:aws:ecs:us-west-2:123456789:task-definition/fargate-task-definition:6..  
  
Deleting Scaling Policies from service myapp..  
Didn't find any Scaling Policies attached to service myapp   
  
Deregistering Scalable Targets from service myapp..  
Didn't find any Scalable Targets on service myapp   
Updating Service myapp with task definition arn:aws:ecs:us-west-2:123456789:task-definition/fargate-task-definition:6 and desired count 1   
Waiting for pending tasks to finish. 1/1 running ...  
Waiting for Service myapp to reach steady state   
...  
Service myapp reached steady state   
Updated Service myapp with Arn arn:aws:ecs:us-west-2:123456789:service/ecs-tutorial/myapp   
  
 Deployment Successful.
```

The key part to note is the AWS Events and the deployment Id (for example, 9219274889298964351). Harness starts tasks for the new version and scales the old tasks down to 0.

```