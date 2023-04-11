## Using Terragrunt steps together

The Terragrunt steps can be used independently or you can connect them by using the same **Provisioner Identifier** in all of the steps.

Here's how to use all the steps together:

1. Terragrunt Plan step:
   1. Add the Terragrunt Plan step and define the Terragrunt script for it to use.
   2. Select **Apply** in **Command**. 
   3. Enter a **Provisioner Identifier**.
2. Terragrunt Apply step:
   1. Select **Inherit from Plan** in **Configuration Type**.
   2. Reference the Terragrunt Plan step using the same **Provisioner Identifier**.
3. Terragrunt Destroy step:
   1. Select **Inherit from Apply** or **Inherit from Plan** in **Configuration Type**.
   1. Reference the Terragrunt Apply or Plan step using the same **Provisioner Identifier**.
4. Terragrunt Rollback step:
   1. Reference the Terragrunt Apply or Plan step using the same **Provisioner Identifier**.

<!-- ![](./static/2161eed44e5b1ef3369542d40747af39160c7a25b71f03f160ce1e29329c6bab.png) -->

<docimage path={require('./static/2161eed44e5b1ef3369542d40747af39160c7a25b71f03f160ce1e29329c6bab.png')} />

Each of these steps is described below in [Terragrunt steps](#terragrunt-steps).

