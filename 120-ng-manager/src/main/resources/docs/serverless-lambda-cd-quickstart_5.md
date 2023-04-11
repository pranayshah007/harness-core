# Create the Deploy stage

Pipelines are collections of stages. For this quickstart, we'll create a new Pipeline and add a single stage.

:::note

**Create a Project for your new CD Pipeline:** if you don't already have a Harness Project, create a Project for your new CD Pipeline. Make sure that you add the **Continuous Delivery** module to the Project. See [Create Organizations and Projects](../../../platform/organizations-and-projects/create-an-organization.md).

:::

1. In your Harness Project, click **Deployments**, and then click **Create a** **Pipeline**.
2. Enter the name **Serverless Quickstart** and click **Start**. Your Pipeline appears.
3. Click **Add Stage** and select **Deploy**.
4. Enter the name **Deploy Service**, make sure **Service** is selected, and then click **Set Up Stage**.

   ![](./static/serverless-lambda-cd-quickstart-110.png)
   
   The new stage settings appear.
1. In **About the** **Service**, click **New Service**.
2. Give the Service the name **quickstart** and click **Save**.

![](./static/serverless-lambda-cd-quickstart-111.png)

:::note

Let's take a moment and review Harness Services and Service Definitions (which are explained below). A Harness Service represents your microservice/app logically.  
You can add the same Service to as many stages as you need. Service Definitions represent your artifacts, manifests, and variables physically. They are the actual files and variable values.  
By separating Services and Service Definitions, you can propagate the same Service across stages while changing the artifacts, manifests, and variables with each stage.

::: 

Once you have created a Service, it's persistent and you can use it throughout the stages of this or any other Pipeline in the Project.
