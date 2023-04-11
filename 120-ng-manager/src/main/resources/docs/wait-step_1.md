# Add the Wait step

The Wait step is available in Approval, Custom, CD, and Feature Flag stages. You can add the Wait step anywhere in those stages.

1. In your stage **Execution** (or **Rollout Strategy** in Feature Flags), click **Add Step**, and then click **Wait**.
2. Enter a name for the step.
3. In **Duration**, enter how long the Wait step should run. Once the timeout occurs, the pipeline execution proceeds.  
When the Wait step runs, the duration is displayed in its **Details**.  
![](./static/wait-step-27.png)
4. Click **Apply Changes**.
