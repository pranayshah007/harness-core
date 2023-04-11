 dockercfg: <+artifact.imagePullSecret>  
  
createNamespace: true  
namespace: <+infra.namespace>  
  
...
```

For details on using Values YAML in Harness, go to [Kubernetes Services](../k8s-services/kubernetes-services.md).

[Harness Variables and Expressions](../../../platform/12_Variables-and-Expressions/harness-variables.md) can be added to Values files (for example values.yaml), not the manifests themselves. This provides more flexibility.
