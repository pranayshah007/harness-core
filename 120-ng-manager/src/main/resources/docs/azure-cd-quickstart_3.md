# Step 1: Create the Deploy Stage

Pipelines are collections of stages. For this quickstart, we'll create a new Pipeline and add a single stage.

:::note

**Create a Project for your new CD Pipeline:** if you don't already have a Harness Project, create a Project for your new CD Pipeline. Ensure that you add the **Continuous Delivery** module to the Project. See [Create Organizations and Projects](../../../platform/organizations-and-projects/create-an-organization.md).In your Harness Project, click **Deployments**, and then click **Create a** **Pipeline**.

:::

1. In your Harness Project, click **Deployments**, and then click **Create a Pipeline**.
2. Enter the name **Azure Quickstart** and click **Start**.

  Your Pipeline appears.

  Do the following:

1. Click **Add Stage** and select **Deploy**.
2. Enter the name **Deploy Service**, make sure **Service** is selected, and then click **Set Up Stage**.
3. The new stage settings appear.
4. In **About the** **Service**, click **New Service**.
5. Give the Service the name **quickstart** and click **Save**.

:::note

Let's take a moment and review Harness Services and Service Definitions (which are explained below). Harness Services represent your microservices/apps logically. You can add the same Service to as many stages as you need. Service Definitions represent your artifacts, manifests, and variables physically. They are the actual files and variable values. 

By separating Services and Service Definitions, you can propagate the same Service across stages while changing the artifacts, manifests, and variables with each stage. Once you have created a Service, it is persistent and can be used throughout the stages of this or any other Pipeline in the Project.

::: 

