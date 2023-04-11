## Wrap Up

The Wrap Up section shows the Rolling Update strategy used.


```
...  
Name:                   harness-example-deployment  
Namespace:              default  
CreationTimestamp:      Sun, 17 Feb 2019 22:03:53 +0000  
Labels:                 <none>  
Annotations:            deployment.kubernetes.io/revision: 1  
                        kubectl.kubernetes.io/last-applied-configuration:  
                          {"apiVersion":"apps/v1","kind":"Deployment","metadata":{"annotations":{"kubernetes.io/change-cause":"kubectl apply --kubeconfig=config --f...  
                        kubernetes.io/change-cause: kubectl apply --kubeconfig=config --filename=manifests.yaml --record=true  
Selector:               app=harness-example  
Replicas:               2 desired | 2 updated | 2 total | 2 available | 0 unavailable  
StrategyType:           RollingUpdate  
MinReadySeconds:        0  
RollingUpdateStrategy:  25% max unavailable, 25% max surge  
...  
NewReplicaSet:   harness-example-deployment-5674658766 (2/2 replicas created)  
Events:  
  Type    Reason             Age   From                   Message  
  ----    ------             ----  ----                   -------  
  Normal  ScalingReplicaSet  8s    deployment-controller  Scaled up replica set harness-example-deployment-5674658766 to 2  
  
Done.
```