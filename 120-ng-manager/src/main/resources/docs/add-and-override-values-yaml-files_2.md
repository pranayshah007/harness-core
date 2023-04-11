# Limitations

* For Kubernetes deployments, the values.yaml file used in Harness doesn't support Helm templating, only Go templating.
* Helm templating is fully supported in the remote Helm charts you add to your Harness Service. If you add a Helm chart and a values.yaml, the values.yaml can use Helm templating.
