# Review: Common Parallel Steps

Running steps in parallel can be beneficial in many ways, such as:

* Simulating load using multiple [HTTP steps](../../cd-execution/cd-general-steps/using-http-requests-in-cd-pipelines.md).
* Running multiple [Verify steps](../../cd-execution/cv-category/verify-deployments-with-the-verify-step.md) for different providers (AppDynamics, Splunk, Prometheus, etc).
* Running independent steps that don't need to be run serially.
* Running multiple Kubernetes [Apply](../cd-k8s-ref/kubernetes-apply-step.md) steps to deploy multiple Kubernetes resources at once.
* [Deleting](../../cd-execution/kubernetes-executions/delete-kubernetes-resources.md) multiple resources at once.
* Creating or updating multiple Jira issues using [Jira Create](../../cd-advanced/ticketing-systems-category/create-jira-issues-in-cd-stages.md) and [Jira Update](../../cd-advanced/ticketing-systems-category/update-jira-issues-in-cd-stages.md).
* Provisioning multiple resources using Terraform. See [Provision with the Terraform Apply Step](../../cd-advanced/terraform-category/run-a-terraform-plan-with-the-terraform-apply-step.md).
* Save time. You might have 5 steps but you can run steps 2 and 3 in parallel because they are independent. Step 4 is run once they have completed.
