## Private repo artifact

In some cases, your Kubernetes cluster might not have the permissions needed to access a private repo (GCR, etc). For these cases, you use the expression `<+artifact.imagePullSecret>` in the Values file and reference it in the Secret and Deployment objects in your manifest.  
This key will import the credentials from the Docker credentials file in the artifact.  
See [Example Manifests](#example-manifests).
