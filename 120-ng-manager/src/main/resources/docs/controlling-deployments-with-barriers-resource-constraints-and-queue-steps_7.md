# Best Practices

Here are some best practices to help you decide which resource control option to use:

* **Barriers:** use when you want to coordinate the timing of different components’ deployments with stages/step groups executed in parallel in the same Pipeline.  
For example, your Pipeline executes Stages A and B in parallel, but you want a database migration in Stage A to complete before a deployment in Stage B. You place a Barrier step after the migration in Stage A and before the deployment step in Stage B.
* **Resource Constraints:** use when you want to prevent simultaneous deployments to the same Service + Infrastructure combination. This feature is enabled by default.
* **Queue steps:** use when you want to control the **sequence** of multiple Pipeline executions. This can be used on different Pipelines or even multiple executions of the same Pipeline.

