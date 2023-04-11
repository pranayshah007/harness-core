## Using Harness expressions in your scripts

If you need quotes around the [Harness variable expressions](../../../platform/12_Variables-and-Expressions/harness-variables.md) in your script, use single quotes, like this:

`export EVENT_PAYLOAD='<+trigger.eventPayload>'` 

If you use [Harness variable expressions](../../../platform/12_Variables-and-Expressions/harness-variables.md) in comments in your script, Harness will still try to evaluate and render the variable expressions. Don't use variable expressions that Harness cannot evaluate.
