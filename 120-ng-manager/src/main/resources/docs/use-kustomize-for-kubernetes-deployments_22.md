## Delegate Selection

You might have multiple Delegates installed in your infrastructure, so you have to make certain that Harness uses the Delegate with the plugin installed.

Each **Execution** step has a **Delegate Selector** setting where you can select the Delegate used by that the step.

For example, if your stage Execution has **Canary**, **Canary Delete**, and **Rolling** steps, open each step, select **Advanced**, and then select the **Delegate Selector** for the Delegate that has the plugin installed.

![](./static/use-kustomize-for-kubernetes-deployments-11.png)

The Delegate Selector setting lists the Delegate Tags for all Delegates. You can see these Tags by looking at the Delegate details:

![](./static/use-kustomize-for-kubernetes-deployments-12.png)

Now the Delegate with the plugin installed is used for the Pipeline steps, and the plugin is located using the path you provided in **Manifests**.
