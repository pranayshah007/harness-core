## values.yaml

This file uses the `image: <+artifact.image>` to identify the primary artifact added in the Harness Service Definition **Artifacts** section.

It also uses `name: <+stage.name>` to reference a Stage variable `name` and `namespace: <+infra.namespace>` to reference the namespace entered in the Stage's **Infrastructure Definition**. Service type and ports are hardcoded.

The name, image, and namespace values are referenced in the manifests described later.


```yaml
name: <+stage.name>  
replicas: 2  
  
image: <+artifact.image>  