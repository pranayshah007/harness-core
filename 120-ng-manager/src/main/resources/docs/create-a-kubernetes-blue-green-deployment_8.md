# Step 2: Add the Execution Steps

In the stage's **Execution**, click **Add Step**, and select the **Blue Green** strategy.

Harness adds all the steps you need to perform the Blue Green strategy:

![](./static/create-a-kubernetes-blue-green-deployment-30.png)

That's it. Harness will deploy the artifact using the stage service initially, and swap traffic to the primary service.

Let's look at the default settings for the Stage Deployment step.
