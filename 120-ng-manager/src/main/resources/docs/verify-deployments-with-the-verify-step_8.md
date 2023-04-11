# Step 4: Create a Monitored Service

In **Monitored Service**, click **Click to autocreate a monitored service**.

:::note

The option to auto-create a monitored service is not available if you have configured either a service, an environment, or both as runtime values. When you run the pipeline, Harness concatenates the service and environment values you enter in the runtime inputs screen and generates a monitored service name. If a monitored service with the same name exists, Harness assigns it to the pipeline. If no monitored service that matches the generated monitored service name exists, Harness skips the verification step. 

For example, suppose you enter the service as `todolist` and the environment as `dev`. In that case, Harness generates the monitored service name `todolist_dev`, checks whether a monitored service with the name `todolist_dev` is available, and assigns it to the pipeline. If no monitored service is available with the name `todolist_dev`, Harness skips the verification step.

:::

You can also create a monitored service using a monitored service template. To use a template to create a monitored service:

1. In Monitored Service, click **Use Template**.  
The Monitored Service templates slider appears on the right. It displays all the available monitored service templates.
2. Select the appropriate monitored service template.  
The template details appear on the right. The fields that are configured as **Runtime Input** while creating the template are displayed here.

![](./static/verify-deployments-with-the-verify-step-23.png)

3. Click **Use Template** to close the Monitored Service Templates slider.  
The fields that are configured as Runtime Input while creating the template are displayed under **Template Inputs**.You can modify the template by clicking the Open in Template Studio button on the top. This opens the template in a separate tab where you can make changes. After making the changes, you can save the changes to the current template, save as a new version, or save as a new template.
4. Enter appropriate values.  
For example, if the health source has been configured as a runtime input while creating the template, the health source related fields are displayed.
