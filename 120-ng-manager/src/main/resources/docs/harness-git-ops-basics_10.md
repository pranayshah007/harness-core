## Can I use Harness GitOps images from a local registry?

If you want Kubernetes to pull images from your private registry instead of the public registries, you can simply pull the public images, add then to your local registry, and then update the Agent YAML to use the local registry.
