# Option: Git Providers

1. Click **Git**.
2. In **Repository Name**, enter a name.
3. In **GitOps Agent**, select or create the Agent you want to use to fetch manifests from this repo. See [Install a Harness GitOps Agent](install-a-harness-git-ops-agent.md).
4. In **Repository URL**, enter the URL to your repo. For example, `https://github.com/argoproj`.
   
   Typically, you will enter the URL to the root of the repo. Harness GitOps Repositories that use this template will use subfolders of that path for their **URL** settings.

5. Click **Continue**.
6. In **Credentials**, select one of the following:
   * Specify Credentials for Repository
      - In **Credentials**, in **Connection Type**, select **HTTPS**, or **SSH**, or **GitHub App**.
         - If you use Two-Factor Authentication for your Git repo, you connect over **HTTPS** or **SSH**.
         - For **SSH**, ensure that the key is not OpenSSH, but rather PEM format. To generate an SSHv2 key, use: `ssh-keygen -t rsa -m PEM` The `rsa` and `-m PEM` ensure the algorithm and that the key is PEM. Next, follow the prompts to create the PEM key. For more information, see the [ssh-keygen man page](https://linux.die.net/man/1/ssh-keygen).
         - **HTTP** also has the **Anonymous** option.
      - For steps on setting up the GitHub App, see [Use a GitHub App in a GitHub Connector](../../platform/7_Connectors/git-hub-app-support.md).
      - Click **Save & Continue**. Harness validates the connection.
   * Enable LFS support

     This option is available if you selected **HTTP**.

     Select the option to use [Git Large File Storage](https://github.com/git-lfs/git-lfs/).
   * Proxy

     This option is available if you selected **HTTP**.

     A proxy for your repository can be specified in the Proxy setting.

     Harness uses this proxy to access the repository. Harness looks for the standard proxy environment variables in the repository server if the custom proxy is absent.

     An example repository with proxy:


     ```yaml
     apiVersion: v1  
     kind: Secret  
     metadata:  
      name: private-repo  
      namespace: cd  
      labels:  
         argocd.argoproj.io/secret-type: repository  
     stringData:  
      type: git  
      url: https://github.com/argoproj/private-repo  
      proxy: https://proxy-server-url:8888  
      password: my-password  
      username: my-username
   ```
