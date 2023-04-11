## Option 1: Apply Ignored Resource

The Apply step will apply any resource in a Service **Manifests** explicitly. You must provide the path and name of the file in **Apply**, and Harness will deploy the resource.

For details on the Apply Step, see [Deploy Manifests Separately using Apply Step](../../cd-execution/kubernetes-executions/deploy-manifests-using-apply-step.md).

For example, the following image shows a Jobs resource in a Service **Manifests** section that uses the ignore comment `# harness.io/skip-file-for-deploy` so that the stage does not apply it, and the **Apply** step that specifies the same Jobs resource:

![](./static/ignore-a-manifest-file-during-deployment-20.png)

The **File Path** setting in the Apply step must include the folder name and the file name. In the above example, the folder **jobs** is included with the file name **job.yaml**: `jobs/job.yaml`.

The path in **File Path** must be relative to the path you specified for the manifest in the Service **Manifests** section. 

For example, if the path in **Manifests** is `default-k8s-manifests/Manifests/Files/templates` and the Job manifests is in `default-k8s-manifests/Manifests/Files/templates/jobs/job.yaml` you must enter `jobs/job.yaml` in **File Path**.

You can include multiple resource files in the Apply step **File Path** setting.

If you apply the ignore comment `# harness.io/skip-file-for-deploy` to a resource but do not use the resource in an **Apply** step, the resource is never deployed.