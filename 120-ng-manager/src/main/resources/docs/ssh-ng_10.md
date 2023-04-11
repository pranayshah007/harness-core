# Use SSH Credential for Authenticating to the Target Host

You can use an SSH Key or Kerberos for authenticating to the target host. In this tutorial, we will use an SSH Key.

1. In **Specify Credentials**, click **Create or Select a Secret**.
2. In **Create or Select an Existing Secret**, click **New SSH Credential**.
3. In **SSH Details**, for **Name**, enter **ssh-tutorial-key** for this SSH Credential and click **Continue**.
   
   ![](./static/ssh-ng-180.png)

4. In **Configuration and Authentication**, you have three authentication options: In this tutorial, we will use **Username/SSH Key**. Click the down-drop menu and select **Username/SSH Key**.
   
   ![](./static/ssh-ng-181.png)

1. For username, enter **ec2-user**. This is the user for the EC2 instance.
5. For **Select or create a SSH Key**, click **Create or Select a Secret**.
6. in **Create or Select an Existing Secret**, select **New Secret File**.
   
   ![](./static/ssh-ng-182.png)

7. in **Add new Encrypted File**, enter a name for **Secret Name**: **ssh-key-name**. This is the name you will use to reference this file.
8. For **Select File**, click **Browse**. On your machine, browse for the .pem file that you downloaded from your EC2 instance. Select that file and Harness uploads it to the **Select File** field. Click **Save**.
   
   ![](./static/ssh-ng-183.png)

9.  In **Configuration and Authentication**, keep the default values for **Passphrase** and **SSH port**. Click **Save and Continue**.
    
    ![](./static/ssh-ng-184.png)
    
10. In **Verify Connection**, enter the hostname for the EC2 instance in the **Add a Host Name to start verification** field and click **Connection Test**.
    
    ![](./static/ssh-ng-185.png)
    
11. The Secure Shell connection to the EC2 instance is tested. Click **Finish**. Click **Continue**.
    
    ![](./static/ssh-ng-186.png)
    
    You can use the **Preview Hosts** section to test the connection at any time.\
    
    ![](./static/ssh-ng-187.png)

1. Click **Save**.
1. Back in **Environment**, click **Continue**.

Next, you'll select the deployment strategy for this stage, the package type, and the number of instances to deploy on.

![](./static/ssh-ng-188.png)
