## Disable Data Collection

Today, user tracking can be disabled manually. Soon, it will be an option in the Harness installation process.

To disable user tracking, navigate to the **environment** folder in your Harness CD Community Edition installation: `harness-cd-community/docker-compose/harness/environment`.

Change the following environment variables in the following files:

* **manager.env:** change `SEGMENT_ENABLED_NG=true` to `SEGMENT_ENABLED_NG=false`.
* **ng-manager.env:** change `SEGMENT_ENABLED=true` to `SEGMENT_ENABLED=false`.
* **pipeline-service.env:** change `SEGMENT_ENABLED=true` to `SEGMENT_ENABLED=false`.

Save all files, and then restart Harness using `docker-compose up -d`.
