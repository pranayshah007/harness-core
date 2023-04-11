# Labels

The following labels are applied by Harness during deployment.


| **Label** | **Value** | **Usage** |
| --- | --- | --- |
| `harness.io/release-name` | `release name` | Applied on pods. Harness uses a release name for tracking releases, rollback, etc. You can supply a release name in an Environment's Infrastructure Definition **Release Name** field. |
| `harness.io/track` | `canary` \| `stable` | Applied on pods in a Canary deployment. |
| `harness.io/color` | `blue` \| `green` | Applied on pods in a Blue/Green deployment. |
