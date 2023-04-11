# Notes

* When using names in Harness Kubernetes stages, remember that Kubernetes service and pod names follow DNS-1035 and must consist of lowercase alphanumeric characters or '-', start with an alphabetic character, and end with an alphanumeric character.
* **GCP Workload Identity:** if you installed the Harness Kubernetes Delegate in a GCP Kubernetes cluster (GKE) that has GCP Workload Identity enabled, the GCP Connector will use the GCP Workload Identity if it inherits its credentials from that Delegate (using the **Use the credentials of a specific Harness Delegate** option).