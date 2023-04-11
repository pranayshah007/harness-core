# Important notes

* Harness does not support AWS cross-account access for [ChartMuseum](https://chartmuseum.com/) and AWS S3. For example, if the Harness Delegate used to deploy charts is in AWS account A, and the S3 bucket is in AWS account B, the Harness Cloud Provider that uses this Delegate in A cannot assume the role for the B account.
* Harness cannot fetch Helm chart versions with Helm OCI because Helm OCI no longer supports `helm chart list`. See [OCI Feature Deprecation and Behavior Changes with Helm v3.7.0](https://helm.sh/docs/topics/registries/#oci-feature-deprecation-and-behavior-changes-with-v370).
* Currently, you cannot list the OCI image tags in Harness. You must pass the tag as a runtime input from a previous step or as a trigger. This is a Helm limitation. For more information, go to [Helm Search Repo Chart issue](https://github.com/helm/helm/issues/11000).
