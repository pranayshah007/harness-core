## Add a Health Check after Slot Deployment

Typically, it's a good practice to add a health check to ensure that the Docker container or non-containerized app is running correctly.

The Slot Deployment step is considered successful once the slot is in a running state. A running state does not ensure that your new app is accessible. It can take some time for new content to become available on Azure. Also, the slot deployment might succeed but the Docker container or non-containerized artifact could be corrupted.

A health check after Slot Deployment can ensure a successful deployment.

A health check can be performed using a [Shell Script](../../cd-execution/cd-general-steps/using-shell-scripts.md) step. You can also use Harness [Approval](/docs/category/approvals) steps to ensure the app is running before proceeding to the Traffic Shift step.
