# Review: Primary Deployment Rolling Update

The Primary Deployment group runs the actual deployment as a rolling update with the number of pods you specify in the Service Definition **Manifests** files (for example, `replicas: 3`).

Click **Rolling Deployment**. For details on its settings, see [Kubernetes Rollout Step](../../cd-technical-reference/cd-k8s-ref/kubernetes-rollout-step.md).

Similar to application-scaling, during a rolling update of a Deployment, the Kubernetes service will load-balance the traffic only to available pods (an instance that is available to the users of the application) during the update.

Rolling updates allow an update of a Deployment to take place with zero downtime by incrementally updating pod instances with new ones. The new pods are scheduled on nodes with available resources. The rolling update Deployment uses the number of pods you specified in the Service Definition **Manifests** (number of replicas).
