## Export JSON representation of Terragrunt Plan

:::info

When **All Modules** is selected in **Module Configuration**, the **Export JSON representation of Terragrunt Plan** option is not supported.

:::

Enable this setting to use a JSON representation of the plan implemented in this step.

In subsequent **Execution** steps, such as a Shell Script step, you can reference the plan using this expression format:

```
<+execution.steps.[Terragrunt step Id].plan.jsonFilePath>
```

For example, if you had a Terragrunt Plan step with the Id `Plan_Step`, you could use the expression in a Shell Script step like this:

```
cat "<+execution.steps.Plan_Step.plan.jsonFilePath>"
```
