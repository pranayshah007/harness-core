## Define the AWS deployment target

1. In **Infrastructure**, we'll add an AWS Connector to connect Harness with your Lambda service.
2. In **Infrastructure Details**, in **Specify your environment**, click **New Environment**. Just like with a Service, you can create a new Environment or select an existing one. We'll create a new one.
3. In **New Environment**, enter a name, select **Pre-Production**, and click **Save**. The new Environment appears.
4. In **Infrastructure Definition**, click **AWS**.
5. In **Amazon Web Services Details**, click in **Connector**.
6. In **Create or Select an Existing Connector**, click **New Connector**.
7. Enter the following and click **Save and Continue**.
	* **Name:** `AWS Serverless`.
	* **Credentials:** `AWS Access Key`. Enter the AWS access key for the AWS User you created with the required policies in [Before You Begin](#before-you-begin).
	* Enter the secret key as a [Harness Text Secret](../../../platform/6_Security/2-add-use-text-secrets.md). The Harness Delegate uses these credentials to authenticate Harness with AWS at deployment runtime.
	* **Delegates Setup:** `Only use Delegates with all of the following tags`.
	* Select the Delegate you added earlier in this quickstart.
1. The **Connection Test** verifies the connection. Click **Finish**.
2. Back in **Amazon Web Services Details**, in **Region**, enter the region for your AWS Lambda service, such as **us-east-1**.
3. In **Stage**, enter the name of the stage in your service that you want to deploy to, such as **dev**. This is the same as the `--stage` option in the `serverless deploy` command.
   
   ![](./static/serverless-lambda-cd-quickstart-121.png)
   
   When you run your deployment, you'll see these settings used in the logs. For example: `serverless deploy list --stage dev --region us-east-1`.
1. Click **Continue**. The **Execution** steps appear.
