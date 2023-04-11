### Permissions

The third-party credentials used in Harness connectors require different permissions depending on your deployment type (Kubernetes, ECS, Serverless, etc) and the tasks your pipelines will perform in those platforms.

For example, if your Pipeline deploys a Docker image to a Kubernetes cluster, you will need a Connector that can pull the images and any related manifests from their repositories. Also, you will need another Connector that can push the image to the target cluster and create any objects you need (Secrets, Deployments, etc).
