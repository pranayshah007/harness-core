# Step 5: Add Values YAML Files

You add a values file in the same way you added your manifests. You simply select **Values YAML** in **Specify Manifest Type**.

In **Manifest Details**, you enter the path to each values.yaml file.

Your values YAML files can use [Harness variables](../../../platform/12_Variables-and-Expressions/harness-variables.md) to reference artifacts in the **Service Definition** (`<+artifact.image>`), Stage and Service variables, and and other Harness variables.

Your manifests reference your values YAML file using [Go templating](https://godoc.org/text/template), as described above.

You cannot use [Harness variables](../../../platform/12_Variables-and-Expressions/harness-variables.md) in Kubernetes manifests. You can only use Harness variables in values YAML files. See [Example Kubernetes Manifests using Go Templating](../../cd-technical-reference/cd-k8s-ref/example-kubernetes-manifests-using-go-templating.md).

