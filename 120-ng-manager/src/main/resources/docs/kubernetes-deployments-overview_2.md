# What Does Harness Need Before You Start?

A Harness Kubernetes deployment requires the following:

1. Kubernetes manifests and values YAML files.
2. Artifact, if not hardcoded in manifests or values file. For example, a Docker image of NGINX from Docker Hub.
3. Kubernetes cluster: You will need a target cluster for the Harness Delegate, your app, and your Kubernetes workloads. Your cluster should have enough RAM to host the Delegate and your apps and workloads.
