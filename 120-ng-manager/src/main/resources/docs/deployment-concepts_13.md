### Cons

* Scripting canary deployments can be complex (Harness automates this process).
* Manual verification can take time (Harness automates this process with Continuous Verification).
* Required monitoring and instrumentation for testing in production (APM, Log, Infra, End User, etc).
* Database compatibility (schema changes, backward compatibility).

For Kubernetes, Harness does this a little different.

In Phase 1 we do a canary to the same group but we leave the production version alone. We just use other instances. Then we delete our canary version in Phase 1.

In Phase 2 we do a rolling deployment with the production version and scale down the older version.

![](./static/deployment-concepts-03.png)

See:

* [Create a Kubernetes Canary Deployment](../cd-execution/kubernetes-executions/create-a-kubernetes-canary-deployment.md)
