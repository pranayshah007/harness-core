# Marking the Wait step as Success or Fail

When the Wait step is running, it provides **Mark as Success** and **Mark as Failed** options. **Mark as Success** ends the wait period and proceeds with the execution. **Mark as Failed** initiates the Failure Strategy for the step or stage.

![](./static/wait-step-28.png)

For information on Failure Strategies, go to [Define a Failure Strategy on Stages and Steps](../../../platform/8_Pipelines/define-a-failure-strategy-on-stages-and-steps.md).

For example, let's say a Wait step has the Failure Strategy **Manual Intervention**. When the user clicks **Mark as Failed**, they are prompted with the **Manual Intervention** options:

![](./static/wait-step-29.png)

If no Failure Strategy is set at the step or stage level, then clicking **Mark as Failed** simply fails the pipeline execution at the Wait step.
