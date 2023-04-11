# When to Queue

Queue steps can be added anywhere in your Stage, so it's important to add them whenever the resource you want to protect is being used.

For example, if Pipeline A will perform some Terraform provisioning and Pipeline B will deploy to the provisioned infrastructure, you will want to place the Queue step before the [Terraform Apply](../cd-advanced/terraform-category/run-a-terraform-plan-with-the-terraform-apply-step.md) step in Pipeline A, and before the deployment step in Pipeline B (such as a Kubernetes [Rolling step](../cd-execution/kubernetes-executions/create-a-kubernetes-rolling-deployment.md)).

