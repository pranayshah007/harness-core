# Add the artifact

Currently, Harness supports ZIP file artifacts only. Harness doesn't support Docker images yet.Next, we'll add a publicly-available artifact to your Service. The artifact is a zip file with a JavaScript function hosted in Artifactory.

We'll add a new Artifactory Connector and install a Harness Kubernetes Delegate in a Kubernetes cluster. The Delegate is a worker process that performs the deployment operations. The Delegate will use the URL and credentials you provide in the Connector to connect to Artifactory and fetch the artifact at runtime.

1. In **Artifact**, click **Add Primary**.
2. In **Specify Artifact Repository Type**, click **Artifactory**, and click **Continue.**
3. In **Artifactory Repository**, click **New Artifactory Connector**.
4. In **Create or Select an Existing Connector**, click **New Artifactory Connector**.
5. Enter a name for the Connector, such as **JFrog Serverless**. Click **Continue**.
6. In **Details**, in **Artifactory Repository URL**, enter `https://harness.jfrog.io/artifactory/`.
7. In **Authentication**, select **Anonymous**.
   
   ![](./static/serverless-lambda-cd-quickstart-113.png)

1. In **Delegates Setup**, click **Install new Delegate**. The Delegate wizard appears.
8. Click **Kubernetes**, and then click **Continue**.
   
   ![](./static/serverless-lambda-cd-quickstart-114.png)

1. Enter a name for the Delegate, like **serverlesslocal**, click the **Laptop** size.
   
   ![](./static/serverless-lambda-cd-quickstart-115.png)
   
   With the **Laptop** size, you can use a local minikube cluster or a small cluster hosted on a cloud platform. If you use minikube, start minikube with the following resources: `minikube start --memory 3g --cpus 2`.
1. Click **Continue**.
2. Click **Download YAML file**. The YAML file for the Kubernetes Delegate is downloaded to your computer.
