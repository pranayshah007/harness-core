# Basic Deployments

In a Basic deployment, a new Service/Artifact version is deployed to the deployment slot. Basic deployments are useful for development, learning Harness, and any non-mission critical workflows.

The Basic strategy adds the **Slot Deployment** step automatically, but it does not add other steps like **Traffic Shift** or **Swap Slot**.

While **Traffic Shift** and **Swap Slot** can be added, it's better to select the Canary or Blue Green strategies for these steps.
