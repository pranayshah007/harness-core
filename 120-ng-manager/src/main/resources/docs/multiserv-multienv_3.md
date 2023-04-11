# Deploy multiple Services to one Environment

You can deploy multiple Services to the same Environment and single Infrastructure. You can deploy the Services serially or in parallel.

1. In your CD stage, click **Service**.
2. Enable the **Deploy multiple Services** setting.
3. In **Select Services**, select the Services you want to deploy.

![](./static/multiserv-multienv-14.png)

For information on **Deploy services in parallel**, go to [Deploying in parallel or serial](#deploying_in_parallel_or_serial) below.

If one or more of the Services uses Runtime Inputs, you can view the settings or switch them to Fixed Value and add a value.

![](./static/multiserv-multienv-15.png)

The Services displayed depend on the **Deployment Type** in **Overview**. For example, if the deployment type is Kubernetes, only Kubernetes Services are listed in the **Select Services** list.

![](./static/multiserv-multienv-16.png)

1. Click **Continue**.
2. In **Environment**, select a single Environment and Infrastructure. You don't need to enable **Deploy to multiple Environments or Infrastructures**.

![](./static/multiserv-multienv-17.png)

1. Click **Continue**, select an Execution strategy, and complete the Execution steps.
2. Click **Save**.
3. Click **Run**, and then **Run Pipeline**.

You can see the two Service deployments running in parallel on the same Infrastructure.

![](./static/multiserv-multienv-18.png)
