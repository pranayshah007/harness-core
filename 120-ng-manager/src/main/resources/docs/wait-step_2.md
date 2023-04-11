## Duration

The allowed values for **Duration** are:

* `w` for weeks
* `d` for days
* `h` for hours
* `m` for minutes
* `s` for seconds
* `ms` for milliseconds

The maximum is `53w`.

You can use a Fixed Value, Runtime input, or Expression for **Duration**.

If you use Runtime input, you can enter the wait time when you run the pipeline. You can also set it in a Trigger.

If you use an Expression, ensure that the Expression resolves to one of the allowed time values. 

For information on Fixed Value, Runtime input, and Expression, go to [Fixed Values, Runtime Inputs, and Expressions](../../../platform/20_References/runtime-inputs.md). 
