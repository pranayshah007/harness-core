# Example: rolling update deployment

Now that the setup is complete, you can click **Run Pipeline**.

The Pipeline is deployed.

To see the completed deployment, log into your cluster and run `kubectl get all`. The output lists the new Deployment:


```
NAME                                                   READY     STATUS    RESTARTS   AGE  
pod/harness-example-deployment-5674658766-6b2fw        1/1       Running   0          34m  
pod/harness-example-deployment-5674658766-p9lpz        1/1       Running   0          34m  
                                                         
NAME                                                   TYPE           CLUSTER-IP      EXTERNAL-IP      PORT(S)        AGE  
service/kubernetes                                     ClusterIP      10.83.240.1     <none>           443/TCP        34m  
                                                         
NAME                                                   DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE  
deployment.apps/harness-example-deployment             2         2         2            2           34m  
                                                         
NAME                                                   DESIRED   CURRENT   READY     AGE  
replicaset.apps/harness-example-deployment-5674658766  2         2         2         34m
```