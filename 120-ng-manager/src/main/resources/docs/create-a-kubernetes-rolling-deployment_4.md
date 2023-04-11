# Define rollout strategy

There are no mandatory Rolling Update–specific settings for manifests in the Harness Service. You can use any Kubernetes configuration.

The default Rolling Update strategy used by Harness is:


```
RollingUpdateStrategy:  25% max unavailable, 25% max surge
```

If you want to set a Rolling Update strategy that is different from the default, you can include the strategy settings in your Deployment manifest:


```yaml
strategy:  
  type: RollingUpdate  
  rollingUpdate:  
    maxSurge: 1  
    maxUnavailable: 1
```

For details on the settings, see [RollingUpdateDeployment](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.20/#rollingupdatedeployment-v1-apps) in the Kubernetes API.
