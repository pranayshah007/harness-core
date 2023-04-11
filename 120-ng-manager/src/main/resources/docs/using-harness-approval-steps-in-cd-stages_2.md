# Visual Summary

Here's a manual approval step during the execution of a Pipeline:

![](./static/using-harness-approval-steps-in-cd-stages-00.png)

An approver can approve/reject the step, stopping the Pipeline. The approver can also add comments and define variables for use by subsequent approvers and steps.

Here's a quick video that walks you through setting up and running the step:

<!-- Video:
https://www.youtube.com/watch?v=V-d6kaMBf-w-->
<docvideo src="https://www.youtube.com/watch?v=V-d6kaMBf-w" />


Here's what a manual approval step looks like in YAML:

```yaml
- step:  
      type: HarnessApproval  
      name: Harness Approval Step  
      identifier: Harness_Approval_Step  
      timeout: 1d  
      spec:  
          approvalMessage: Test  
          includePipelineExecutionHistory: true  
          approvers:  
              userGroups:  
                  - docs  
              minimumCount: 1  
              disallowPipelineExecutor: false  
          approverInputs:  
              - name: foo  
                defaultValue: bar
```