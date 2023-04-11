## ECS Rolling Rollback

Before you deploy, click **Rollback** and view the **ECS Rolling Rollback** step.

![](./static/ecs-deployment-tutorial-49.png)

When you created the Execution, an ECS Rolling Rollback step is automatically added to the **Rollback** section of Execution.

If Harness needs to rollback and restore the ECS setup to its previous working version, or if you interrupt the deployment to roll it back manually, the first step is to roll back the ECS services.

When a rollback occurs, Harness rolls back all steps in the reverse order they were deployed. This is true for ECS services deployed to EC2 or Fargate clusters.
