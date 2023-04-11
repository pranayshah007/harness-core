# Step 1: Add the CloudFormation Rollback Stack

In the **Rollback** section of your Deploy stage, click **Add Step**, and then select the **CloudFormation Rollback Stack** step.

![](./static/rollback-provisioned-infra-with-the-cloud-formation-rollback-step-03.png)

When rollback happens, Harness runs the last successfully provisioned version of the stack.

Open **CloudFormation Rollback Stack**.

Enter a name and timeout for the step.

In **Provisioner Identifier**, enter the same Provisioner Identifier you used in the **Create Stack** step.

Harness determines what to rollback using a combination of `Provisioner Identifier + Harness account id + Harness org id + Harness project id`.

If you've made these settings expressions, Harness uses the values it obtains at runtime when it evaluates the expression.

