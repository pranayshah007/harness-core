## Services

A Service represents your microservices and other workloads.

A Service is an entity to be deployed, monitored, or changed independently.

When a Service is added to the stage in a Pipeline, you define its Service Definition. Service Definitions represent the real artifacts, manifests, and variables of a Service. They are the actual files and variable values.

You can also propagate and override a Service in subsequent stages by selecting its name in that stage's Service settings.

For examples, see:

* [Kubernetes Services](../../cd-services/k8s-services/kubernetes-services.md)
* [Propagate and Override CD Services](../../cd-services/cd-services-general/propagate-and-override-cd-services.md)
