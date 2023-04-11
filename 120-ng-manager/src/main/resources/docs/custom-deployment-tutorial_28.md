## Referencing fetched instances using expressions

You can reference each instance fetched by the **Fetched Instances** step using the step's **Output** tab.

![](./static/custom-deployment-tutorial-35.png)

You can expand each instances and copy the expression for the instance metadata you need.

For example, the expression for the instancename of the 4th instance fetched here is (the stage name is `DT_Tutorial`):

```
<+pipeline.stages.DT_Tutorial.spec.execution.steps.fetchInstances.deploymentInfoOutcome.serverInstanceInfoList[3].hostName>
```

To reference the entire instance list, you can use this expression:

```
<+pipeline.stages.DT_Tutorial.spec.execution.steps.fetchInstances.deploymentInfoOutcome.serverInstanceInfoList>
```

