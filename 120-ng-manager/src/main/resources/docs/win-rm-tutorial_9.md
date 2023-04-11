# Use WinRM Credentials with NTLM to Authenticate

We will now create the credentials for the secret that is used by Harness to connect to the target host.

1. For **Specify Credentials**, click **Create or Select a Secret**.
2. in **Create or Select an Existing Secret**, click **New WinRm Credential**.
3. In **WinRm Details**, enter a name for this credential.
4. For **Select an Auth Scheme**, keep the default **NTLM**.
5. For **Domain**, enter the domain name for your EC2 instance.
6. For **Username**, enter the user name used to access the EC2 instance.
7. For **Password**, click **Create or Select a Secret**.
8. In **Create or Select an Existing Secret**, click **New Secret Text**.
9. In **Add new Encrypted text**, enter a name for this secret. This is the name you will use to reference the text elsewhere in your resources.
10. In **Secret Value**, enter the password that you use to access the EC2 instance. Click **Save** and **Password** is populated with the secret you created.
11. Click the checkboxes for **Use SSL** and **Skip Cert Check**. Leave the **Use No Profile checkbox** empty.
12. For **WinRM Port**, keep the default port number **5986**. Click **Save and Continue**.
13. For **Add a Host Name to start Verification**, enter the hostname for your EC2 instance and click **Test Connection**. Harness checks for Delegates and verifies the connection to the target host.
14. In **Create New Infrastructure**, click **Preview Hosts**.
15. Click the checkbox for the host and click **Test Connection.** The WinRM connection to the EC2 instance is tested. Click **Finish**. Click **Continue**.
