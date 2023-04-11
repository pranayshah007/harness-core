# Enter the path and name of the manifest

1. In **File Path**, enter the path to a manifest file.

The **File Path** setting contains the path to the file you want deployed. The file must be in the same repo as the manifest(s) you selected in the Harness service being deployed by this stage. 

The repo is specified in the service's **Service Definition** settings, in **Manifests**. The file entered in **File Path** must be subordinate to that path.

In the following example, the path used in the **Manifests** section of the Service Definition is `default-k8s-manifests/Manifests/Files/templates/`. The **Apply** step uses a Job manifest in the subfolder `jobs/job.yaml`.

<!-- ![](./static/bded796afd9394b571b7c2229ac96ad99ff608558bfa4aa2daf5ab670f886578.png) -->

<docimage path={require('./static/bded796afd9394b571b7c2229ac96ad99ff608558bfa4aa2daf5ab670f886578.png')} />


You can enter multiple file paths in **File Path**. Simply click **Add File**.
