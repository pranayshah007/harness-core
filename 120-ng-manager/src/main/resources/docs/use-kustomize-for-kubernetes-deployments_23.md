# Kustomize deployment with the Apply step

You can use the Apply step in your Kustomize deployments.

When you use Kustomize, the **File Path** in the Apply Step should be set according to the following conditions.

* **Default:** Apply step **File Path** is the path from the root of the repo to the folder with the kustomization YAML file is located. This is the same as the Kustomize Folder Path setting in the **Manifest Details**.

![](./static/use-kustomize-for-kubernetes-deployments-13.png)* **Optimized Kustomize Manifest Collection:** When the **Optimized Kustomize Manifest Collection** option is enabled in **Manifest Details**, the Apply step **File Path** must be the same path as **Kustomize YAML Folder Path**.

![](./static/use-kustomize-for-kubernetes-deployments-14.png)
