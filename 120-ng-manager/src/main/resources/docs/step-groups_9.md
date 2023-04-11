# Step 3: Add Rollback Steps

A step group can have its own Rollback steps separate from the Rollback steps for the Stage.

The step group Rollback steps are only run if the Failure Strategy for the step group has **Rollback Step Group** selected.

In the step group, click the Execution/Rollback toggle:

![](./static/step-groups-02.png)

In the Rollback view, click **Add Step** to add a rollback step.

For example, you can use the Rolling Rollback step for a [Kubernetes Rollback](../cd-k8s-ref/kubernetes-rollback.md).
