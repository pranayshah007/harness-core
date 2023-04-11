## Helm chart using artifact added to the stage

You add an image artifact to the **Artifacts** section of the service and then reference it in the Helm chart values.yaml file.

Artifacts in the **Artifacts** section are referenced using the `<+artifact.image>` expression. For example:


```yaml
...  
image: <+artifact.image>  
pullPolicy: IfNotPresent  
dockercfg: <+artifact.imagePullSecret>  
...
```

This is the same method when using artifacts with standard Kubernetes deployments. See [Add Container Images as Artifacts for Kubernetes Deployments](../cd-kubernetes-category/add-artifacts-for-kubernetes-deployments.md).
