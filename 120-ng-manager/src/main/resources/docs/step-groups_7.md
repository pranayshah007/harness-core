# Option: Step Group Failure Strategy

A step group can have its own Failure Strategy separate from the Failure Strategy for the Stage.

The Failure Strategy can execute the Rollback steps for the step group.

The step group Rollback steps are only run if the Failure Strategy for the step group has **Rollback Step Group** selected.

![](./static/step-groups-01.png)

See [Step Failure Strategy Settings](../../../platform/8_Pipelines/w_pipeline-steps-reference/step-failure-strategy-settings.md).

The Failure Strategy of any step in a step group overrides the Failure Strategy of the step group.

If you do not use a step group Failure Strategy, then the Stage's Failure Strategy is used.
