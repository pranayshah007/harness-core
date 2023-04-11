## Slot Deployment Step

The Slot Deployment step is where you select the Web App and source deployment slot for the deployment.

1. Open the **Slot Deployment** step.
2. Enter the following settings and click **Submit**.
   * **Name:** enter a name for the step.
   * **Timeout:** enter a minimum of **10m**. The slot deployment relies on Azure and can take time.
   * **Web App Name:** enter the Azure Web App for deployment.
   * **Deployment Slot:** enter the Source slot for the deployment. This slot is where Harness deploys the new Web App version.Make sure the slot you select is running. Harness shows all slots regardless of their status.
