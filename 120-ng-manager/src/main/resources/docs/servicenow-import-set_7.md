# Review transform map outcomes

Within ServiceNow, **Transform Table Maps** determine how data is written from the staging table to existing target tables. Map rows determine whether the transformation creates or updates a ticket, and determines the ticket type.

![Transform Table Maps](static/servicenow-transform-table.png)

You can view the outcomes of transform maps after the pipeline is run.

1. Click the **ServiceNow Import Set** in the executed pipeline.
2. Click the **Output** tab.

You can see the results in the **Transform Map Outcomes** table for each record.

![Transform Map Outcomes](static/servicenow-outputs.png)

To reference these results as expressions in other pipeline steps, click the copy button next to the **Output Name**. The result is expressions like these:

```
<+pipeline.stages.stage1.spec.execution.steps.step1.output.transformMapOutcomes[0].transformMap>
<+pipeline.stages.stage1.spec.execution.steps.step1.output.transformMapOutcomes[0].status>
<+pipeline.stages.stage1.spec.execution.steps.step1.output.transformMapOutcomes[1].transformMap>
<+pipeline.stages.stage1.spec.execution.steps.step1.output.transformMapOutcomes[1].status>
```

You can echo them in a subsequent step, such as a [Shell Script step](../../cd-execution/cd-general-steps/using-shell-scripts.md).
