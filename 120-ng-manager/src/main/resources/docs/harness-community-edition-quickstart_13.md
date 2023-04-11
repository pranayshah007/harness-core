### Connector

1. Click the **Connector** dropdown menu, and then click **New Connector**.
2. In **Name**, enter **localK8s**, and then click **Continue**.
3. In **Details**, click **Use the credentials of a specific Harness Delegate**, and then click **Continue**.
   + If you are running a local Delegate but using a target cluster that does not have a Delegate installed in it, select **Specify master URL and credentials**, and then see [Notes](#notes).
4. In **Delegates Setup**, select **Connect only via Delegates which has all of the following tags**, and then enter and select **quickstart**. The Delegate you added earlier is selected.
5. Click **Save and Continue**.
6. In **Connection Test**, click **Finish**.
