# Option: HTTP Helm Repository

1. Click **Helm**.
2. In **Repository Name**, enter a name.
3. In **GitOps Agent**, select or create the Agent you want to use to fetch charts from this repo. See [Install a Harness GitOps Agent](install-a-harness-git-ops-agent.md).
4. In **Repository URL**, enter the URL to your HTTP Helm Repository. For example, `https://charts.bitnami.com/bitnami`.
5. Click **Continue**.
6. In **Credentials**, in **Connection Type**, select **HTTPS** or **SSH**.
   - If you use Two-Factor Authentication for your Git repo, you connect over **HTTPS** or **SSH**.
   - For **SSH**, ensure that the key is not OpenSSH, but rather PEM format. To generate an SSHv2 key, use: `ssh-keygen -t rsa -m PEM` The `rsa` and `-m PEM` ensure the algorithm and that the key is PEM. Next, follow the prompts to create the PEM key. For more information, see the [ssh-keygen man page](https://linux.die.net/man/1/ssh-keygen).
   - **HTTP** also has the **Anonymous** option.
7.  Click **Save & Continue**. Harness validates the connection.
8.  Click **Finish**. You now have a Harness GitOps Repository Credentials Template added.
