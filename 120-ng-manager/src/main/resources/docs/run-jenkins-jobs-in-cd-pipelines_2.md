# Limitations and requirements

* **EnvInject plugin**: For Harness to capture Jenkins environment variables, your Jenkins configuration requires the [EnvInject plugin](https://wiki.jenkins.io/display/JENKINS/EnvInject+Plugin). The plugin does not provide full compatibility with the pipeline plugin. Go to [known limitations](https://plugins.jenkins.io/envinject) from Jenkins for more information.
* **CD and custom stage types only**: The Jenkins step is available in CD (deploy) and custom stages only.
* **Jenkins with GitHub plugin**: Branch names cannot contain double quotes if you are using Jenkins with a GitHub plugin. If any previous build executed by Jenkins used a branch with double quotes in its name, delete the history of branches built already, or recreate the job.
