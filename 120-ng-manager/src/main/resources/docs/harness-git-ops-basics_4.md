## GitOps vs Config-as-Code vs Harness Git Experience

Harness has multiple Git-based features and it's important to understand the differences:

* **GitOps:** used for deploying infrastructure and services. The Git commit in a source manifest repo triggers a sync between the desired state in Git and the live cluster state. This can be used to simply bootstrap clusters or for full service deployments.
* **Config-as-Code:** Harness supports full YAML-based configuration of Pipelines and other Harness entities like Connectors. Harness Pipeline Studio includes a full YAML IDE with hints and autocomplete, so you can simply code your Pipelines as YAML. See [Harness YAML Quickstart](../../platform/8_Pipelines/harness-yaml-quickstart.md).
* **Harness Git Experience:** Harness can sync your Pipelines and other entities with Git repos so you can make all your changes in Git instead of, or in addition to, using the Harness Manager UI. See [Harness Git Experience Overview](../../platform/10_Git-Experience/harness-git-experience-overview.md).
