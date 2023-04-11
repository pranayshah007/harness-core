# Step 5: Deploy and Review

1. Click **Save** **> Save Pipeline** and then **Run**.
   Now you can select the specific artifact to deploy.
2. In **Primary Artifact**, select **stable**. This is the same as using `docker pull nginx:stable`.
3. Click **Run Pipeline**. Harness will verify the Pipeline and then run it.
   You can see the status of the deployment, and pause or abort it.

   ![](./static/kubernetes-cd-quickstart-93.png)

4. Toggle **Console View** to watch the deployment with more detailed logging.Click the **Rollout Deployment** step and expand **Wait for Steady State**.

   You can see `deployment "my-nginx" successfully rolled out`.

   ![](./static/kubernetes-cd-quickstart-94.png)

Congratulations! The deployment was successful.

In your Project's Deployments, you can see the deployment listed:

![](./static/kubernetes-cd-quickstart-95.png)

If you run into any errors, it is typically because the cluster does meet the requirements from [Before You Begin](#before_you_begin) or the cluster's network setting does not allow the Delegate to connect to Docker Hub.In this tutorial, you learned how to:

* Install and launch a Harness Kubernetes Delegate in your target cluster.
* Connect Harness to your Kubernetes cluster and an artifact server.
* Add your manifests to Harness.
* Create an Infrastructure Definition that targets your cluster and namespace.
* Add a Kubernetes rolling update.
* Deploy your Kubernetes Pipeline to your target cluster.

Next, try Harness [Continuous Integration](docs/continuous-integration) to build a codebase, upload it to a repo, and run unit and integrations tests: [CI pipeline tutorials](../../../continuous-integration/ci-quickstarts/ci-pipeline-quickstart.md).
