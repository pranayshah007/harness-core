# Option: Override chart values YAML in service

You can override the values YAML in the Helm chart by adding multiple values YAML files when you add the chart.

If you use multiple files, priority is given from the last file to the first file.

For example, let's say you have 3 files: the default values.yaml, values2.yaml added next, and values3.yaml added last.

![](./static/deploy-helm-charts-05.png)

All files contain the same key:value pair. The values3.yaml key:value pair overrides the key:value pair of values2.yaml and values.yaml files.

Your values.yaml file can use [Go templating](https://godoc.org/text/template) and [Harness built-in variable expressions](../../../platform/12_Variables-and-Expressions/harness-variables.md).

See [Example Kubernetes Manifests using Go Templating](../../cd-technical-reference/cd-k8s-ref/example-kubernetes-manifests-using-go-templating.md).
