### Wave deployments

You often hear the term wave deployments used when PR Pipelines are discussed.

A wave deployment is a deployment strategy in Continuous Delivery that involves releasing changes to a portion of users at a time, rather than all users at once. Typically, this is done using separate cloud regions for each target environment.

Wave deployments help reduce the risk of deployment failures and allow for quick recovery. The changes are rolled out in waves, typically starting with a group of users in one region and gradually expanding to the entire user base across all regions. This approach allows for a more controlled and monitored rollout of changes, improving the overall quality and stability of the deployment process.

With Harness GitOps, you can implement wave deployments by creating multiple environments for your application: one environment for each cloud region. Then, gradually promote changes from one environment to the next. This way, you can test changes in a safe and controlled manner before releasing them to the entire user base.

PR Pipelines support the wave deployments practice by allowing you to change a microservice in each target environment as needed.
