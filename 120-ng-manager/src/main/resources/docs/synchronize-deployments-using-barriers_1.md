# Barriers and Synchronization

When deploying interdependent services, such as microservices or a large and complicated application, there might be a need to coordinate the timing of the different components' deployments. A common example is the need to verify a group of services only after *all the services* are deployed successfully.

Harness address this scenario using Barriers. Barriers allow you to synchronize different stages and step groups in your Pipeline, and control the flow of your deployment systematically.

Barriers have an effect only when two or more stages/step groups use the same barrier name (**Barrier Reference** setting in the Barrier step), and are executed in parallel in a Pipeline. When executed in parallel, both stages/step groups will cross the barrier at the same time.

If a stage/step group fails before reaching its barrier point, the stage/step group signals the other stages/step groups that have the same barrier, and the other stages/step groups will react as if they failed as well. At that point, each stage/step group will act according to its [Define a Failure Strategy on Stages and Steps](../../platform/8_Pipelines/define-a-failure-strategy-on-stages-and-steps.md).

Here's a visualization of three stages run in parallel using Barriers. Stages A and B will wait for each other to reach Barrier X and Stages B and C will wait for each other to reach Barrier Y.

![](./static/synchronize-deployments-using-barriers-29.png)
