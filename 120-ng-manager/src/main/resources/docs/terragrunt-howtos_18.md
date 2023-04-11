### Provisioner Identifier

The **Provisioner Identifier** identifies the provisioning performed in this step.

* Enter a unique value in **Provisioner Identifier**.

The **Provisioner Identifier** can be used with other steps to perform common Terragrunt tasks:

- Apply a Terragrunt plan:
  - Use the same **Provisioner Identifier** from a previous Terraform Plan step.
  - Set **Configuration Type** in this Terraform Apply step to **Inherit From Plan**. 
- Destroy resources provisioned by this Terragrunt Apply step:
  - Use the same **Provisioner Identifier** in this Terragrunt Apply step and a subsequent Terragrunt Destroy step.
- Roll back the provisioning performed by this Terragrunt Apply step:
  - Use the same **Provisioner Identifier** in this Terragrunt Apply step and a Terragrunt Rollback step (in the **Rollback** section of **Execution**).


Here's an example of how the **Provisioner Identifier** is used across steps:

<!-- ![](./static/2161eed44e5b1ef3369542d40747af39160c7a25b71f03f160ce1e29329c6bab.png) -->

<docimage path={require('./static/2161eed44e5b1ef3369542d40747af39160c7a25b71f03f160ce1e29329c6bab.png')} />
