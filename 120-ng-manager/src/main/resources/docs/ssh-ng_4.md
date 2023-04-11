# Create the Deploy Stage

Pipelines are collections of stages. In this tutorial, we will create a new Pipeline and add a stage for Secure Shell deployments of artifacts to a target host.

1. In your Harness project, click **Pipelines**, and then **Create a Pipeline**.
2. Enter a name for the pipeline: **ssh-tutorial-pipeline**. Keep the default **Inline (Pipeline is stored in Harness)** for **How do you want to set up your pipeline**.
3. Click **Start** and your pipeline appears.
4. Click **Add Stage**.
5. For **Select Stage Type**, select **Deploy**.
6. For **Stage Name**, enter: **ssh-tutorial-stage-one**.
7. Click Secure Shell and then click **Set Up Stage**.

![](./static/ssh-ng-169.png)

Next, you will create a Harness Service that represents your application. Once you have created a Service, it is persistent and can be used throughout the stages of this or any other Pipeline in the Project.
