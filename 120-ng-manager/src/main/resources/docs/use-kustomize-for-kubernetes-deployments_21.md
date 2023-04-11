### Path to Plugin in Service Manifest

In the Harness Service that uses the Kustomization and plugin, in **Manifests**, select the existing Kustomize manifest or click **Add Manifest** and add a new as described in [Step 1: Add Manifests and Kustomization](#step-1-add-manifests-and-kustomization) above.

In **Manifest Details**, provide the path to the plugin on the Delegate host.

![](./static/use-kustomize-for-kubernetes-deployments-10.png)

Click **Submit**. Harness is now configured to use the plugin when it deploys using Kustomize.
