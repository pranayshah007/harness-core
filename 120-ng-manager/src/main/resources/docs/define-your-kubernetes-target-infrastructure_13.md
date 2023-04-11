### Namespace

1. Enter the namespace of the target Kubernetes cluster. Typically, this is `default`.

The namespace must already exist during deployment. Harness will not create a new namespace if you enter one here.

You can enter a fixed value, runtime input, or expression.

You can reference the namespace in your manifest using the Harness expression: `<+infra.namespace>`.

For example, if you entered `default` in **Namespace**, in your values.yaml you can use:

```yaml
name: myApp  
replicas: 2  
  
image: <+artifact.image>  
  
createNamespace: true  
namespace: <+infra.namespace>  
...
```

And then in the Namespace object manifest (and any object manifest that uses the namespace) you reference the values.yaml value for namespace:


```yaml
 {{- if .Values.createNamespace}}  
apiVersion: v1  
kind: Namespace  
metadata:  
 name: {{.Values.namespace}}  
{{- end}}
```

Now your values.yaml and manifest is templated for use with any stage.

For more information about manifests in Harness, see [Add Kubernetes Manifests](../../cd-advanced/cd-kubernetes-category/define-kubernetes-manifests.md).

If you omit the `namespace` key and value from a manifest in your Service, Harness automatically uses the namespace you entered in the Harness Environment  **Infrastructure Definition** settings **Namespace** field.
