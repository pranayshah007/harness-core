# Limitations

You cannot use [Harness variables](../../../platform/12_Variables-and-Expressions/harness-variables.md) in Kubernetes manifests. You can only use Harness variables in Values YAML files. Harness support Go templating, so you can use variables in Values YAML files and have the manifests reference those variables/values.
