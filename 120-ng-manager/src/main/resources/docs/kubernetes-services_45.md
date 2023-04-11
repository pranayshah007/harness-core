# Go templating

Harness supports [Go templating](https://godoc.org/text/template) for Kubernetes manifests and values YAML files. 

You can add one or more values YAML files containing values for different scenarios, and then use Go templating in the manifest files to reference the values in the values YAML files.

Built-in Go templating support enables you to use Kubernetes without the need for Helm.

For more information, see [Example Kubernetes Manifests using Go Templating](../../cd-technical-reference/cd-k8s-ref/example-kubernetes-manifests-using-go-templating.md).

Let's look at a few Kubernetes templating examples.

<details>
<summary>Basic values YAML and manifests for a public image</summary>

Here's the values YAML file:

```yaml
name: <+stage.name>  
replicas: 2  
  
image: <+artifacts.primary.image>  