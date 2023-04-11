### Release Name

**Release name** is located in **Advanced**. You do not need to edit it.

During deployment Harness creates a ConfigMap listing the resources of the release and uses the **Release name** for tracking them.

The **Release name** is a combination of `release-` and a unique string created using the Harness expression `<+INFRA_KEY>`.

For example, in a Kubernetes deployment you can see `harness.io/release-name=release-2f9eadcc06e2c2225265ab3cbb1160bc5eacfd4f`.

In Harness, the **Release Name** is displayed in the logs of the deployment step:

![](./static/define-your-kubernetes-target-infrastructure-00.png)

The release name must be unique across the cluster. `release-<+INFRA_KEY>` ensures a unique name.

`release-` is used as a prefix because Kubernetes service and pod names must follow RFC-1035 and must consist of lowercase alphanumeric characters or '-', start with an alphabetic character, and end with an alphanumeric character.

See [Kubernetes Releases and Versioning](../../cd-technical-reference/cd-k8s-ref/kubernetes-releases-and-versioning.md).
