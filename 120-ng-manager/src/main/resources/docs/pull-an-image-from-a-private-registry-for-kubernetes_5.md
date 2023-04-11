# Example: Values YAML and Manifests

This is a simple example using the Artifact `<+artifact.image>` and `dockercfg` references.

We use Go templating with a values.yaml file and manifests for deployment, namespace, and service. The manifests for deployment, namespace, and service are in a **templates** folder that is a peer of the values.yaml file.
