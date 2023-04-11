# Option: Add Values YAML File in Manifest Folder

If you have a Values YAML file in the same folder as the manifests, you can simply enter the folder path in **File/Folder Path** and Harness will fetch and apply the values file along with the manifests.

If you also add a separate Values YAML file in Manifests using the **Values YAML** type (described below), that Values YAML will overwrite any matching values in the Values YAML in the Manifest **File/Folder Path**.

![](./static/define-kubernetes-manifests-31.png)
