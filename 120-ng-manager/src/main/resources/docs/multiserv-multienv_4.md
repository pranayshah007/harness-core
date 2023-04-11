# Deploy multiple Services to multiple Environments

You can deploy multiple Services to multiple Environments and Infrastructures. You can deploy the Services serially or in parallel.

1. In your CD stage, click **Service**.
2. Enable the **Deploy multiple Services** setting.
3. In **Select Services**, select the Services you want to deploy.
   ![](./static/multiserv-multienv-19.png)

   For information on **Deploy services in parallel**, go to [Deploying in parallel or serial](#deploying_in_parallel_or_serial) below.If one or more of the Services uses Runtime Inputs, you can view the settings or switch them to Fixed Value and add a value.

   ![](./static/multiserv-multienv-20.png)

   The Services displayed depend on the **Deployment Type** in **Overview**.

1. Click **Continue**.
2. In **Environment**, enable **Deploy to multiple Environments or Infrastructures**.
3. Select multiple Environments and Infrastructures.
   In the following example, we are deploying to a single Environment but multiple Infrastructures in the Environment.
   
   ![](./static/multiserv-multienv-21.png)
   
4. Click **Continue**, select an Execution strategy, and complete the Execution steps.
5. Click **Save**.
6. Click **Run**, and then **Run Pipeline**.

You can see the two Service deployments running in parallel on both Infrastructures.

![](./static/multiserv-multienv-22.png)

The Service deployments are grouped by Infrastructure. The first two Services are running in parallel on one Infrastructure and the second two Services are running on the other Infrastructure.
