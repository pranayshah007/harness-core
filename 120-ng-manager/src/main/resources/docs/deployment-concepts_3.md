## When to use rolling deployments

* When you need to support both new and old deployments.
* Load balancing scenarios that require reduced downtime.

One use of Rolling deployments is as the stage following a Canary deployment in a deployment pipeline. For example, in the first stage you can perform a Canary deployment to a QA environment and verify each group of nodes and, once successful, you perform a Rolling to production.
