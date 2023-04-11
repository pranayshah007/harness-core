## App Services Configuration

In Azure App Service, App settings are variables passed as environment variables to the application code.

In Harness, you have the option of setting **Application settings** and **Connection strings** in the Harness Service under **App Services Configuration**.

1. Click **Add Application Settings** and **Add Connection Strings** to add your settings.

You can use remote Git repos that contain your settings files, or you can click **Harness** to use the [Harness File Store](../../cd-services/cd-services-general/add-inline-manifests-using-file-store.md) to add them to your Harness Project. For example:

![](./static/azure-web-apps-tutorial-157.png)

See [Configure an App Service app in the Azure portal](https://docs.microsoft.com/en-us/azure/app-service/configure-common) from Azure.These are the same setting you would normally set for your App using the Azure CLI:

```
az webapp config appsettings set --resource-group <group-name> --name <app-name> --settings DB_HOST="myownserver.mysql.database.azure.com"
```

Or via the portal:

![](./static/azure-web-apps-tutorial-158.png)
