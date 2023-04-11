# Sidecar workloads

You can use Harness to deploy both primary and sidecar Kubernetes workloads.

Kubernetes sidecar workloads are a powerful way to modularize and encapsulate application functionality while keeping the overall architecture simple and easy to manage.

Sidecars are commonly used to implement cross-cutting concerns like logging, monitoring, and security. By separating these concerns into separate containers, it's possible to add or modify them without affecting the primary container or the application running inside it.

For example, a logging sidecar can be used to capture and store application logs, metrics, and other data, without requiring the primary container to implement any logging code.

Sidecars can also be used to implement advanced features like load balancing, service discovery, and circuit breaking. By using a sidecar container for these features, it's possible to keep the primary container simple and focused on its core functionality, while still providing advanced capabilities to the application.

For more information, go to [Add a Kubernetes sidecar container](../../cd-advanced/cd-kubernetes-category/add-a-kubernetes-sidecar-container.md).

