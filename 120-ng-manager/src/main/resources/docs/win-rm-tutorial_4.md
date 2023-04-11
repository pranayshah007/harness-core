# Create a Harness WinRM Service

Next, you will create a Harness Service that represents your application. Once you have created a Service, it is persistent and can be used throughout the stages of this or any other Pipeline in the Project.

The Harness WinRM Service contains the application package artifact (file or metadata) and the related config files to deploy on the target host.

Let's create the Service for an application artifact.

1. For **Select Service**, click **New Service,** enter a name for the service: **winrm-service****.** You will use this name when selecting this Service in Harness Environments.
2. For **Service Definition**, in **Deployment Type**, select **WinRM** and click **Continue.**
