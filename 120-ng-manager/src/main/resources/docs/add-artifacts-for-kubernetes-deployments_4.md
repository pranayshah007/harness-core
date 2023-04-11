## Public artifact hardcoded in the manifest

If the image location is hardcoded in your Kubernetes manifest (for example, `image: nginx:1.14.2`), you do not need to use the **Artifacts** settings in the Harness Service Definition. The Harness Delegate will pull the image at runtime. You simply need to ensure that the Delegate can connect to the public repo.
