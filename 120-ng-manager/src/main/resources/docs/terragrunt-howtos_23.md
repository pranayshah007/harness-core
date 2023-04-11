### Configuration Type

You can add a Terragrunt Destroy step to remove any provisioned infrastructure, just like running the `terragrunt run-all destroy` command. See [destroy](https://terragrunt.gruntwork.io/docs/features/execute-terraform-commands-on-multiple-modules-at-once/#the-run-all-command) from Terragrunt.

* In **Configuration Type**, select how you want to destroy resources:

    - **Inherit From Plan**. Destroy the resources from a Terraform Plan step.
        - Using the Terragrunt Destroy step with a previous Terragrunt Plan step is the same as using the `terragrunt plan` command with the `-destroy` flag.
        - To use this Terraform Destroy step with a Terraform Plan step, you must select **Destroy** in the **Command** setting of the **Terraform Plan** step.
     - **Inherit From Apply**. Destroy the resources from a Terraform Apply step.
        - Using the Terragrunt Destroy step with a previous Terragrunt Apply step is the same as using the `terragrunt apply` command with the `-destroy` flag.
    - **Inline**. Destroy any resources using a Terragrunt script.
