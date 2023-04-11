### Connector

1. Click the **Connector** dropdown menu.
2. Click **New Connector**.
3. In **Name**, enter **GitHub**, and then click **Continue**.
4. In **URL Type**, select **Repository**.
5. In **Connection Type**, select **HTTP**.
6. In **GitHub Repository URL**, enter `https://github.com/kubernetes/website`.
7. Click **Continue**.
8. In **Username**, enter your GitHub account username.
9. In **Personal Access Token**, click **Create or Select a Secret**.
10. Click **New Secret Text**.
11. In **Secret Name**, enter the name **github-pat**.
12. In **Secret Value**, paste in a GitHub Personal access token.When you're logged into GitHub, these are typically listed at <https://github.com/settings/tokens>.
  For steps on setting up a GitHub PAT, see [Creating a personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) from GitHub.
  Ensure you PAT has the **repo** scope selected:
  ![](static/repoScope.png)
1. Click **Save**, and then click **Continue**.
2. In **Connect to the provider**, click **Connect through a Harness Delegate**, and then **Continue**.
3. In **Delegates Setup**, click **Install a New Delegate**.
4. Click **Kubernetes**, and then click **Continue**.
5. Enter a name **quickstart** for the Delegate, click the **Laptop** size, and then click **Continue**.
6. Click **Download YAML file**.
  The YAML file for the Kubernetes Delegate will download to your computer. 

7. Open a terminal and navigate to where the Delegate file is located.If you're using a remote Kubernetes cluster, see [Notes](#notes).

    In the terminal, run this command:

    ```bash
    kubectl apply -f harness-delegate.yaml
    ```

    This installs the Delegate into the default cluster that comes with Docker Desktop Kubernetes. It can take a few minutes for the Delegate pod to run.
8. Run `kubectl get pods -n harness-delegate-ng` to verify that it is **Ready: 1/1** and **Status: Running**.
1. Back in Harness, click **Continue**.
2. Once the Delegate registers, click **Done**.
3. In **Delegates Setup**, click **Connect only via Delegates which has all of the following tags**, and then select the tag for your new Delegate (**quickstart**).
4. Click **Save and Continue**.
5. The **Connection Test** should prove successful. If not, review your credentials.
6. Click **Finish**.
