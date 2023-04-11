# Skipping the tracking label

Once the relevant feature flag is enabled, a Harness Kubernetes canary deployment works like this:

* If the deployment object already exists in the cluster without the `harness.io/track: stable` label, Harness will not add the `harness.io/track: stable` label to the deployment object.
* If the deployment object already exists with the `harness.io/track: stable` label, Harness will not delete it.
* For any new deployment object, Harness will not add the `harness.io/track: stable` label.
