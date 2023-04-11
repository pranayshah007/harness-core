# File Path

Enter the path to a manifest file.

**File Path** has the following requirements:

* The path to the manifest for the Apply step must be subordinate to the path for the manifest in the **Manifests** section of the Service Definition. The manifest cannot be in the same folder as **Manifests**.
* The path must include the folder name and the file name.

In the following example, the path used in the **Manifests** section of the Service Definition is `default-k8s-manifests/Manifests/Files/templates/`. The **Apply** step uses a Job manifest in the subfolder `jobs/job.yaml`.

![](./static/kubernetes-apply-step-00.png)

You can enter multiple file paths in File Path. Simply click **Add file**
