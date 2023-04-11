## Before You Begin

* Review [Harness Key Concepts](../../../first-gen/starthere-firstgen/harness-key-concepts.md) to establish a general understanding of Harness.
* Make sure that you have a Delegate available in your environment.
	+ You can install a Kubernetes or Docker Delegate. See Install Delegates.
	+ Ideally, you should install the Delegate in the same subnet as the target host(s).
* Target host: in this guide, we use an AWS EC2 instance as the target host with a minimum t2-medium.
* SSH Keys for the target host(s): you will need [SSH Keys](../../../platform/6_Security/4-add-use-ssh-secrets.md#add-ssh-credential) for the target hosts. For example, in this tutorial, we connect to an AWS EC2 instance by providing the username and an existing secret file for that AWS EC2 instance. When a EC2 instance is created, a Key Pair is generated in AWS. From the Key Pair for the AWS EC2 instance, you can download a .PEM file to your machine and upload that file to Harness as a secret file.

You can also simply deploy the artifact to your local computer instead of using an AWS EC2 instance. If you want to do this, install the Harness Delegate on your local computer (for example, using Docker Desktop), use a [Physical Data Center](../../../first-gen/firstgen-platform/account/manage-connectors/add-physical-data-center-cloud-provider.md) Connector instead of an AWS Connector, and when you set up the target infrastructure SSH key in Harness, use your local login information. You might also need to enable remote access on your computer.
