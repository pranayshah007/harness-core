## Apply

The Apply section deploys the manifests from the Service **Manifests** section as one file.


```
kubectl --kubeconfig=config apply --filename=manifests.yaml --record  
  
configmap/harness-example-config-3 configured  
deployment.apps/harness-example-deployment created  
  
Done.
```
