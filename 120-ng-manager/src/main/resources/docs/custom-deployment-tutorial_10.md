# Create the Pipeline

Now we'll create a Harness Pipeline that will use the Deployment Template.

1. Click **Pipelines**.
2. Click **Create a Pipeline**.
3. In **Create new Pipeline**, enter the name **DT Tutorial**, and click **Start**.
4. Click **Add Stage**.
5. Click **Deploy**, and enter the name **DT Tutorial**.
6. In **Deployment** **Templates**, click the Deployment Template you created, **DT**, and then click **Use Template**.  
  ![](./static/custom-deployment-tutorial-18.png)
7. Click **Set Up Stage**.

The stage is created and you are on the **Service** tab.

Next we'll define the Harness Service using the Deployment Template you created and a Docker artifact for deployment.

If you are new to Harness, learn about the basics in [CD overview and key concepts](../cd-concepts/cd-pipeline-basics.md) and [CD Pipeline modeling overview](../cd-concepts/cd-pipeline-modeling-overview.md).
