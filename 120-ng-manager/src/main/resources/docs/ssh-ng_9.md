# Create the PDC Connector for the Host

1. In **Infrastructure Definition**, for **Connector**, click **Select Connector** to create the Connector for PDC.
   
   ![](./static/ssh-ng-175.png)

8. In **Create or Select an Existing Connector**, select **New Connector**.
9.  In **Physical Data Center**, enter a name for this connector: **PDC-Connector**.
10. Click **Continue**.
   
   ![](./static/ssh-ng-176.png)

11. In **Details**, keep the default for **Manually enter host names** and enter the hostname for the EC2 instance.
12. Click **Continue**.

   ![](./static/ssh-ng-177.png)

13. In **Delegates Setup**, keep the default for **Use any available Delegate**. 
14. Click **Save and Continue**. Harness validates connectivity for the PDC connector.
   
   ![](./static/ssh-ng-178.png)
   
   For information on installing a delegate, see [Delegate installation overview](/docs/platform/2_Delegates/delegate-concepts/delegate-overview.md).
15. Click **Finish**. The Infrastructure Definition is updated with the PDC Connector.
  
![](./static/ssh-ng-179.png)
