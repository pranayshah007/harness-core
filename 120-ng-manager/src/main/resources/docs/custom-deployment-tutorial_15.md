# Define the Execution

The **Fetch Instances** step is added automatically.

![](./static/custom-deployment-tutorial-25.png)

When the pipeline runs, that step will run your Deployment Template's script and traverse the JSON instances array returned to identify the target instances for deployment.

Execution has Fetch Instances but it still needs a step for deployment.

1. Click the **+** sign after **Fetch Instances** and select **Deployment Template Steps**.
  
  We are adding the deployment step after **Fetch Instances** because we know there are existing pods to be fetched in the `default` namespace.
  
  The **deploy** step you added to the Deployment Template is displayed.
  
  ![](./static/custom-deployment-tutorial-26.png)1. Click the **deploy** step and click **Use Template**.
2. Enter the name **DT Tutorial** for the step and click **Apply Changes**.

Let's add one more step to describe the deployment and see how it worked on each fetched instance.

1. After the **DT Tutorial** step, click **Add Step**, and select **Shell Script**.
2. Name the step **kubectl describe deployments**.
3. In **Script**, enter the following:
   
```bash
kubectl describe deployment nginx-deployment
```
![](./static/custom-deployment-tutorial-27.png)

Next, we need this script to loop through all the fetched instances. We do that by using a [Looping Strategy](../../../platform/8_Pipelines/looping-strategies-matrix-repeat-and-parallelism.md) in the step's **Advanced** section.

1. Click **Advanced**.
2. Click **Looping Strategy**.
3. Click Repeat, and enter the following script:

  ```yaml
  repeat:  
    items: <+stage.output.hosts>
  ```

  ![](./static/custom-deployment-tutorial-28.png)

  The `<+stage.output.hosts>` expression references all of the hosts/pods/instances returned by your script.
1. Click **Apply Changes**.

Execution is complete. Now we'll select the Delegate you set up as the Delegate to use for the entire stage.
