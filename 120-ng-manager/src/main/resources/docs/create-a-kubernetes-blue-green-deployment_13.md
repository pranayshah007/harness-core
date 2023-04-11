## Apply

The Apply section applies a services and deployment from the Prepare section. It uses a combination of all of the manifests in the Service **Manifests** section as one file using `kubectl apply`.


```
kubectl --kubeconfig=config apply --filename=manifests.yaml --record  
  
service/bgdemo-svc created  
  
deployment.apps/bgdemo-blue created  
  
service/bgdemo-svc-stage created  
  
Done.
```
