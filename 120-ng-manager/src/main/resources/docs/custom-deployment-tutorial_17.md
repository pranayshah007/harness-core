# Run and verify the pipeline

1. Click **Save**. The Pipeline is ready to run.
2. Click **Run**, and then click **Run Pipeline**.

The Pipeline runs and you can see the Kubernetes Deployment object created and the target instances fetched.

Click the **Fetch Instances** step, and then click **Output** to see the instances output from your script:

![](./static/custom-deployment-tutorial-30.png)

Lastly, look at the **kubectl describe deployments** step to see the deployment on each pod:

![](./static/custom-deployment-tutorial-31.png)

Congratulations!
