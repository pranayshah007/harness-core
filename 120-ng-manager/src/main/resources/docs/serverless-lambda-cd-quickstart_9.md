## Install and register the Delegate

1. Open a terminal and navigate to where the Delegate file is located. You'll connect to your cluster using the terminal so you can simply run the YAML file on the cluster.
2. In the same terminal, log into your Kubernetes cluster. On most platforms, you select the cluster, click **Connect**, and copy the access command.
3. Next, install Harness Delegate using the **harness-delegate.yml** file you just downloaded. In the terminal connected to your cluster, run this command:

	```
	kubectl apply -f harness-delegate.yml
	```

	You can find this command in the Delegate wizard:

	![](./static/serverless-lambda-cd-quickstart-116.png)

1. In Harness, click **Verify**. It'll take a few minutes to verify the Delegate. Once it is verified, close the wizard.
2. Back in **Set Up Delegates**, in the list of Delegates, you can see your new Delegate and its tags.
3. Select the **Connect using Delegates with the following Tags** option.
4. Enter the tag of the new Delegate and click **Save and Continue**.
   
   ![](./static/serverless-lambda-cd-quickstart-118.png)

5. In **Connection Test**, you can see that the connection is successful. Click **Finish**.
   
   ![](./static/serverless-lambda-cd-quickstart-119.png)
   
