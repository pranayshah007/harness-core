# TAS execution strategies

Now you can select the [deployment strategy](../../cd-deployments-category/deployment-concepts.md) for this stage of the pipeline.

```mdx-code-block
import Tabs2 from '@theme/Tabs';
import TabItem2 from '@theme/TabItem';
```
```mdx-code-block
<Tabs2>
  <TabItem2 value="Basic" label="Basic">
```

The TAS workflow for performing a basic deployment takes your Harness TAS service and deploys it on your TAS infrastructure definition. 

1. In Execution Strategies, select **Basic**, then select **Use Strategy**.
2. The basic execution steps are added. 
   
   ![](./static/basic-deployment.png)

3. Select the **Basic App Setup** step to define **Step Parameters**.
   
   The basic app setup configuration uses your manifest in Harness TAS to set up your application.

    1. **Name** - Edit the deployment step name.
    2. **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    3. **Instance Count** - Select whether to **Read from Manifest** or **Match Running Instances**.  
       The **Match Running Instances** setting can be used after your first deployment to override the instances in your manifest.
    4. **Existing Versions to Keep** - Enter the number of existing versions you want to keep. This is to roll back to a stable version if the deployment fails.
    5. **Additional Routes** - Enter additional routes if you want to add routes other than the ones defined in the manifests.
    6. Select **Apply Changes**.

4. Select the **App Resize** step to define **Step Parameters**.
    1. **Name** - Edit the deployment step name.
    2. **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    3. **Total Instances** - Set the number or percentage of running instances you want to keep.
    4. **Desired Instances - Old Version** - Set the number or percentage of instances for the previous version of the application you want to keep. If this field is left empty, the desired instance count will be the difference between the maximum possible instance count (from the manifest or match running instances count) and the number of new application instances.
    5. Select **Apply Changes**.

5. Add a **Tanzu Command** step to your stage if you want to execute custom Tanzu commands in this step. 
    1. **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    2. **Script** - Select one of the following options.
        - **File Store** - Select this option to choose a script from **Project**, **Organization**, or **Account**.
        - **Inline** - Select this option to enter a script inline.
    3. Select **Apply Changes**.
   
6. Add an **App Rollback** step to your stage if you want to roll back to an older version of the application in case of deployment failure.
7. Select **Save**.

Now the pipeline stage is complete and you can deploy.

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Canary" label="Canary">
```
The TAS canary deployment is a phased approach to deploy application instances gradually, ensuring the stability of a small percentage of instances before rolling out to your desired instance count. With canary deployment, all nodes in a single environment are incrementally updated in small phases. You can add verification steps as needed to proceed to the next phase.

Use this deployment method when you want to verify whether the new version of the application is working correctly in your production environment.

The canary deployment contains **Canary App Setup** and **App Resize** steps. You can add more **App Resize** steps to perform gradual deployment. 

1. In Execution Strategies, select **Canary**, and then click **Use Strategy**.
2. The canary execution steps are added. 
   
   ![](./static/canary-deployment.png)

3. Select the **Canary App Setup** step to define **Step Parameters**.
    1. **Name** - Edit the deployment step name.
    2. **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    3. **Instance Count** - Select whether to **Read from Manifest** or **Match Running Instances**.  
       The **Match Running Instances** setting can be used after your first deployment to override the instances in your manifest.
    4. **Resize Strategy** - Select **Add new instances first, then downsize old instances** or **Downsize old instances first, then add new instances** strategy.
    5.  **Existing Versions to Keep** - Enter the number of existing versions you want to keep. This is to roll back to a stable version if the deployment fails.
    6.  **Additional Routes** - Enter additional routes if you want to add routes other than the ones defined in the manifests.
    7.  Select **Apply Changes**.
4. Select the **App Resize** step to define **Step Parameters**.
    1. **Name** - Edit the deployment step name.
    2. **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    3. **Total Instances** - Set the number or percentage of running instances you want to keep.
    4. **Desired Instances - Old Version** - Set the number or percentage of instances for the previous version of the application you want to keep. If this field is left empty, the desired instance count will be the difference between the maximum possible instance count (from the manifest or match running instances count) and the number of new application instances.
    5. Select **Apply Changes**.
5. Add more **App Resize** steps to perform gradual deployment.
6. Add a **Tanzu Command** step to your stage if you want to execute custom Tanzu commands in this step. 
    1. **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    2. **Script** - Select one of the following options.
        - **File Store** - Select this option to choose a script from **Project**, **Organization**, or **Account**.
        - **Inline** - Select this option to enter a script inline.
    3. Select **Apply Changes**.
7. Add an **App Rollback** step to your stage if you want to rollback to an older version of the application in case of deployment failure.
8. Select **Save**.

Now the pipeline stage is complete and can be deployed.

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Blue Green" label="Blue Green">
```
Harness TAS blue green deployments use the route(s) in the TAS manifest and a temporary route you specify in the deployment configuration.

The blue green deployment deploys the applications using the temporary route first using the **App Setup** configuration. Next, in the **App Resize** configuration, Harness maintains the number of instances at 100% of the `instances` specified in the TAS manifest.

Use this deployment method when you want to perform verification in a full production environment, or when you want zero downtime.

For blue green deployments, by default, the **App Resize** step is 100% because it does not change the number of instances as it did in the canary deployment. However, you can define the percentage in the **App Resize** step. In blue green, you are deploying the new application to the number of instances set in the **App Setup** step and keeping the old application at the same number of instances. You 

Once the deployment is successful, the **Swap Routes** configuration switches the networking routing, directing production traffic (green) to the new application and stage traffic (blue) to the old application.

1. In Execution Strategies, select **Blue Green**, and then click **Use Strategy**.
2. The blue green execution steps are added. 
   
   ![](./static/bg-deployment.png)

3. Select the **BG App Setup** step to define **Step Parameters**.
    1. **Name** - Edit the deployment step name.
    2. **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    3. **Instance Count** - Select whether to **Read from Manifest** or **Match Running Instances**.  
       The **Match Running Instances** setting can be used after your first deployment to override the instances in your manifest.
    4. **Existing Versions to Keep** - Enter the number of existing versions you want to keep. This is to roll back to a stable version if the deployment fails.
    5. **Additional Routes** - Add additional routes in addition to the routes added in the TAS manifest.
   
       Additional routes has two uses in blue green deployments.
       * Select the routes that you want to map to the application in addition to the routes already mapped in the application in the manifest in your Harness service.
       * You can also omit routes in the manifest in your Harness service, and select them in **Additional Routes**. The routes selected in **Additional Routes** will be used as the final (green) routes for the application.
    6. **Temporary Routes** - Add temporary routes in addition to additional routes.
   
       Later, in the **Swap Route** step, Harness will replace these routes with the routes in the TAS manifest in your service.  
       If you do not select a route in Temporary Routes, Harness will create one automatically.
    7. Select **Apply Changes**.
4. Select the **App Resize** step to define **Step Parameters**.
    1. **Name** - Edit the deployment step name.
    2. **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    3. **Total Instances** - Set the number or percentage of running instances you want to keep.
    4. **Desired Instances - Old Version** - Set the number or percentage of instances for the previous version of the application you want to keep. If this field is left empty, the desired instance count will be the difference between the maximum possible instance count (from the manifest or match running instances count) and the number of new application instances.
    5. Select **Apply Changes**.
5. Select the **Swap Routes** step to define **Step Parameters**.
    1. **Name** - Edit the deployment step name.
    2. **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    3. **Downsize Old Application** - Select this option to down size older applications.
    4. Select **Apply Changes**.
6. Add a **Tanzu Command** step to your stage if you want to execute custom Tanzu commands in this step. 
    1. **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    2. **Script** - Select one of the following options.
        - **File Store** - Select this option to choose a script from **Project**, **Organization**, or **Account**.
        - **Inline** - Select this option to enter a script inline.
    3. Select **Apply Changes**.   
7. Add a **Swap Rollback** step to your stage if you want to rollback to an older version of the application in case of deployment failure.

   When **Swap Rollback** is used in a deployment's **Rollback Steps**, the application that was active before the deployment is restored to its original state with the same instances and routes it had before the deployment.

   The failed application  is deleted.
8. Select **Save**.

Now the pipeline stage is complete and can be deployed.

```mdx-code-block
  </TabItem2>
  <TabItem2 value="Rolling" label="Rolling">
```
The TAS rolling deployment deploys all pods or instances in a single environment incrementally added one-by-one with a new service or artifact version.

Use this deployment method when you want to support both new and old deployments. You can also use with load balancing scenarios that require reduced downtime. 

1. In Execution Strategies, select **Rolling**, and then click **Use Strategy**.
2. The rolling deploy step is added. 
   
   ![](./static/rolling-deployment.png)

3. Select the **Rolling Deploy** step to define **Step Parameters**.
    1. **Name** - Edit the deployment step name.
    2.  **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    3.  **Additional Routes** - Add additional routes in addition to the routes added in the TAS manifest.
4. Add a **Tanzu Command** step to your stage if you want to execute custom Tanzu commands in this step. 
    1. **Timeout** - Set how long you want the Harness delegate to wait for the TAS cloud to respond to API requests before timeout.
    2. **Script** - Select one of the following options.
        - **File Store** - Select this option to choose a script from **Project**, **Organization**, or **Account**.
        - **Inline** - Select this option to enter a script inline.
    3. Select **Apply Changes**.   
5. Add a **Rolling Rollback** step to your stage if you want to rollback to an older version of the application in case of deployment failure.
6. Select **Save**.

Now the pipeline stage is complete and can be deployed.

```mdx-code-block
  </TabItem2>    
</Tabs2>
```
