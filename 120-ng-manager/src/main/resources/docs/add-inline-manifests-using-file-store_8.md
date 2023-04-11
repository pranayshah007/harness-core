# Selecting Kubernetes Manifests and Values YAML File from File Store

You can select and apply the File Store folder with the Kubernetes manifests and the values.yaml file to your pipeline.

1. In File Store, select the CD\_Manifests folder that contains the manifests, and click **Apply Selected**. File Store in **Manifest Details** is populated with the /CD\_Manifests folder and the manifests within that folder.
2. In **Manifest Details**, select Values.yaml.
3. In **Create or Select an Existing Config file**, click **Project**, and navigate to the CD\_Manifests/Templates folder. Select values.yaml and click **Apply Selected**.
4. **Manifest Details** is now populated with the values.yaml file. Click **Submit**. The manifests that you created for this service are now applied to the service.

![](./static/add-inline-manifests-using-file-store-13.png)
