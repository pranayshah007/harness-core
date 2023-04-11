### Use the same Delegate for fetching chart and deployment steps

Chart fetching and deployment is performed by the same step. For example, in a Kubernetes Rolling deployment strategy it is performed by the Rolling step.

You can select a Delegate for a step to use in the step's **Advanced** settings, **Delegate Selector**.

![](./static/use-a-local-helm-chart-00.png)

Ensure that the Delegate(s) selected here is the same Delegate(s) with the local Helm chart install and the Delegate YAML updated accordingly.
