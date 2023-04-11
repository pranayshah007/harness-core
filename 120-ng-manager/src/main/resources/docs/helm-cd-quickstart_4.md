# Step 1: Create the Deploy Stage

Pipelines are collections of stages. For this quickstart, we'll create a new Pipeline and add a single stage.

:::note

**Create a Project for your new CD Pipeline:** if you don't already have a Harness Project, create a Project for your new CD Pipeline. Ensure that you add the **Continuous Delivery** module to the Project. See [Create Organizations and Projects](../../../platform/organizations-and-projects/create-an-organization.md).

:::

1. In your Harness Project, click **Deployments**, and then click **Create a** **Pipeline**.
   
   ![](./static/helm-cd-quickstart-00.png)

2. Enter the name **Helm Quickstart** and click **Start**.
   Your Pipeline appears.
   
   ![](./static/helm-cd-quickstart-01.png)

3. Click **Add Stage** and select **Deploy**.
4. Enter the name **Deploy Service**, make sure **Service** is selected, and then click **Set Up Stage**.
   The new stage settings appear.
5. In **About the** **Service**, click **New Service**.
   :::note

   Let's take a moment and review Harness Services and Service Definitions (which are explained below). Harness Services represent your microservices/apps logically. You can add the same Service to as many stages are you need. Service Definitions represent your artifacts, manifests, and variables physically. They are the actual files and variable values.  
   
   By separating Services and Service Definitions, you can propagate the same Service across stages while changing the artifacts, manifests, and variables with each stage.

   :::
1. Give the Service the name **nginx** and click **Save**. 
   Once you have created a Service, it is persistent and can be used throughout the stages of this or any other Pipeline in the Project.
2. In **Deployment Type**, click **Kubernetes**. Now your Service looks like this:
   ![](./static/helm-cd-quickstart-02.png)

Next, we'll add the NGINX Helm chart for the deployment.
