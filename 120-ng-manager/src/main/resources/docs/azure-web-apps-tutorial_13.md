# Canary Deployments

A Harness Azure Web App Canary deployment shifts traffic from one deployment slot to another incrementally.

First, you select the deployment slot where the deployment will be done. Next, you add Traffic Shift steps to incrementally shift traffic from the production slot to the deployment slot.

Finally, you swap the deployment slot with the target slot. Azure swaps the Virtual IP addresses and URLs of the deployment and target slots.

First, you need to collect the existing Deployment slots from your Azure Web App.

1. In the Azure portal, click your Web App, and then click **Deployment slots**. You can see the Deployment slots for your Web App.
2. Click **Swap**. You can see the Source and Target slots.

![](./static/azure-web-apps-tutorial-164.png)

You'll use these slot names in your Harness steps.

For a Canary deployment, Harness adds the following steps.
