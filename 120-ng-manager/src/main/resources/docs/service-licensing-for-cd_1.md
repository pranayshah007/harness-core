 Active Services

Harness gets all Services that are part of any Pipeline execution (deployment) over the past **30 Days**. We call these a list of **Active Services**.

![](./static/service-licensing-for-cd-00.png)

When determining Active Services, the status of the deployments does not matter. A Service is considered Active even if it was part of any failed deployments.

This includes if the Step involving a Service was skipped during a Pipeline execution.

For example, suppose a Pipeline has 4 different steps, each corresponding to a different Service: service1, service2, service3, service4.

When the Pipeline is run, Step 2 failed and the Pipeline skipped execution of Steps involving service3 and service4.

Harness still counts service3 and service4 as **Active Services** since they were part of a Pipeline execution (independent of the Pipeline execution status).

Also, the number of instances deployed does not matter here. A Service is considered Active even if the corresponding deployment(s) does not create any instance.
