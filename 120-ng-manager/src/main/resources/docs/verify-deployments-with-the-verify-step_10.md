# Step 6: Select Sensitivity

In **Sensitivity**, you select the risk level that will be used as failure criteria during the deployment. When the criteria are met, the Failure Strategy for the stage or step is executed.

For time-series analysis (APM), the risk level is determined using standard deviations, as follows: 5𝞼 ([sigma](https://watchmaker.uncommons.org/manual/ch03s05.html)) represents high risk, 4𝞼 represents medium risk, and 3𝞼 or below represents low risk.

Harness also takes into account the number of points that deviated: 50%+ is **High**, 25%-50% is **Medium**, and 25% or below is **Low**.

Every successful deployment contributes to creating and shaping a healthy baseline that tells Harness what a successful deployment looks like, and what should be flagged as a risk. If a deployment failed due to verification, Harness will not consider any of the metrics produced by that deployment as part of the baseline.
