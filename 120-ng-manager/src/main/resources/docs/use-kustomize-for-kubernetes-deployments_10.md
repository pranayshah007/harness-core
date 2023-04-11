# Review: Artifact Sources and Kustomization

You can list artifacts in two ways:

* Artifacts can be hardcoded in the deployment YAML file deployed using your Kustomization files.
* You can add artifacts to the Service **Artifacts** section and reference them in Kustomize Patch files using the Harness variable `<+artifact.image>`. See [Option: Kustomize Patches](#option-kustomize-patches) below, and [Built-in Harness Variables Reference](../../../platform/12_Variables-and-Expressions/harness-variables.md).
