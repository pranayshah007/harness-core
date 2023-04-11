# Export Human Readable representation of Terraform Plan

Enable this option to view the Terraform plan file path and contents as human-readable JSON is subsequent steps, such as a [Shell Script step](../../cd-execution/cd-general-steps/using-shell-scripts).

Once you enable this option and run a CD stage with the Terraform Plan step, you can click in the Terraform Plan step's **Output** tab and copy the **Output Value** for the **humanReadableFilePath** output.

![alt](static/human-readable.png)

The format for the expression is:
- **humanReadableFilePath**:
  - `<+terraformPlanHumanReadable."pipeline.stages.[stage Id].spec.execution.steps.[step Id].tf_planHumanReadable">`

For example, if the Terraform Plan stage and step Ids are `tf` then you would get the following expressions:

- **humanReadableFilePath**:
  - `<+terraformPlanHumanReadable."pipeline.stages.tf.spec.execution.steps.tf.tf_planHumanReadable">`

Next, you can enter those expressions in a subsequent [Shell Script step](../../cd-execution/cd-general-steps/using-shell-scripts) step and Harness will resolve them to the human-readable paths and JSON.

For example, here is a script using the variables:

```bash
echo "<+terraformPlanHumanReadable."pipeline.stages.tf.spec.execution.steps.tf.tf_planHumanReadable">"
echo "Plan is"
cat "<+terraformPlanHumanReadable."pipeline.stages.tf.spec.execution.steps.tf.tf_planHumanReadable">"
```
Note that if there are no changes in the plan, the standard Terraform message is shown in the Shell Step's logs:

```
No changes. Your infrastructure matches the configuration.
Terraform has compared your real infrastructure against your configuration
and found no differences, so no changes are needed.
```
