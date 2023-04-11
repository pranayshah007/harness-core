# Create the Deploy Stage

Pipelines are collections of stages. In this tutorial, we will create a new Pipeline and add a stage for WinRM deployments of artifacts to a target host.

1. In your Harness project, click **Pipelines**, and then **Create a Pipeline**.
2. Enter a name for the pipeline: **winrm****-tutorial**. Keep the default **Inline (Pipeline is stored in Harness)** for **How do you want to set up your pipeline**.
3. Click **Start** and your pipeline appears.
4. Click **Add Stage**.
5. For **Select Stage Type**, select **Deploy**.
6. For **Stage Name**, enter: **winrm****-stage.**
7. For **Deployment Type**, select **WinRM** and click **Set Up Stage**.

![](./static/win-rm-tutorial-128.png)
