## Services Dashboard

**What is a Harness Service?** A Harness Service is both a logical and configuration view of the services you deploy.

The logical view is immutable and contains a name, unique Id, and description. The configuration view can be changed with each stage of a Pipeline and contains the artifacts, manifests, repo URLs, etc for that stage's deployment of the Service.

**What is a Service instance in Harness?** Harness licensing is determined by the service instances you deploy. A service instance is when you use Harness to deploy the underlying infrastructure for the instance. For example, an instance of a Kubernetes workload where Harness creates the pods.

The Services dashboard provides an overview of all the Services and Service instances in your Project:

![](./static/monitor-cd-deployments-19.png)

Click a Service in the Total Services table drills down to show more Service details:

![](./static/monitor-cd-deployments-20.png)
