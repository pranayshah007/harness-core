## Add the manifest

1. In **Manifests**, select **Add Manifest**.  
   Harness uses **TAS Manifest**, **Vars**, and **AutoScaler** manifest types for defining TAS applications, instances, and routes.  
   You can use one TAS manifest and one autoscaler manifest only. You can use unlimited vars file manifests. 

   ![](./static/tas-manifest-type.png)
 
2. Select **TAS Manifest** and select **Continue**.
3. In **Specify TAS Manifest Store**, select **Harness** and select **Continue**.
4. In **Manifest Details**, enter a manifest name. For example, `nginx`.
5. Select **File/Folder Path**. 
6. In **Create or Select an Existing Config file**, select **Project**. This is where we will create the manifest.
    1. Select **New**, select **New Folder**, enter a folder name, and then select **Create**.
    2. Select the new folder, select **New**, select **New File**, and then enter a file name. For example, enter `manifest`.
    3. Enter the following in the `manifest` file, and then click **Save**.
       
       ```
       applications:
       - name: ((NAME))
       health-check-type: process
       timeout: 5
       instances: ((INSTANCE))
       memory: 750M
       routes:
         - route: ((ROUTE))
       ```
7. Select **Apply Selected**.
   
   You can add only one `manifest.yaml` file.  

8. Select **Vars.yaml path** and repeat steps 6.1 and 6.2 to create a `vars` file. Then, enter the following information:
   
   ```
   NAME: harness_<+service.name>
   INSTANCE: 1
   ROUTE: harness_<+service.name>_<+infra.name>.apps.tas-harness.com
   ```
9.  Select **Apply Selected**.
    
   You can add any number of `vars.yaml` files.  

11. Select **AutoScaler.yaml** and repeat steps 6.1 and 6.2 to create an `autoscaler` file. Then, enter the following information:
    
    ```
    instance_limits:
      min: 1
      max: 2
    rules:
    - rule_type: "http_latency"
      rule_sub_type: "avg_99th"
      threshold:
        min: 100
        max: 200
    scheduled_limit_changes:
    - recurrence: 10
      executes_at: "2032-01-01T00:00:00Z"
      instance_limits:
        min: 1
        max: 2
    ```
12. Select **Apply Selected**.
    
    You can add only one `autoscaler.yaml` file. 

13. Select **Submit**.
