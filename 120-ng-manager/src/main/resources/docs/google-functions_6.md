# Cloud Functions services

The Harness Cloud Functions service the following: 

- **Function Definition**:
  - You can enter the function manifest YAML in Harness for use a remote function manifest YAML in a Git repo.
- **Artifacts**:
  - You add a connection to the function ZIP file in Google Cloud Storage.

<details>
<summary>How the Function Definition and Artifact work together</summary>

The function manifest YAML in **Function Definition** and the function zip file in **Artifacts** work together to define and deploy a Google Cloud Function.

The function manifest YAML file is a configuration file that defines the function's name, runtime environment, entry point, and other configuration options. The YAML file is used to specify the function's metadata, but it does not include the function's code.

The function code is packaged in a zip file that contains the function's source code, dependencies, and any other necessary files. The zip file must be created based on the manifest file, which specifies the entry point and runtime environment for the function.

When you deploy a function using the manifest file, the Cloud Functions service reads the configuration options from the YAML file and uses them to create the function's deployment package. The deployment package includes the function code (from the zip file), the runtime environment, and any dependencies or configuration files specified in the manifest file.

</details>

