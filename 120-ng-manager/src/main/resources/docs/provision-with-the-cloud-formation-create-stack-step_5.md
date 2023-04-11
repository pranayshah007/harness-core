## Provisioner Identifier

Enter a unique value in **Provisioner Identifier**.

The **Provisioner Identifier** identifies the provisioning done by this step. You reference the **Provisioner Identifier** in other steps to refer to the provisioning done by this step.

Only one **Create Stack** step with a specific **Provisioner Identifier** can be added in the same stage. If you add multiple **Create Stack** steps with the same **Provisioner Identifier**, only the first **Create Stack** step will be successful.The most common use of **Provisioner Identifier** is between the Create Stack, Delete Stack, and Rollback Stack steps.

For example, in the case of a **Create Stack** failure, the **Rollback Stack** step rolls back the provisioning from the **Create Stack** step using its **Provisioner Identifier**.

![](./static/provision-with-the-cloud-formation-create-stack-step-01.png)

Ultimately, Harness determines what stack to roll back to using a combination of `Provisioner Identifier + Harness account id + Harness org id + Harness project id`.
