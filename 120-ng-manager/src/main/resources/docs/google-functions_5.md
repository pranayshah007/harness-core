# Cloud Functions permission requirements

When you set up a Harness GCP connector to connect Harness with your GCP account, the GCP IAM user or service account must have the appropriate permissions assigned to their account. 

<details>
<summary>Cloud Functions minimum permissions</summary>

Cloud Functions supports the basic roles of **Editor**, **Owner**, **Developer**, and **Viewer**.

You can use the **Owner** role or the permissions below.

Here are the minimum permissions required for deploying Cloud Functions.

- **Cloud Functions API:**
  - `cloudfunctions.functions.create`: Allows the user to create new functions.
  - `cloudfunctions.functions.update`: Allows the user to update existing functions.
  - `cloudfunctions.functions.list`: Allows the user to list existing functions.
  - `cloudfunctions.operations.get`: Allows the user to get the status of a function deployment.
- **Cloud Run API:**
  - `run.services.create`: Allows the user to create new Cloud Run services.
  - `run.services.update`: Allows the user to update existing Cloud Run services.
  - `run.services.get`: Allows the user to get information about a Cloud Run service.
  - `run.revisions.create`: Allows the user to create new revisions for a Cloud Run service.
  - `run.revisions.get`: Allows the user to get information about a revision of a Cloud Run service.
  - `run.routes.create`: Allows the user to create new routes for a Cloud Run service.
  - `run.routes.get`: Allows the user to get information about a route of a Cloud Run service.

Note that these are the minimum set of permissions required for deploying Cloud Functions and running them on Cloud Run via API. Depending on your use case, you may need additional permissions for other GCP services such as Cloud Storage, Pub/Sub, or Cloud Logging.

Also, note that in order to call the Cloud Functions and Cloud Run APIs, the user or service account must have appropriate permissions assigned for calling the APIs themselves. This may include permissions like `cloudfunctions.functions.getIamPolicy`, `run.services.getIamPolicy`, `cloudfunctions.functions.testIamPermissions`, `run.services.testIamPermissions`, and `iam.serviceAccounts.actAs`.

For details, go to [Cloud Functions API IAM permissions](https://cloud.google.com/functions/docs/reference/iam/permissions) and Cloud Functions [Access control with IAM](https://cloud.google.com/functions/docs/concepts/iam).

Harness will also pull the function ZIP file in your **Google Cloud Storage**.

For Google Cloud Storage (GCS), the following roles are required:

- Storage Object Viewer (`roles/storage.objectViewer`)
- Storage Object Admin (`roles/storage.objectAdmin`)

For more information, go to the GCP documentation about [Cloud IAM roles for Cloud Storage](https://cloud.google.com/storage/docs/access-control/iam-roles).

Ensure the Harness delegate you have installed can reach `storage.cloud.google.com` and your GCR registry host name, for example `gcr.io`. 

</details>

