# CloudFormation Rollback Stack step

The CloudFormation Rollback Step is automatically added to the **Rollback** section.

![](./static/provision-target-deployment-infra-dynamically-with-cloud-formation-08.png)

When rollback happens, Harness runs the last successfully provisioned version of the stack.

Open **CloudFormation Rollback Stack**.

Enter a name and timeout for the step.

In **Provisioner Identifier**, enter the same Provisioner Identifier you used in the Create Stack step.

Harness determines what to rollback using a combination of `Provisioner Identifier + Harness account id + Harness org id + Harness project id`.

If you've made these settings expressions, Harness uses the values it obtains at runtime when it evaluates the expression.
