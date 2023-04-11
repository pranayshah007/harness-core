### Add Github Connector

1. In **Github Connector**, click **New Github Connector**.
2. Enter the following Github Connector settings:
    1. **Name:** enter **gitops-github**.
    2. **URL Type:** select **Repository**.
    3. **Connection Type:** select **HTTP**.
    4. **GitHub Repository URL:** enter the HTTP URL for repo you used for your ApplicationSet, such as `https://github.com/johnsmith/applicationset.git`.
    5. **Authentication:** select **Username and Token**. For the Token, you'll need to use a Personal Access Token (PAT) from Github. If you are logged into Github, just go to <https://github.com/settings/tokens>.
    6. Ensure the PAT has the **repo** scope selected.
   
   ![](./static/harness-git-ops-application-set-tutorial-52.png)
   
   You will store the PAT in a [Harness Text Secret](../../platform/6_Security/2-add-use-text-secrets.md). For details on Secrets Management, go to [Harness Secrets Management Overview](../../platform/6_Security/1-harness-secret-manager-overview.md).
    
    7. Select **Enable API access** and use the same Harness Secret.
3. Click **Continue**.
4. In **Connect to the provider**, select **Connect through Harness Platform**., and then click **Save and Continue**.
5. When the **Connection Test** in complete, click **Continue**.
