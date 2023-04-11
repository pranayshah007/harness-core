# Add a Serverless AWS Lambda Deploy step

In **Execution**, you add the steps that define how Harness deploys your Serverless Lambda service.

Harness automatically adds two Serverless Lambda steps to **Execution**:
* **Serverless Lambda Deploy:** this step performs the deployment.
* **Serverless Lambda Rollback:** this step performs a rollback in the event of a deployment failure. To see this step, toggle the Execution/Rollback setting.
 
![](./static/serverless-lambda-cd-quickstart-122.png)

1. In **Execution**, click **Serverless Lambda Deploy**.
2. Click the **Advanced** tab and select the Delegate that you installed in **Delegate Selector**.
   
   ![](./static/serverless-lambda-cd-quickstart-123.png)
   
   If you only have one Delegate installed in your Project, then this isn't necessary. But if you have multiple Delegates, you want to make sure the Serverless Lambda Deploy step uses the Delegate where you installed Serverless.
3. Click **Apply Changes**.

Now you're ready to deploy.
