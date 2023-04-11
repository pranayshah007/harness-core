# Specify output variables

Shell Script step Output Variables have a maximum size of 512KB.To export variables from the script to other steps in the stage, you use the **Script Output Variables** option.

Let's look at a simple example of a script with the variable **name**:

```bash
name=123
```

The `name` variable cannot be used outside the script unless you use **Script Output Variables**.

You do not need to use `export` for the variables to use them with **Script Output Variables**. You can simply declare them, like `name="123"`. Export is for using the variables in child processes within the script.In **Script Output Variables**, in **Value**, you enter the name of the script variable you want to output (`name`).

1. In **Name**, enter a name to use in other steps that will reference this variable. This is the output variable name that will be used in a Harness expression for referencing the output variable.

![](./static/using-shell-scripts-20.png)

The format to reference the output variable can be one of the following:

* Within the stage:
	+ `<+execution.steps.[step_id].output.outputVariables.[output_variable_name]>`
* Anywhere in the pipeline:
	+ `<+pipeline.stages.[stage_Id].spec.execution.steps.[step_Id].output.outputVariables.[output_variable_name]>`

For example, you could reference the output variable `newname` like this:

```
echo "anywhere in the pipeline: " <+pipeline.stages.Shell_Script_stage.spec.execution.steps.ShellScript_1.output.outputVariables.newname>
echo "anywhere in the stage: " <+execution.steps.ShellScript_1.output.outputVariables.newname>
```

Here's an example showing how the **Script Output Variables** references the exported variable, and how you reference the output variable name to get that value:

<!-- ![](./static/61423f07740b1d9d685c23b8b119ab9f01514473adc50e043c16f699aee3c010.png) -->

<docimage path={require('./static/61423f07740b1d9d685c23b8b119ab9f01514473adc50e043c16f699aee3c010.png')} />


So now the result of `<+execution.steps.ShellScript_1.output.outputVariables.newname>` is `123`.

To find the expression to reference your output variables, find the step in the Pipeline execution, and click its **Output** tab.

![](./static/using-shell-scripts-22.png)
