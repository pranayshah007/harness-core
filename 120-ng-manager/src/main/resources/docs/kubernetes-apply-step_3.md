# Name

The name for the Apply step.

The step Id is automatically generated from the name and used to reference the step using its Fully Qualified Name (FQN), like `<+execution.steps.[step Id].name>`.

For example, if the step Id is **Apply**, the FQN for the step settings are:

* `<+execution.steps.Apply.name>`
* `<+execution.steps.Apply.spec.filePaths>`
* `<+execution.steps.Apply.spec.skipDryRun>`
* `<+execution.steps.Apply.spec.skipSteadyStateCheck>`
* `<+execution.steps.Apply.timeout>`
