## Wait for Steady State

The Wait for Steady State section shows the containers and pods rolled out.


```
kubectl --kubeconfig=config get events --output=custom-columns=KIND:involvedObject.kind,NAME:.involvedObject.name,MESSAGE:.message,REASON:.reason --watch-only  
  
kubectl --kubeconfig=config rollout status Deployment/harness-example-deployment --watch=true  
  
  
Status : Waiting for deployment "harness-example-deployment" rollout to finish: 0 of 2 updated replicas are available...  
Event  : Pod    harness-example-deployment-5674658766-6b2fw   Successfully pulled image "registry.hub.docker.com/library/nginx:stable-perl"   Pulled  
Event  : Pod   harness-example-deployment-5674658766-p9lpz   Successfully pulled image "registry.hub.docker.com/library/nginx:stable-perl"   Pulled  
Event  : Pod   harness-example-deployment-5674658766-6b2fw   Created container   Created  
Event  : Pod   harness-example-deployment-5674658766-p9lpz   Created container   Created  
Event  : Pod   harness-example-deployment-5674658766-6b2fw   Started container   Started  
Event  : Pod   harness-example-deployment-5674658766-p9lpz   Started container   Started  
  
Status : Waiting for deployment "harness-example-deployment" rollout to finish: 1 of 2 updated replicas are available...  
  
Status : deployment "harness-example-deployment" successfully rolled out  
  
Done.
```
