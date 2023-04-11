# Option: Fetch Helm chart dependencies

Harness can fetch Helm chart dependencies within GitHub using the `--dependency-update` command flag. Harness fetches dependent Helm charts along with the main Helm chart being targeted for the deployment. These dependencies will be resolved before Harness performs the deployment of the primary Helm chart configured with the service explicitly. 

For more information, go to [Helm Docs](https://helm.sh/docs/helm/helm_template/#helm).

To update Helm chart dependencies:

* For Kubernetes deployments, configure the template with `--dependency-update` command flag.

![](./static/update-helm-dependency-k8s.png)

* For Native Kubernetes deployments, add the command flag, `--depdency-update` to `Install` and `Upgrade` commands.

![](./static/update-helm-dependencies-nativek8s.png)

:::info
All dependency repositories must be available and accessible from delegate.
:::
