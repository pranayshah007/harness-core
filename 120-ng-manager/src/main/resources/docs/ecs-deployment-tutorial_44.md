 AWS Event: 2022-10-11 02:51:27.667 (service abc__1) registered 1 targets in (target-group arn:aws:elasticloadbalancing:us-east-1:1234567890:targetgroup/example-tg-ip-2/34b77f72b13e45f4)  
Waiting for Service abc__1 to reach steady state   
Service abc__1 reached steady state   
Created Stage Service abc__1 with Arn arn:aws:ecs:us-east-1:1234567890:service/example-test/abc__1   
  
Target Group with Arn: arn:aws:elasticloadbalancing:us-east-1:1234567890:targetgroup/example-tg-ip-2/34b77f72b13e45f4 is associated with Stage Service abc__1  
Tag: [BG_VERSION, GREEN] is associated with Stage Service abc__1
```
You can see the target is registered in the Target Groups associated with the Stage Listener selected, the new ECS service is deployed as the Stage Service, associated with the Target Group, and the Service is tagged with `BG_VERSION`, `GREEN`.
