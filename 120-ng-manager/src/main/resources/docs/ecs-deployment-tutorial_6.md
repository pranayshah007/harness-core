# Create the Harness ECS pipeline

1. In your Harness Project, click **Deployments**.
2. Click **Pipelines**, and then click **Create a Pipeline**.
3. Enter the name **ECS Tutorial** for the Pipeline and then click **Start**.
4. Click **Add Stage**.
5. Click **Deploy**.
6. In **Stage Name**, enter **ECS Tutorial**.
7. In **Deployment Type**, click **Amazon ECS**, and then click **Set Up Stage**.

Here's a quick summary:

![](./static/ecs-deployment-tutorial-39.png)

The new stage is created. Next, we'll add a Harness Service to represent the app you're deploying, and configure the Service with the ECS Task Definition, Service Definition, and artifact for deployment.
