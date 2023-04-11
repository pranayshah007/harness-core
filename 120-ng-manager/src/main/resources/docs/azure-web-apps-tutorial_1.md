# Objectives

You'll learn how to:

* Specify the Web App startup command, configuration, and artifact you want to deploy as a Harness Service.
* Connect Harness with your Azure subscription.
* Define the target Web App in Harness and install a Harness Delegate to perform the deployment.
* Define the steps for the different deployment strategies:
	+ **Basic:** Slot Deployment step. No traffic shifting takes place.
	+ **Canary:** Slot Deployment, Traffic Shift, and Swap Slot steps. Traffic is shifted from the source slot to the target slot incrementally.
	+ **Blue Green:** Slot Deployment and Swap Slot steps. All traffic is shifted from the source slot to the target slot at once.
