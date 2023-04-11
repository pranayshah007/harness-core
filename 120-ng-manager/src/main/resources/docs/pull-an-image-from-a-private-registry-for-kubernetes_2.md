# Review: Private Repo Authentication for Container Instances

When you are using private Docker images, you must authenticate with the repo to pull the image. The encrypted dockercfg file provides the credentials needed to authenticate.

The dockercfg file is located in the Docker image artifact. You reference this file in your values.yaml or manifests. Harness imports the credentials from the file to access the private repo.
