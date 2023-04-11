## Add HELM\_LOCAL\_REPOSITORY environment variable to Delegate YAML

You need to provide the path to the local chart in the Delegate YAML using the `HELM_LOCAL_REPOSITORY` environment variable.

1. Add the `HELM_LOCAL_REPOSITORY` environment variable to the StatefulSet (Legacy Delegate) or Deployment (Immutable Delegate) object in the Delegate YAML.

The format should be:

`<basePath>/<repoName(encoded)>/<chartName>/<version>/chartName/`

Here's an example:


```
...  
        env:  
        - name: HELM_LOCAL_REPOSITORY  
          value: "./repository/helm/source/69434bd8-4b9d-37d8-a61f-63df65cd8206/nginx/0.1.0/nginx"  
...
```
If the chart version is not included, Harness will fetch the `latest` version.

The `HELM_LOCAL_REPOSITORY` environment variable is the same for both Delegate types.

For information on Delegate types, go to [Install a Kubernetes Delegate](../../../platform/2_Delegates/install-delegates/install-a-kubernetes-delegate.md) or [Install a Docker Delegate](../../../platform/2_Delegates/install-delegates/overview.md).
