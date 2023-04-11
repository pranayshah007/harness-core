### Provisioner Identifier

You use the **Provisioner Identifier** in subsequent steps to refer to the plan run in this step.

* Enter a unique value in Provisioner Identifier.

The most common use of **Provisioner Identifier** is between the Terragrunt Plan and Terragrunt Apply or Terragrunt Destroy steps. 

For the Terragrunt Apply step to apply the plan from the Terragrunt Plan step, it uses the same **Provisioner Identifier**.

For the Terragrunt Destroy step to destroy the plan from the Terragrunt Plan step, it uses the same **Provisioner Identifier**.

Here's an example of how the **Provisioner Identifier** is used across steps:

<!-- ![](./static/2161eed44e5b1ef3369542d40747af39160c7a25b71f03f160ce1e29329c6bab.png) -->

<docimage path={require('./static/2161eed44e5b1ef3369542d40747af39160c7a25b71f03f160ce1e29329c6bab.png')} />
