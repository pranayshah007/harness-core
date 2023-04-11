### Target host instance variable expressions

You can use `<+instance...>` expressions to reference host(s) properties.

The `<+instance...>` expressions refer to the **Instance Attributes** in the Deployment Template:

![](./static/custom-deployment-tutorial-33.png)

The following expressions refer to instance(s) collected by the mandatory **instancename** field:

* [<+instance.hostName>](../../../platform/12_Variables-and-Expressions/harness-variables.md#instance-host-name)
* [<+instance.host.instanceName>](../../../platform/12_Variables-and-Expressions/harness-variables.md#instance-host-instance-name)
* [<+instance.name>](../../../platform/12_Variables-and-Expressions/harness-variables.md#instance-name)

The expression `<+instance.host.properties.[property name]>` can used to reference the other properties you added to **Instance Attributes**.

For example, in the example above you can see the `artifact` field name mapped to the `artifactBuildNo` property.

To reference `artifact` you would use `<+instance.host.properties.artifact>`.

`instance.name` has the same value as `instance.hostName`. Both are available for backward compatibility.

To use these expressions, you need to enable the Repeat Looping Strategy and use the expression `<+stage.output.hosts>` on the step that follows **Fetch Instances**.

For example, here is a Shell Script step that outputs these expressions:

![](./static/custom-deployment-tutorial-34.png)

1. In the step, in **Advanced**, click **Looping Strategy**.
2. Select **Repeat**.
3. In **Repeat**, use the Repeat [Looping Strategy](../../../platform/8_Pipelines/looping-strategies-matrix-repeat-and-parallelism.md) and identify all the hosts for the stage as the target:

```yaml
repeat:  
  items: <+stage.output.hosts>
```
Now when this step runs, it will run on every host returned by **Fetch Instances**.
