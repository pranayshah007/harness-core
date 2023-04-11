## ApplicationSets

An ApplicationSet uses an ApplicationSet controller to automatically and dynamically generate applications in multiple target environments. A GitOps ApplicationSet is similar to a GitOps Application but uses a template to achieve application automation using multiple target environments.

ApplicationSet is supported in your cluster using a [Kubernetes controller](https://kubernetes.io/docs/concepts/architecture/controller/) for the `ApplicationSet` [CustomResourceDefinition](https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definitions/) (CRD). You add an ApplicationSet manifest to a Harness GitOps Application just like you would add a typical Deployment manifest. At runtime, Harness uses the ApplicationSet to deploy the application to all the target environments' clusters.

![](./static/harness-git-ops-application-set-tutorial-61.png)
