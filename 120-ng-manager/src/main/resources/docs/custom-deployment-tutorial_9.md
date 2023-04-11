# Add Execution steps

1. Now that the Deployment Template **Infrastructure** is complete, click **Continue** to view **Exectuion**.

  You can create or select step templates in Execution.

  You don't have to use any of these steps in your stage. Execution is simply a way of associating steps with Deployment Templates.

  We'll create a Shell Script step template to deploy our Docker image artifact to the instances we fetch using the script we added in **Fetch Instance Script**.

1. In **Deployment Steps**, click **Add Step**, and click **Create and Use Template**.
2. In **Step Library**, click **Shell Script**.
3. In **Name**, enter **deploy**.
4. In **Script**, enter the following:
  
  ```bash
  /opt/harness-delegate/client-tools/kubectl/v1.19.2/kubectl apply -f deployment.yaml
  ```
1. Click **Save**.
2. In **Save as new Template**, in **Name**, enter **deploy**.
3. In **Version Label**, enter **v1**.
  ![](./static/custom-deployment-tutorial-16.png)
4. Click **Save**. The step template is added to the Deployment Template.
  ![](./static/custom-deployment-tutorial-17.png)
1. Click **Save** to save the Deployment Template. If you haven't already, name the Deployment Template **DT**.
