# Basic Values YAML and Manifests for Public Image

This is a simple example using the Artifact reference `<+artifact.image>`. It can be used whenever the public image is not hardcoded in manifests.

See [Add Container Images as Artifacts for Kubernetes Deployments](../../cd-advanced/cd-kubernetes-category/add-artifacts-for-kubernetes-deployments.md).

We use Go templates with a values.yaml file and manifests for deployment, namespace, and service. The manifests for deployment, namespace, and service are in a **templates** folder that's a peer of the values.yaml file.
