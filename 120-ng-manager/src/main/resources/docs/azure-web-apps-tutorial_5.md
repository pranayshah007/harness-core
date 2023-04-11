## Startup Command

You can use **Startup Command** to add a startup script for your app.

For details on Web App startup commands, go to [What are the expected values for the Startup File section when I configure the runtime stack?](https://docs.microsoft.com/en-us/azure/app-service/faq-app-service-linux#what-are-the-expected-values-for-the-startup-file-section-when-i-configure-the-runtime-stack-) and [Azure App Service on Linux FAQ](https://docs.microsoft.com/en-us/troubleshoot/azure/app-service/faqs-app-service-linux#built-in-images) from Azure.

1. Click **Add Startup Command**.

You can use remote Git repos that contain your start command file, or you can click **Harness** to use the [Harness File Store](../../cd-services/cd-services-general/add-inline-manifests-using-file-store.md) to add them to your Harness Project.

Here's an example of the startup command to start your JAR app for a Java SE stack:


```
java -jar /home/site/wwwroot/app.jar --server.port=80
```