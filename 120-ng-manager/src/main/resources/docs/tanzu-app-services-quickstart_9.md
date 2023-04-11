## Add the artifact for deployment

1. In **Artifacts**, select **Add Artifact Source**.
2. In **Specify Artifact Repository Type**, select **Artifactory**, and select **Continue**.
3. In **Artifactory Repository**, click **New Artifactory Connector**.
4. Enter a name for the connector, such as **JFrog**, then select **Continue**.
5. In **Details**, in **Artifactory Repository URL**, enter `https://harness.jfrog.io/artifactory/`.
6. In **Authentication**, select **Anonymous**, and select **Continue**.
   
   ![](./static/artifactory-repo-connector.png)

7. In **Delegates Setup**, select **Only use Delegate with all of the following tags** and enter the name of the delegate created in [connect to a TAS provider (step 8)](#connect-to-a-tas-provider).
8. Select **Save and Continue**
9.  After the test connection succeeds, select **Continue**.
10. In **Artifact Details**, enter the following details:
    1.  Enter an **Artifact Source Name**.
    2.  Select **Generic** or **Docker** repository format.
    3.  Select a **Repository** where the artifact is located.
    4.  Enter the name of the folder or repository where the artifact is located.
    5.  Select **Value** to enter a specific artifact name. You can also select **Regex** and enter a tag regex to filter the artifact.
11. Select **Submit**.
