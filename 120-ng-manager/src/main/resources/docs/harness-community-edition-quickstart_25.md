### Use one of your company's Kubernetes clusters

If your company uses Kubernetes, they likely have dev or QA accounts on a cloud platform. If you have access to those environments, you can add a cluster there.

If you don't have access, you can request access to a namespace in an existing cluster. Just ask for access to a namespace and a service account with permission to create entities in the target namespace.

The YAML provided for the Harness Delegate defaults to the `cluster-admin` role because that ensures anything could be applied. If you can't use `cluster-admin` because you are using a cluster in your company, you'll need to edit the Delegate YAML.

The set of permissions should include `list`, `get`, `create`, `watch` (to fetch the pod events), and `delete` permissions for each of the entity types Harness uses.

If you don’t want to use `resources: [“*”]` for the Role, you can list out the resources you want to grant. Harness needs `configMap`, `secret`, `event`, `deployment`, and `pod` at a minimum for deployments, as stated above.
