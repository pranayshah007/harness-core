## How do I model my CD practices in Harness?

Continuous Delivery is modeled using Pipelines and Stages.

In each Stage, you define **what** you want to deploy using Services, **where** you want to deploy it using Environments, and **how** you want to deploy it using Execution steps.

For example, a **Service** uses your Kubernetes manifests and Docker image, an **Environment** connects to your dev cluster, and Harness automatically generates an **Execution** using a Rolling Deployment step.

![](./static/cd-pipeline-modeling-overview-02.png)

The image above shows you the order for modeling a CD stage:

1. Create a pipeline.
2. Add a CD stage.
3. Define a service.
4. Target an environment and infrastructure.
5. Select execution steps.

You can model visually, using code, or via the REST API.
