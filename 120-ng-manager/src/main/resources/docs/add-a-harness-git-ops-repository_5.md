# Option: HTTP Helm Repository

1. Click **Helm**.
2. In **Repository Name**, enter a name.
3. In **GitOps Agent**, select or create the Agent you want to use to fetch charts from this repo. See [Install a Harness GitOps Agent](install-a-harness-git-ops-agent.md).
4. In **Repository URL**, enter the URL to your HTTP Helm Repository. For example, `https://charts.bitnami.com/bitnami`.
5. Click **Continue**.
6. In **Credentials**, select one of the following:
   * Specify Credentials for Repository
      - In **Credentials**, in **Connection Type**, select **HTTPS** or **SSH**.
         - If you use Two-Factor Authentication for your Git repo, you connect over **HTTPS** or **SSH**
         - For **SSH**, ensure that the key is not OpenSSH, but rather PEM format. To generate an SSHv2 key, use: `ssh-keygen -t rsa -m PEM`. The `rsa` and `-m PEM` ensure the algorithm and that the key is PEM. Next, follow the prompts to create the PEM key.
         - For more information, see the [ssh-keygen man page](https://linux.die.net/man/1/ssh-keygen).
      - **HTTP** also has the **Anonymous** option.
      - Click **Save & Continue**. Harness validates the connection.
   * Use a Credentials Template
      - Select the GitOps Credentials Template to use.

        See [Harness GitOps Repository Credentials Template](add-harness-git-ops-repository-credentials-template.md).

        If you use a Repository Credentials Template for GitOps Repository authentication, then the repo path in the GitOps Repository must be a subfolder of the repo path in the Repository Credentials Template.

        For example, if you created a Repository Credentials Template for the URL `https://something.com`, GitOps Repositories that have their URL as `https://something.com/*` are able to use that Repository Credentials Template.

        Harness will auto-detect the Repository Credentials Template (if any) based on the GitOps Repository **URL** and auto-populate it. If Harness auto-populated the GitOps Repository, then you cannot edit the Repository Credentials Template setting.
