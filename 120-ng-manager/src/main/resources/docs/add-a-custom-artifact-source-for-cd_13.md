### Values YAML in Manifests

You add a Values YAML file along with your manifests in the **Manifests** section of the Service.

The Values YAML can use Harness expressions to reference artifacts in the **Artifacts** section of the Service.

To reference the artifact, use the expression `<+artifact.image>` in your Values YAML file. For example:

```yaml
name: example  
replicas: 2  
  
image: <+artifact.image>  