# Step 5: Deploy and Review

1. Click **Save** and then **Run**.
2. Click **Run Pipeline**. Harness will verify the Pipeline and Connectors and then run the Pipeline.
   There are no artifacts to select because the NGINX artifact is hardcoded in the kustomization.
   You can see the status of the deployment, and pause or abort it.

   ![](./static/kustomize-quickstart-78.png)

3. Toggle **Console View** to watch the deployment with more detailed logging.Click the **Rollout Deployment** step.

   ![](./static/kustomize-quickstart-79.png)

4. Expand **Fetch Files** to see Harness fetch the repo, including the kustomization files.
5. In **Initialize** you can see the manifest rendered using the kustomization and then validated with a `kubectl dry run`.
6.  Expand **Wait for Steady State**. You will the pods reach steady state:

   `Status : "the-deployment" successfully rolled out`

Congratulations! The deployment was successful.

In your Project's Deployments, you can see the deployment listed:

![](./static/kustomize-quickstart-80.png)

If you run into any errors, it is typically because the cluster does meet the requirements from [Before You Begin](kubernetes-cd-quickstart.md#before-you-begin) or the cluster's network setting does not allow the Delegate to connect to Docker Hub.

In this tutorial, you learned how to:

* Install and launch a Harness Kubernetes Delegate in your target cluster.
* Connect Harness to your Kubernetes cluster and Git provider.
* Add your kustomization files to Harness.
* Create an Infrastructure Definition that targets your cluster and namespace.
* Add a Kubernetes rolling update.
* Deploy your Kustomize Pipeline to your target cluster.

Next, try the following quickstarts:

* [Kubernetes deployment tutorial](kubernetes-cd-quickstart)
* [Helm Chart deployment tutorial](helm-cd-quickstart)
