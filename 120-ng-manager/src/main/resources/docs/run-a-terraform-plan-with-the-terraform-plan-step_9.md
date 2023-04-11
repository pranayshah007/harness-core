## Command

In **Command**, select **Apply or Destroy**.

* **Apply:** The plan will be applied by a [Terraform Apply](run-a-terraform-plan-with-the-terraform-apply-step.md) step later in your stage. Even though you are only running a Terraform plan in this step, you identify that this step can be used with a Terraform Apply step later.
* **Destroy:** The plan will be applied by a [Terraform Destroy](remove-provisioned-infra-with-terraform-destroy.md) step later in your stage.
