# Add a Git repository

In the Repository setup, you will select the [Agent](install-a-harness-git-ops-agent.md) to use when synching state. Be sure you have a GitOps Agent set up already.

See [Install a Harness GitOps Agent](install-a-harness-git-ops-agent.md).

You will also provide the credentials to use when connecting to the Git repository. Ensure you have your credentials available.

If you use a [GitOps Repository Credentials Template](add-harness-git-ops-repository-credentials-template.md) with a GitOps Repository, then the repo path in the GitOps Repository must be a subfolder of the repo path in the Repository Credentials Template.

1. In your Harness Project, click **GitOps**, and then click **Settings**.
2. Click **Repositories**.
3. Click **New Repository**.

   ![](./static/add-a-harness-git-ops-repository-80.png)

4. In **Specify Repository Type**, click **Git** or **Helm**.
