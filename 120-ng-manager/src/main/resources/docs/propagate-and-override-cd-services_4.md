# Propagate a service

1. Open or create a pipeline with at least one stage.
2. To add another stage to the pipeline, select the plus sign (+) or select **Add Stage** after the first stage, and then select **Deploy**.
   
   ![](./static/propagate-and-override-cd-services-01.png)
3. Enter a stage name and select **Set Up Stage**.
   
   The new stage is added to the Pipeline.
4. Select the **Service** tab if it is not already selected.
   
   The propagation option appears.
   
   ![](./static/propagate-and-override-cd-services-02.png)
5. Select **Propagate from** and then select the stage with the service you want to use.
   
   ![](./static/propagate-and-override-cd-services-03.png)
   
   You can see the stage name and service name.

This stage now uses the exact same Service as the stage you selected.
