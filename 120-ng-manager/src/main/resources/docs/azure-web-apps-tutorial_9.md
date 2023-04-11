# Define Web App Infrastructure Details

The target Azure environment for your Harness Web App deployment is defined in a Harness Environment's **Infrastructure**. You will provide the Web App name later in your stage's **Execution**.

You simply provide select or create an Azure Cloud Provider Connector and then select the Web App's Subscription Id and Resource Group.

1. In your stage **Infrastructure**, in Environment, select an Environment or click **New Environment** and add a new Production or Pre-production Environment.
2. In **Specify Infrastructure**, select an Infrastructure or click **New Infrastructure** and follow these steps.
3. Enter a name for the new Infrastructure.
4. In **Web App Infrastructure Details**, you will select or create an Azure Cloud Provider Connector and then select the Web App's Subscription Id and Resource Group.
5. In **Connector**, select or create an Azure Cloud Provider Connector that connects to your Application and Tenant Ids.

  For steps on setting up a new Azure Cloud Provider Connector, go to [Add a Microsoft Azure Cloud Connector](../../../platform/7_Connectors/add-a-microsoft-azure-connector.md).

  Azure Web App Roles and PermissionsIf you use Microsoft Azure Cloud Connector and Service Principal or Managed Identity credentials, you can use a custom role or the **Contributor** role. The **Contributor** role is the minimum requirement.

  Below are the Azure RBAC permissions used for System Assigned Managed Identity permissions to perform Azure Web App deployments for container and non-container artifacts.

  ```
  [  
                      "microsoft.web/sites/slots/deployments/read",  
                      "Microsoft.Web/sites/Read",  
                      "Microsoft.Web/sites/config/Read",  
                      "Microsoft.Web/sites/slots/config/Read",  
                      "microsoft.web/sites/slots/config/appsettings/read",  
                      "Microsoft.Web/sites/slots/*/Read",  
                      "Microsoft.Web/sites/slots/config/list/Action",  
                      "Microsoft.Web/sites/slots/stop/Action",  
                      "Microsoft.Web/sites/slots/start/Action",  
                      "Microsoft.Web/sites/slots/config/Write",  
                      "Microsoft.Web/sites/slots/Write",  
                      "microsoft.web/sites/slots/containerlogs/action",  
                      "Microsoft.Web/sites/config/Write",  
                      "Microsoft.Web/sites/slots/slotsswap/Action",  
                      "Microsoft.Web/sites/config/list/Action",  
                      "Microsoft.Web/sites/start/Action",  
                      "Microsoft.Web/sites/stop/Action",  
                      "Microsoft.Web/sites/Write",  
                      "microsoft.web/sites/containerlogs/action",  
                      "Microsoft.Web/sites/publish/Action",  
                      "Microsoft.Web/sites/slots/publish/Action"  
  ]
  ```
1. In **Subscription Id**, select the Azure subscription used by your Web App.
  
  The subscription is located in the Web App **Overview** section of the Azure portal.
  
  ![](./static/azure-web-apps-tutorial-160.png)

1. In **Resource Group**, select the resource group used by your Web App.
  
  The resource group is located in the Web App **Overview** section of the Azure portal.
  
  ![](./static/azure-web-apps-tutorial-161.png)

Within the same resource group, you can't mix Windows and Linux apps in the same region. See [Limitations](https://docs.microsoft.com/en-us/azure/app-service/overview#limitations) from Azure.

When you're done, Infrastructure will look something like this:

![](./static/azure-web-apps-tutorial-162.png)

Now that you have the **Service** and **Infrastructure** defined, you can select a deployment strategy and configure its step in your stage **Execution**.

1. Click **Continue** and select a [deployment strategy](../../cd-deployments-category/deployment-concepts.md). 

The steps for the strategy are added automatically.
