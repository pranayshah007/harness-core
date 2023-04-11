### Provisioner Identifier

There are two options for **Provisioner Identifier**:

- If you are destroying the resources from a previous Terragrunt Plan or Terragrunt Apply step, enter the **Provisioner Identifier** from that step in **Provisioner Identifier** in this Terragrunt Destroy step.
- If you are using the **Inline** option in **Configuration Type**, enter a unique value in **Provisioner Identifier**.

The most common use of **Provisioner Identifier** is to destroy resources from a Terragrunt Plan or Terragrunt Apply step. 

Here's an example of how the **Provisioner Identifier** is used across steps:

<!-- ![](./static/2161eed44e5b1ef3369542d40747af39160c7a25b71f03f160ce1e29329c6bab.png) -->

<docimage path={require('./static/2161eed44e5b1ef3369542d40747af39160c7a25b71f03f160ce1e29329c6bab.png')} />
