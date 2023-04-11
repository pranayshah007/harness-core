## Connector

We'll create a Harness AWS Connector to connect to your AWS account using the IAM User you configured earlier in [Set up AWS IAM](ecs-deployment-tutorial.md#set-up-aws-iam).

1. In **Connector**, click **Select Connector**.
2. In **Create or Select an Existing Connector**, click **New Connector**.
3. In **AWS Cloud Provider**, in **Name**, enter **ECS Tutorial**, and click **Continue**.
4. In Credentials, enter the following and click **Continue**:
	1. Select **AWS Access Key**.
	2. **Access Key:** enter the IAM User access key.
	3. **Secret Key:** add a new Harness Secret using the access key's secret key. Secrets are always stored in encrypted form and decrypted only when they are needed. To learn more about Harness secrets, go to [Harness Secrets Management Overview](../../../platform/6_Security/1-harness-secret-manager-overview.md).
	4. **Test Region:** US East (Virginia).
5. In **Connect to the provider**, click **Connect through a Harness Delegate**, and then click **Continue**.
6. In **Delegates Setup**, click **Only use Delegates with all of the following tags**, and then enter the tag of the Delegate you set up earlier.
7. Click **Save and Continue**.
8. After the test, click **Finish**.

The Connector is added to your Infrastructure Definition.
