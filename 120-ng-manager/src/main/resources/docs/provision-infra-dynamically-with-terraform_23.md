## Remote variables

You can connect Harness to remote variable files.

1. Click **Add Terraform Var File**, and then click **Add Remote**.
2. Select your Git provider (GitHub, etc.) and then select or create a Connector to the repo where the files are located. Typically, this is the same repo where your Terraform script is located, so you can use the same Connector.
3. Click **Continue**. The **Var File Details** settings appear.
   
   ![](./static/provision-infra-dynamically-with-terraform-04.png)
4. In **Identifier**, enter an identifier so you can refer to variables using expressions if needed.
   
   For example, if the **Identifier** is **myremotevars** you could refer to its content like this:
   
   `<+pipeline.stages.MyStage.spec.infrastructure.infrastructureDefinition.provisioner.steps.plan.spec.configuration.varFiles.myremotevars.spec.store.spec.paths>`
5. In **Git Fetch Type**, select **Latest from Branch** or **Specific Commit ID**.
6. In **Branch**, enter the name of the branch.
7. In **File Paths**, add one or more file paths from the root of the repo to the variable file.
8. Click **Submit**. The remote file(s) are added.
