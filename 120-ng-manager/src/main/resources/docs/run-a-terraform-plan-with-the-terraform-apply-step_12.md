## Configuration File Repository

**Configuration File Repository** is where the Terraform script and files you want to use are located.

Here, you'll add a connection to the Terraform script repo.

Click **Specify Config File** or edit icon.

The **Terraform Config File Store** settings appear.

Click the provider where your files are hosted.

![](./static/run-a-terraform-plan-with-the-terraform-apply-step-05.png)

Select or create a Connector for your repo. For steps, see [Connect to a Git Repo](../../../platform/7_Connectors/connect-to-code-repo.md) or [Artifactory Connector Settings Reference](../../../platform/7_Connectors/ref-cloud-providers/artifactory-connector-settings-reference.md) (see **Artifactory with Terraform Scripts and Variable Definitions (.tfvars) Files**).

In **Git Fetch Type**, select **Latest from Branch** or **Specific Commit ID**. When you run the Pipeline, Harness will fetch the script from the repo.

**Specific Commit ID** also supports [Git tags](https://git-scm.com/book/en/v2/Git-Basics-Tagging). If you think the script might change often, you might want to use **Specific Commit ID**. For example, if you are going to be fetching the script multiple times in your Pipeline, Harness will fetch the script each time. If you select **Latest from Branch** and the branch changes between fetches, different scripts are run.

In **Branch**, enter the name of the branch to use.

In **Folder Path**, enter the path from the root of the repo to the folder containing the script.

For example, here's a Terraform script repo, the Harness Connector to the repo, and the **Config Files** settings for the branch and folder path:

![](./static/run-a-terraform-plan-with-the-terraform-apply-step-06.png)

Click **Submit**.

Your Terraform Apply step is now ready. You can now configure a Terraform Destory or Rollback step that can use the Terraform script from this Terraform Apply step.

The following sections cover common Terraform Apply step options.
