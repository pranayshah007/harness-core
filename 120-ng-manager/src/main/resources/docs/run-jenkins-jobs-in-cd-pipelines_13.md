# Multibranch pipeline support

For Harness to capture Jenkins environment variables, your Jenkins configuration requires the [EnvInject plugin](https://wiki.jenkins.io/display/JENKINS/EnvInject+Plugin). The plugin does not provide full compatibility with the pipeline plugin. Go to [known incompatibilities](https://wiki.jenkins.io/display/JENKINS/EnvInject+Plugin#EnvInjectPlugin-Knownincompatibilities) from Jenkins for more information. The Jenkins multibranch pipeline (workflow multibranch) feature enables you to automatically create a Jenkins pipeline for each branch on your source control repo.

Each branch has its own [Jenkinsfile](https://jenkins.io/doc/book/pipeline/jenkinsfile/), which can be changed independently. This features enables you to handle branches better by automatically grouping builds from feature or experimental branches.

In **Job Name**, multibranch pipelines are displayed alongside other jobs, with the child branches as subordinate options.

Select **>** and select the branch.
