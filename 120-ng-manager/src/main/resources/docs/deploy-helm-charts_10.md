# Option: Reference the artifact

If the image artifact is not hardcoded in the Helm chart, add the artifact in **Artifacts** and use the expression `<+artifact.image>` in your values.yaml. For example:


```yaml
...  
image: <+artifact.image>  
pullPolicy: IfNotPresent  
dockercfg: <+artifact.imagePullSecret>  
...
```

This is the same method when using artifacts with standard Kubernetes deployments. For more information, go to [Add Container Images as Artifacts for Kubernetes Deployments](../cd-kubernetes-category/add-artifacts-for-kubernetes-deployments.md).
