# Where to add the Kubernetes Delete Step

You can add a Kubernetes Delete step anywhere in your Pipeline, but the resource you want to delete must already exist by the time Pipeline execution reaches the Kubernetes Delete step.

If you are using a Kubernetes Apply step, Canary Deployment, Rolling Deployment, or some provisioning step to create resources, ensure that you only add the Kubernetes Delete step after its target resource is created.
