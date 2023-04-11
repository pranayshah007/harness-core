### Configuration Type

This setting determines whether you want to apply a Terragrunt plan from a previous Terragrunt Plan step or run a separate Terragrunt script.

There are two options:

- **Inline**.
  - Select this option to add your Terragrunt script and apply it.
- **Inherit From Plan**.
  - Select this option to apply the Terragrunt plan implemented in a previous Terragrunt Plan step.
  - To identify the plan used in a previous Terragrunt Plan step, use the same **Provisioner Identifier** as that previous Terragrunt Plan step.


:::info

  Terragrunt Apply and Destroy steps do not support inheriting from a Terragrunt Plan step when **All Modules** is selected in the Terragrunt Plan step's **Module Configuration**.

:::
