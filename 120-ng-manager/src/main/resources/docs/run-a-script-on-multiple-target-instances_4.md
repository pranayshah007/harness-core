# Set looping strategy

The Repeat [Looping Strategy](../../../platform/8_Pipelines/looping-strategies-matrix-repeat-and-parallelism.md) allows you to repeat the step for all target hosts. The strategy will iterate through the list of hosts. The list of hosts is identified with the expression `<+stage.output.hosts>`.

1. In your step, click **Advanced**.
2. Click **Looping Strategy**.
3. Click **Repeat** and enter the following:
  
  ```yaml
  repeat:  
    items: <+stage.output.hosts>
  ```
  Here's an example with a Shell Script step:

  ![](./static/run-a-script-on-multiple-target-instances-01.png)
1. Click **Apply Changes**.
