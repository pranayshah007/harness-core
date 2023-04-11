## Kubernetes

You can use values YAML files with your Kubernetes manifests in Harness. This allows you to define several Kubernetes resources as a set.


```bash
files/  
|-values.yaml  
|-templates/  
 |-deployment.yaml  
 |-namespace.yaml  
 |-service.yaml
```

Harness evaluates the values.yaml files you add just like Helm does with its values file. Values.yaml files added to Harness don't use Helm templating, but instead use [Go templating](https://godoc.org/text/template) and [Harness built-in variable expressions](../../../platform/12_Variables-and-Expressions/harness-variables.md). This removes the need for Helm or Tiller to be installed.
