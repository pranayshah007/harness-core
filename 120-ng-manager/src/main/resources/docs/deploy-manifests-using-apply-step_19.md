## Override priority

At pipeline runtime, Harness compiles a single values YAML for the deployment using all of the values YAML files you have set up across the stage's service, environment overrides, and the Apply step.

Harness merges all of the values YAML values/files into one file.

If two or more sources have the same `name:value` pairs (for example, `replicas: 2`), that is a conflict that Harness resolves using the following priority order (from highest to lowest):

1. Kubernetes Apply Step **Override Value**.
2. Environment Service Overrides (if you are using [Services and Environments v2](../../onboard-cd/cd-concepts/services-and-environments-overview.md)).
3. Environment Configuration (if you are using [Services and Environments v2](../../onboard-cd/cd-concepts/services-and-environments-overview.md)).
4. Service Definition Manifests.

![](./static/deploy-manifests-using-apply-step-26.png)

The override priority for these is in reverse order, so 2 overrides 1 and so on.
