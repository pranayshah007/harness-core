### Harness supports list objects

You can leverage Kubernetes list objects as needed without modifying your YAML for Harness.

When you deploy, Harness will render the lists and show all the templated and rendered values in the log.

Harness supports:

* List
* NamespaceList
* ServiceList
* For Kubernetes deployments, these objects are supported for all deployment strategies (Canary, Rolling, Blue/Green).
* For Native Helm, these objects are supported for Rolling deployments.

If you run `kubectl api-resources` you should see a list of resources, and `kubectl explain` will work with any of these.
