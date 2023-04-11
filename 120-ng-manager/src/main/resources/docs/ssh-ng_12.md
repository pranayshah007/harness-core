# Run the Pipeline to Deploy and Review

1. Click **Run** to run the pipeline.
2. In **Run Pipeline**, for **Primary Artifact**, select **Todolist**.
3. In **Artifact Path**, click the down-drop arrow and Harness displays a list of available artifact packages.
4. Select **todolist.war**.
   
   ![](./static/ssh-ng-192.png)

5. Click **Run Pipeline**. Harness runs the pipeline and the **Console View** displays the tasks executed for each step.

Let's review what is happening in the Deploy step. Most sections correspond to the commands you can see in the Deploy step.

1. **Initialize:** initialize the connection to the host(s) and create a temp directory for the deployment.
2. **Setup Runtime Paths:** create folders for runtime, backup, and staging.
3. **Copy Artifact:** copy the artifact to the host.
4. **Copy Config:** copy the config files (if any) to the host.
5. **Cleanup:** remove temp directories.

```
Initialize  
  
Initializing SSH connection to ec2-54-201-142-249.us-west-2.compute.amazonaws.com ....  
Connecting to ec2-54-201-142-249.us-west-2.compute.amazonaws.com ....  
Connection to ec2-54-201-142-249.us-west-2.compute.amazonaws.com established  
Executing command mkdir -p /tmp/aCy-RxnYQDSRmL8xqX4MZw ...  
Command finished with status SUCCESS  
Initializing SSH connection to ec2-54-201-142-249.us-west-2.compute.amazonaws.com ....  
Connecting to ec2-54-201-142-249.us-west-2.compute.amazonaws.com ....  
  
Setup Runtime Paths  
  
Initializing SSH connection to ec2-54-201-142-249.us-west-2.compute.amazonaws.com ....  
Connecting to ec2-54-201-142-249.us-west-2.compute.amazonaws.com ....  
Connection to ec2-54-201-142-249.us-west-2.compute.amazonaws.com established  
Executing command...  
Command finished with status SUCCESS  
  
Copy Artifact  
  
Filename contains slashes. Stripping off the portion before last slash.  
Got filename: todolist.war  
Connecting to ec2-54-201-142-249.us-west-2.compute.amazonaws.com ....  
Connection to ec2-54-201-142-249.us-west-2.compute.amazonaws.com established  
Begin file transfer todolist.war to ec2-54-201-142-249.us-west-2.compute.amazonaws.com:/home/ec2-user/tutorial-service-ssh2/ssh-tutorial-env  
File successfully transferred to ec2-54-201-142-249.us-west-2.compute.amazonaws.com:/home/ec2-user/tutorial-service-ssh2/ssh-tutorial-env  
Command finished with status SUCCESS  
  
Copy Config  
  
Filename contains slashes. Stripping off the portion before last slash.  
Got filename: todolist.war  
Connecting to ec2-54-201-142-249.us-west-2.compute.amazonaws.com ....  
Connection to ec2-54-201-142-249.us-west-2.compute.amazonaws.com established  
Begin file transfer todolist.war to ec2-54-201-142-249.us-west-2.compute.amazonaws.com:/home/ec2-user/tutorial-service-ssh2/ssh-tutorial-env  
File successfully transferred to ec2-54-201-142-249.us-west-2.compute.amazonaws.com:/home/ec2-user/tutorial-service-ssh2/ssh-tutorial-env  
Command finished with status SUCCESS  
  
Cleanup  
  
Initializing SSH connection to ec2-54-201-142-249.us-west-2.compute.amazonaws.com ....  
Connecting to ec2-54-201-142-249.us-west-2.compute.amazonaws.com ....  
Connection to ec2-54-201-142-249.us-west-2.compute.amazonaws.com established  
Executing command rm -rf /tmp/aCy-RxnYQDSRmL8xqX4MZw ...  
Command finished with status SUCCESS
```
Congratulations! You have now successfully created and completed the steps for running a pipeline by using Secure Shell.

In this tutorial you learned how to:

* Create a Harness Secure Shell Service.
* Set up a Harness Artifactory Connector for access to a public repository.
* Add the credentials needed to connect to target hosts.
* Define the target infrastructure for deployment.
* Select the Canary deployment strategy.
* Run the Pipeline and review.
