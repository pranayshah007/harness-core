# Deploy one Service to multiple Environments or Infrastructures

You can deploy one Service to multiple Environments.

1. In your CD stage, click **Service**.
2. In **Select Services**, select the Service you want to deploy. Here's an example using Nginx:
   
   ![](./static/multiserv-multienv-10.png)

3. Click **Continue**.
4. In **Environments**, enable **Deploy to multiple Environments or Infrastructures**.
   You can select one or more Environments, and then one or more Infrastructures in each Environment.
5. In **Specify Environments**, select one or more **Environments**. Each Environment is now listed.
1. For each Environment, in **Specify Infrastructures**, select one or more Infrastructures, or select **All**.
   Here's an example using one Environment and two of its Infrastructures.

   ![](./static/multiserv-multienv-11.png)

   For details on **Deploy to Environments or Infrastructures in parallel?**, go to [Deploying in parallel or serial](#deploying_in_parallel_or_serial) below.

1. Click **Continue**, select an Execution strategy, and complete the Execution steps.
2. Click **Save**.
3. Click **Run**, and then **Run Pipeline**.
   You can see both Infrastructures displayed in the target Environment:
   
   ![](./static/multiserv-multienv-12.png)

   You can click each Infrastructure to see the deployment to it or user the console view to jump between the Infrastructures:

   ![](./static/multiserv-multienv-13.png)
