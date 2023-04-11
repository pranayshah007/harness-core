# Job parameters

If you are using a [parameterized build](https://wiki.jenkins.io/display/JENKINS/Parameterized+Build), when you select the job in **Job Name**, Harness will automatically populate any job parameters from the server.

![](./static/run-jenkins-jobs-in-cd-pipelines-30.png)

You can also add parameters manually by selecting **Add Job Parameter**.

Runtime inputs and expressions are supported for the **Value** only. You can reference a job parameter from the **Input** tab of the executed step.


| **Job parameters from Jenkins step** | **Executed Jenkins step inputs** |
| --- | --- |
| ![](static/jenkinsparamfromjenkins.png) | ![](static/xecutedjenkinsinputs.png) |
