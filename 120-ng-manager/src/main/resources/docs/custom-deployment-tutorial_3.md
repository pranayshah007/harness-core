# Important notes

Unlike the deployments for supported platforms, like Kubernetes and AWS, Deployment Templates have certain limitations:

* All artifact providers and [Custom artifact](../../cd-services/cd-services-general/add-a-custom-artifact-source-for-cd.md) are supported:  


| **Type** | **Nexus3** | **Artifactory** | **Jenkins** | **Amazon S3** | **Docker Registry** | **AWS ECR** | **GCR** | **ACR** | **Google Artifact Registry** | **Custom** |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Docker | x | x |  |  | x | x | x | x |  |  |
| Other(ZIP, Jobs, etc.) |  | x | x | x |  |  |  |  | x | x |
* No steady state checks on deployed services.
* Harness does not track releases.

You can add your own scripts or tests to your Pipelines to describe deployments, check steady state, and track releases. For example, using the [Shell Script](../../../first-gen/continuous-delivery/model-cd-pipeline/workflows/capture-shell-script-step-output.md) or [HTTP](../../cd-execution/cd-general-steps/using-http-requests-in-cd-pipelines.md) steps.
