# Notes

* When you run steps in parallel you cannot reference the outputs of one step in another step. The output for one step might not be available when another step requests it.
* Delegate Selectors can be configured for each step in the step group. You cannot configure a Delegate Selector at the group level.
* Step groups cannot have nested step groups, but you can put groups of steps next to each other in a step group:![](./static/step-groups-04.png)

The steps **in** each group run in parallel but each group runs serially.

