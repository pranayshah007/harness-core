# Use a Basic strategy

You are now taken to **Execution Strategies** where you will use a deployment strategy and run your pipeline.

1. In **Execution Strategies**, select **Basic**. We'll use Basic because we're using one host. If we did Rolling or Canary, we would need multiple hosts.
2. For **Package type**, select **WAR**.
3. Click **Use Strategy**. Harness adds the **Deploy** step for execution.
4. Click the **Deploy** step. Here is where are add the scripts for your package. We'll use the defaults for this tutorial. So we'll simply be copying the artifact to the target host.
   
   ![](./static/ssh-ng-189.png)

5. In **Command Scripts**, edit **Copy Config**.
6. In **Edit Command**, for **Select file type to copy**, click **Artifact**.
   
   ![](./static/ssh-ng-190.png)

7. Click **Save**.
8. Review Looping Strategy: the Looping Strategy is how the deployment will repeat deployments for multiple hosts and for different deployment strategies (Basic, Rolling, Canary).
	1. Click **Advanced**.
	2. Click **Looping Strategy**. You can see that the step will be repeated for all hosts using the `<+stage.output.hosts>` expression.  
	In this tutorial, we're just using one host, but if you had two hosts the step would be repeated for each host.
  
  ![](./static/ssh-ng-191.png)

9.  Click **Apply Changes**.
10. When you're done, click **Save** to publish the Pipeline.
