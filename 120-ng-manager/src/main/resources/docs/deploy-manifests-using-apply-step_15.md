# Skip k8s manifest(s) rendering

By default, Harness uses Go templating and a values.yaml for templating manifest files. 

In some cases, you might not want to use Go templating because your manifests use some other formatting.

Use the **Skip K8s Manifest(s) Rendering** option if you want Harness to skip rendering your manifest files using Go templating.

For details, go to [Deploy Manifests Separately using Apply Step](deploy-manifests-using-apply-step.md).
