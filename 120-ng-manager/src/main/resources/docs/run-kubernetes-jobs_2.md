# Review: Apply Step

CD stages include an **Apply** step that allows you to deploy *any resource* you have set up in the Service **Manifests** section.

For details on what you can deploy in different Harness deployment types, see [What Can I Deploy in Kubernetes?](../../cd-technical-reference/cd-k8s-ref/what-can-i-deploy-in-kubernetes.md).

The Apply step can deploy *all workload types*, including Jobs in any deployment type.

You can add an Apply step anywhere in your Harness stage. This makes the Apply step useful for running Kubernetes Jobs.

Here are some Job examples:

* Run independent but related work items in parallel: sending emails, rendering frames, transcoding files, or scanning database keys.
* Create a new pod if the first pod fails or is deleted due to a node hardware failure or node reboot.
* Create a Job that cleans up the configuration of an environment, to create a fresh environment for deployment.
* Use a Job to spin down the replica count of a service, to save on cost.

Any workload deployed with the **Apply** step is not rolled back by Harness.**Delete Jobs before rerunning deployments:** Once you've deployed the Job, you must delete it before deploying a Job of the same name to the same namespace.### Step 1: Add Job Manifest

In a CD stage, click Service.

In **Manifests**, add your manifests as described in [Add Kubernetes Manifests](../../cd-advanced/cd-kubernetes-category/define-kubernetes-manifests.md).

Include your Job manifest in the folder you specify in **Manifests**.

For example, here's a Manifests section that points to the **templates** folder.

![](./static/run-kubernetes-jobs-13.png)

In the templates folder, there is a folder named **jobs** and a **job.yaml** manifest.

![](./static/run-kubernetes-jobs-14.png)
