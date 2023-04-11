# Step 3: Select a Continuous Verification Type

In **Continuous Verification Type**, select a type that matches your deployment strategy.

**Rolling**, **Canary**, and **Blue Green** simply refer to the strategy in the stage.

For example, Canary deployment strategies use the **Canary** Continuous Verification type, and so on.

![](./static/verify-deployments-with-the-verify-step-22.png)

For **Load Test**, Harness compares the current Verify step execution with the last successful run of the Verify step. The first time you run the Pipeline there's no comparison. The comparisons are made is successive deployments.

Typically, **Load Test** is used in lower environments like QA where there is no continuous load and the deployments are usually verified by generating load via scripts, etc.
