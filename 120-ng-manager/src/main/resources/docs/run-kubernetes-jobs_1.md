# Before You Begin

* **​Kubernetes Jobs:** We assume you are familiar with [Kubernetes Jobs](https://kubernetes.io/docs/concepts/workloads/controllers/jobs-run-to-completion/).
* **Apply step:** The Harness Apply step allows you to deploy any resource you have set up in the Service **Manifests** section at any point in your stage. See [Deploy Manifests Separately using Apply Step](deploy-manifests-using-apply-step.md).
* **Ignoring Manifests:** You can annotate a manifest to have Harness ignore it when performing its main deployment operations. Then you can use the Apply step to execute the manifest wherever you want to run it in the stage. See [Ignore a Manifest File During Deployment](../../cd-advanced/cd-kubernetes-category/ignore-a-manifest-file-during-deployment.md).
* **Delete Jobs before rerunning deployments:** Once you've deployed the Job, you must delete it before deploying a Job of the same name to the same namespace.
