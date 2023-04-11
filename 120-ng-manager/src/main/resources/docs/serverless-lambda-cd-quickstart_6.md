# Add the manifest

Next, we can add a serverless.yaml for our deployment. We'll use [the publicly-available serverless.yaml file](https://github.com/wings-software/harness-docs/tree/main/serverless/artifacts) available from Harness.

1. In **Service Definition**, in **Deployment Type**, click **Serverless Lambda**.
2. In **Manifests**, click **Add Manifest**.
3. Select **Serverless Lambda Manifest**, and click **Continue**.
4. In **Specify Serverless Lambda Manifest Store**, click **GitHub**, and then click **New GitHub Connector**.
   The **Git Connector** settings appear. Enter the following settings.
   * **Name:** `serverless`.
   * **URL Type:** `Repository`.
   * **Connection Type:** `HTTP`.
   * **GitHub Repository URL:** `https://github.com/wings-software/harness-docs.git`.
   * **Username:** Enter your GitHub account username.
   * In **Personal Access Token**, click **Create or Select a Secret**.
     * Click **New Secret Text**.
     * In **Secret Name**, enter a name for the secret like **github-pat**.
     * In **Secret Value**, paste in a GitHub Personal access token.When you're logged into GitHub, these tokens are listed at <https://github.com/settings/tokens>. For steps on setting up a GitHub PAT, see [Creating a personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) from GitHub.
     * Make sure your PAT has the **repo** scope selected:

	![](static/repoScope.png)
1. Select **Connect through Harness Platform**.
1. Click **Finish**.
2. Back in **Specify Serverless Lambda Manifest Store**, click **Continue**.
3. In **Manifest Details**, enter the following.
   * **Manifest Identifier:** `serverless`.
   * **Git Fetch Type:** `Latest from Branch`.
   * **Branch:** `main`.
   * **Folder Path:** `serverless/artifacts`.
   * In **Advanced**, you can see **Serverless Config File Path**. Use this setting when your Serverless manifest isn't named `serverless.yml|.yaml|.js|.json`. This option is the same as the `--config` option in `serverless deploy`. See [AWS - deploy](https://www.serverless.com/framework/docs/providers/aws/cli-reference/deploy) from Serverless.
  
You can see the serverless.yaml manifest in Harness.

![](./static/serverless-lambda-cd-quickstart-112.png)

Here's what the serverless.yaml looks like:

```yaml
service: <+service.name>  
frameworkVersion: '2 || 3'  
  
provider:  
  name: aws  
  runtime: nodejs12.x  
functions:  
  hello:  
    handler: handler.hello  
    events:  
      - httpApi:  
          path: /tello  
          method: get    
package:  
  artifact: <+artifact.path>          
plugins:  
  - serverless-deployment-bucket@latest
```

You can see the [Harness expression](../../../platform/12_Variables-and-Expressions/harness-variables.md) `<+artifact.path>` in `artifact: <+artifact.path>`. The expression `<+artifact.path>` tells Harness to get the artifact from **Artifacts** section of the Service. We'll add the artifact next.

The expression `<+service.name>` simply uses the Harness Service name for the deployed service name.

For Docker images, you use the expression `<+artifact.image>`.
