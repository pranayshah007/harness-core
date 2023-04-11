## What if I have a local cluster for the Delegate but want to deploy to a remote cluster?

If you can't use a local or remote cluster for both the Harness Delegate and the deployment, you'll be fine.

Install the Delegate locally and then when you set up the Harness Kubernetes Cluster Connector, select **Specify master URL and credentials** and use the target cluster master URL and a Kubernetes service account token from the target cluster to connect.

![](./static/harness-community-edition-quickstart-139.png)

You can get the master URL by connecting to the cluster and running `kubectl cluster-info`.

To use a Kubernetes Service Account (SA) and token, you will need to either use an existing SA that has the `cluster-admin` permission (or namespace-level admin permissions) or create a new SA and grant it the `cluster-admin` permission (or namespace-level admin permissions).

For example, here's a manifest that creates a new SA named `harness-service-account` in the `harness` namespace.

```yaml