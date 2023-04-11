# Step 5: Deploy and Review

1. Click **Save** to save your Pipeline.
2. Click **Run**.
3. Click **Run Pipeline**.
   
   Harness verifies the connections and then runs the Pipeline.

   Toggle **Console View** to watch the deployment with more detailed logging.

   ![](./static/helm-cd-quickstart-11.png)

4. Click the **Rollout Deployment** step and expand **Wait for Steady State**.


You can see `Status : quickstart-nginx deployment "quickstart-nginx" successfully rolled out.`

Congratulations! The deployment was successful.

In your Project's Deployments, you can see the deployment listed:

![](./static/helm-cd-quickstart-12.png)

If you run into any errors, it is typically because the cluster does meet the requirements from [Before You Begin](#before_you_begin) or the cluster's network settings do not allow the Delegate to connect to the chart or image repos.

In this quickstart, you learned how to:

* Install and launch a Harness Kubernetes Delegate in your target cluster.
* Set up a Helm Pipeline.
* Run the new Helm Pipeline and deploy a Docker image to your target cluster.
