# Output variables

Output Variables have a maximum size of 512KB.To export variables from the script to other steps in the stage, you use the **Script Output Variables** option.

Let's look at a simple example of a script with the variable **name**:


```
name=123
```

The `name` variable cannot be used outside the script unless you use **Script Output Variables**.

You do not need to use `export` for the variables to use them with **Script Output Variables**. You can simply declare them, like `name="123"`. Export is for using the variables in child processes within the script.In **Script Output Variables**, in **Value**, you enter the name of the script variable you want to output (`name`).

In **Name**, enter a name to use in other steps that will reference this variable. This is the output variable name that will be used in a Harness expression for referencing the output variable.

The format to reference the output variable can be one of the following:

* Within the stage:
	+ Referencing the step output:
		- `<+steps.[step_id].output.outputVariables.[output_variable_name]>`.
	+ Referencing the step output execution:
		- `<+execution.steps.[step_id].output.outputVariables.[output_variable_name]>`
* Anywhere in the Pipeline:
	+ `<+pipeline.stages.[stage_Id].spec.execution.steps.[step_id].output.outputVariables.[output_variable_name]>`

For example, it could be `<+steps.Shell_Script.output.outputVariables.newname>`.
