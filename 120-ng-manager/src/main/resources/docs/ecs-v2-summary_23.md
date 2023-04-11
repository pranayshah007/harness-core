# We can define the deployment behavior properties in the service definition and Harness will deploy with the defined configuration
deploymentConfiguration:
  maximumPercent: 200
  minimumHealthyPercent: 100
loadBalancers:
- targetGroupArn: <+targetGroupArn>
  containerName: nginx
  containerPort: 80    
```
