# Notes

You can use Go templating in your Kubernetes resource files, just as you would for files stored in Git or inline. See [Example Kubernetes Manifests Using Go Templating](../../cd-technical-reference/cd-k8s-ref/example-kubernetes-manifests-using-go-templating.md). For OpenShift, you must use OpenShift templating.

If the artifact you are deploying with your manifest is public (DockerHub) and does not require credentials, you can use the standard public image reference, such as `image: harness/todolist-sample:11`.

