# Before You Begin

Harness will deploy a new artifact version to your existing Azure Web App. You will need the following:

* **An existing Azure Web App using a Docker image or non-containerized artifact:** you can create one in minutes in Azure.
	+ Web App must have **Always on** setting set to **On**.
	  ![](./static/azure-web-apps-tutorial-155.png)
	+ **One or more running slots:** the slots created for your existing Azure Web App. If you are doing a Blue Green deployment, you will need two slots.
* **A Docker image or non-containerized artifact:** this is the same image or artifact you used when you created the Azure Web App.
* **Azure account connection information:** The required permissions are described below.
* **App Service Plan:** the name of the Azure App Service configured for your existing Web App.
