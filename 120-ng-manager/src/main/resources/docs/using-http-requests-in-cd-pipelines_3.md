# Name and Timeout

Enter a name for the step. Harness automatically creates an **Id**. You'll use this Id to reference this step's settings in other steps.

For example, if the stage name is **dev** and the step Id is **HTTP** and you want to reference the URL entered in its **URL** setting, you'd use:

`<+pipeline.stages.dev.spec.execution.steps.HTTP.spec.url>`

In **Timeout**, enter a timeout for this step.You can use:

* `w` for weeks
* `d` for days
* `h` for hours
* `m` for minutes
* `s` for seconds
* `ms` for milliseconds

The maximum is `53w`.Timeouts are set at the Pipeline level also.
