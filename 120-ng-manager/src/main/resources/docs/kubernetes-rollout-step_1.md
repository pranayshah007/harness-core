# Rollout Deployments

The Rollout Deployment step performs a Kubernetes [rolling update strategy](https://developer.harness.io/docs/continuous-delivery/cd-deployments-category/deployment-concepts#rolling-deployment). All nodes within a single environment are incrementally added one-by-one with a new service/artifact version.

The new pods are scheduled on nodes with available resources. The rolling update Deployment uses the number of pods you specified in the Service Definition **Manifests** (number of replicas).

Similar to application-scaling, during a rolling update of a Deployment, the Kubernetes service will load-balance the traffic only to available pods (an instance that is available to the users of the application) during the update.
