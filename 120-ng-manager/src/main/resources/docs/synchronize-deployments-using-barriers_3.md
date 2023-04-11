# Step 2: Configure Barrier

To apply a barrier, do the following:

1. In your stage, in **Execution**, click **Add Step**, and then click **Barrier**.
   
   ![](./static/synchronize-deployments-using-barriers-33.png)
2. Enter a name for the step.
3. In **Timeout**, enter the timeout period, in milliseconds. For example, 600000 milliseconds is 10 minutes. The timeout period determines how long each stage with a barrier must wait for the other stage to reach their barrier point. When the timeouts expire, it is considered a deployment failure.
4. Barrier timeouts are not hard timeouts. A barrier can fail anytime between timeout + 1min.In **Barrier Reference**, select the name of an existing barrier.
   
   ![](./static/synchronize-deployments-using-barriers-34.png)
5. Click **Apply Changes**.

You cannot use a Harness variable expression in **Barrier Reference**.Now you can add another Barrier step using the same name to another stage at the point where you want to synchronize execution.
