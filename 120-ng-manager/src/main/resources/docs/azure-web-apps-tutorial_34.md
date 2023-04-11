## Application Settings and Connection Strings

* If you add App Service Configuration settings in the Harness Service, you must include a **name** (`"name":`), and the name must be unique. This is the same requirement in Azure App Services.
* Do not set Docker settings in the Harness Service **Application Settings** and **Connection Strings**. Harness will override these using the Docker settings in the artifact you add to the Harness Service in **Artifact**.
