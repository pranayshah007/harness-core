### Command

The **Command** setting indicates how this plan will be used later in the stage. 

There are two options:
- **Apply**. Select this option if you will apply the plan with a subsequent Terragrunt Apply step.
- **Destroy**. Select this option if you will destroy the plan with a subsequent Terragrunt Destroy step.

:::info

  Terragrunt Apply and Destroy steps do not support inheriting from a Terragrunt Plan step when **All Modules** is selected in the Terragrunt Plan step's **Module Configuration**.

:::
