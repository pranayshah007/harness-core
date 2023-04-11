## Basic Values YAML and Manifests for Public Image

This is a simple example using the Artifact reference `<+artifact.image>`. It can be used whenever the public image isn't hardcoded in manifests.

We use Go templating with a values.yaml file and manifests for deployment, namespace, and service. The manifests for deployment, namespace, and service are in a **templates** folder that is a peer of the values.yaml file.
