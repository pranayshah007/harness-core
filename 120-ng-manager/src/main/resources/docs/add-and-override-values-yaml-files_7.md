# Review: Artifacts with Manifests and Charts

You can hardcode the deployment artifact in your values.yaml file just as you might in a typical Helm deployment.

Or you can add a path to the artifact in Harness and use a [Harness expression](../../../platform/12_Variables-and-Expressions/harness-variables.md) in your values.yaml to refer to that path.

When Harness executes the Pipeline, the Harness Delegate resolves the expression and pulls the artifact onto the target pods.

Adding artifacts to Harness is covered in [Add Container Images as Artifacts for Kubernetes Deployments](add-artifacts-for-kubernetes-deployments.md).

Once you've added the artifact to Harness, you add the Harness expression `<+artifact.image>` in your values.yaml using the `image` label: `image: <+artifact.image>`.

For example:


```yaml
name: myapp  
replicas: 2  
  
image: <+artifact.image>  
dockercfg: <+artifact.imagePullSecret>  
...
```

Artifacts and manifests are discussed in detail in [Add Kubernetes Manifests](define-kubernetes-manifests.md).
