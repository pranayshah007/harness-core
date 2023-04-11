# Apply step behavior

The Apply step performs the following tasks:

1. Fetches the Kubernetes manifests from the repo defined the Harness service.
2. Renders any values.yaml and manifest files using Go templating and previews the files in the step log.
3. Performs a dry run to show you what resources are about to be created.
4. Applies the resources to the target Kubernetes cluster.
5. Checks that all deployed resources reached steady state. Steady state means the pods are healthy and up and running in the cluster.
6. Prints a summary of the applied resources to the step log.
