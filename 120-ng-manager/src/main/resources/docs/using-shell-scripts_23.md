## Output Variables as Secrets

You can select String or Secret for your output variable.

![](./static/using-shell-scripts-23.png)

When you select Secret and reference the output variable later in the Pipeline, Harness will automatically sanitize the resolved secret value in the logs.

Let's look at an example. First, you add the output variable as a Secret:

![](./static/using-shell-scripts-24.png)

Next, you reference that output variable as a secret, like this:


```
echo "my secret: " <+execution.steps.CreateScript.output.outputVariables.myvar>
```
When you run the Pipeline, the resolved output variable expression is sanitized:

![](./static/using-shell-scripts-25.png)
