## Remote Variables

You can connect Harness to remote variable files.

Click **Add Terraform Var File**, and then click **Add Remote**.

Select your Git provider (GitHub, etc.) and then select or create a Connector to the repo where the files are located. Typically, this is the same repo where your Terraform script is located, so you can use the same Connector.

Click **Continue**. The **Var File Details** settings appear.

![](./static/run-a-terraform-plan-with-the-terraform-apply-step-07.png)

In **Identifier**, enter an identifier so you can refer to variables using expressions if needed.

In **Git Fetch Type**, select **Latest from Branch** or **Specific Commit ID**.

In **Branch**, enter the name of the branch.

In **File Paths**, add one or more file paths from the root of the repo to the variable file.

Click **Submit**. The remote file(s) are added.
