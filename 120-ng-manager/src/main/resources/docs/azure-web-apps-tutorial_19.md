# Blue Green Deployments

A Harness Azure Web App Blue Green deployment swaps traffic from one deployment slot to another.

If you are new to Azure Web App deployment slot swapping, see [What happens during a swap](https://docs.microsoft.com/en-us/azure/app-service/deploy-staging-slots#what-happens-during-a-swap) from Azure.

First, you need to collect the existing Deployment slots from your Azure Web App.

1. In the Azure portal, click your Web App, and then click **Deployment slots**. You can see the Deployment slots for your Web App.
2. Click **Swap**. You can see the Source and Target slots.

![](./static/azure-web-apps-tutorial-166.png)

You'll use these slot names in your Harness steps.

For a Blue Green deployment, Harness adds the following steps.
