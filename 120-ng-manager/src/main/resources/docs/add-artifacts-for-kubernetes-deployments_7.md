## Service account of the Kubernetes Delegate can pull from the private repo

If the Kubernetes service account used by the Connector or Delegate has permission to pull from the private repo, you do not need to use the **Artifacts** settings in the Harness Service Definition. You can hardcode the image location in the manifest, and the Delegate will pull it at runtime.  

If multiple Delegates are selected, make sure that all of their service accounts have permission to pull from the private repo.
