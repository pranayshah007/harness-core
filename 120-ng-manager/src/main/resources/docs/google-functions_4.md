# Deployment summary

Here's a high-level summary of the setup steps.

<details>
<summary>Harness setup summary</summary>

1. Create a Harness CD pipeline.
2. Add a Deploy stage.
3. Select the deployment type **Google Cloud Functions**, and then select **Set Up Stage**.
4. Select **Add Service**.
   1. Add the function definition to the new Cloud Function service. You can paste in the YAML or link to a Git repo hosting the YAML.
   2. Save the new service.
5. Select **New Environment**, name the new environment and select **Save**.
6. In **Infrastructure Definition**, select **New Infrastructure**.
   1. In Google Cloud Provider Details, create or select the Harness GCP connector, **GCP project**, and **GCP region**, and select **Save**.
7.  Select Configure and select the deployment strategy: basic, canary, or blue green.
8.  Harness will automatically add the **Deploy Cloud Function** step. No further configuration is needed.
    1.  For canary and blue green strategies, the **Cloud Function Traffic Shift** step is also added. In this step's **Traffic Percent** setting, enter the percentage of traffic to switch to the new revision of the function.
    2.  For canary deployments, you can add multiple **Cloud Function Traffic Shift** steps to rollout the shift.
    3.  For blue green deployments, you can simply use 100 in the **Traffic Percent** setting.
9.  Select **Save**, and then run the pipeline.

</details>

