## Test

Let's look at a simple example to show how Queue steps work.

Here's the YAML for the Pipeline that contains a Custom Stage with a Queue Step followed by a [Shell Script](../cd-execution/cd-general-steps/using-shell-scripts.md) step that runs a Bash `sleep 30`.

You can copy it and paste it into your Harness Project. You simply need to update the `projectIdentifier` and `orgIdentifier` settings to match your current [Project and Org](../../platform/organizations-and-projects/projects-and-organizations.md).


```yaml
pipeline:  
    name: Queue  
    identifier: Queue  
    projectIdentifier: queuesteptest  
    orgIdentifier: default  
    tags: {}  
    stages:  
        - stage:  
              name: Queue  
              identifier: Queue  
              description: ""  
              type: Custom  
              spec:  
                  execution:  
                      steps:  
                          - step:  
                                type: Queue  
                                name: Queue  
                                identifier: Queue  
                                spec:  
                                    key: "123"  
                                    scope: Pipeline  
                                timeout: 10m  
                          - step:  
                                type: ShellScript  
                                name: Sleep  
                                identifier: Sleep  
                                spec:  
                                    shell: Bash  
                                    onDelegate: true  
                                    source:  
                                        type: Inline  
                                        spec:  
                                            script: sleep 30  
                                    environmentVariables: []  
                                    outputVariables: []  
                                    executionTarget: {}  
                                timeout: 10m  
              tags: {}
```

When you're done the Pipeline will look like this:

![](./static/control-resource-usage-with-queue-steps-04.png)

Open the **Queue** step.

You can see **Run next queued execution after completion of** is set to **Pipeline**. That means that the Pipeline must finish deploying before any other queued Pipeline executions can proceed.

Now let's run this Pipeline twice in a row quickly.

The first run of the Pipeline will run without queuing but the second run of the Pipeline is queued until the first one is complete.

Here's the first run of the Pipeline. It shows the Pipeline execution running (**Running**) and the other Pipeline execution queued.

![](./static/control-resource-usage-with-queue-steps-05.png)

You can click the name of the queued Pipeline to jump to its execution.

Here's the second run of the Pipeline:

![](./static/control-resource-usage-with-queue-steps-06.png)

You can see the Pipeline execution is queued (**Current**) and you the Pipeline execution that is running.

This example used multiple executions of the same Pipeline, but if a Queue step is added to another Pipeline and uses the same Resource Key, the same queuing process is applied to that Pipeline.

Here's another Pipeline, **queue 2**, with the same Queue step Resource Key. You can see it waiting for the **Queue** Pipeline to complete.

![](./static/control-resource-usage-with-queue-steps-07.png)
