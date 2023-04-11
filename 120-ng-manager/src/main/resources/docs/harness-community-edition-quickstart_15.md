# Step 3: Deploy

1. In **Run Pipeline**, click **Run Pipeline**.

  You can see the Pipeline fetch the manifest and deploy the NGINX artifact to your local cluster.

  ![](./static/harness-community-edition-quickstart-138.png)

2. Click **Console View** to see more of the logs and watch the deployment in realtime.

3. In the **Rollout Deployment** step, in Wait for Steady State, you'll see that NGINX was deployed successfully:

  ```
  Status : my-nginx   deployment "my-nginx" successfully rolled out
  ```

Congratulations! You have a successful local deployment using Harness CD Community Edition!

Now you can use Harness to deploy remotely. Simply follow the same steps but use a remote target cluster.
