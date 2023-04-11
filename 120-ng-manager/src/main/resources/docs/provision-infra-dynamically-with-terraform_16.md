## Git providers

1. In **Git Fetch Type**, select **Latest from Branch** or **Specific Commit ID**. When you run the Pipeline, Harness will fetch the script from the repo.
2. **Specific Commit ID** also supports [Git tags](https://git-scm.com/book/en/v2/Git-Basics-Tagging).If you think the script might change often, you might want to use **Specific Commit ID**. For example, if you are going to be fetching the script multiple times in your Pipeline, Harness will fetch the script each time. If you select **Latest from Branch** and the branch changes between fetches, different scripts are run.
3. In **Branch**, enter the name of the branch to use.
4. In **Folder Path**, enter the path from the root of the repo to the folder containing the script.
   
   For example, here's a Terraform script repo, the Harness Connector to the repo, and the **Config Files** settings for the branch and folder path:
   
   <!-- ![](./static/provision-infra-dynamically-with-terraform-03.png) -->
   
   <docimage path={require('./static/provision-infra-dynamically-with-terraform-03.png')} />
5. Click **Submit**.

Your Terraform Plan step is now ready. You can now configure the Terraform Apply step that will inherit the Terraform script and settings from this Terraform Plan step.

You can jump ahead to the Terraform Apply step below. The following sections cover common Terraform Plan step options.
