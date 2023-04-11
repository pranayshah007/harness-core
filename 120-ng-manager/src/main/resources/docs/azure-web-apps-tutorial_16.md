## Traffic Shift steps

The **Traffic Shift** step shifts network traffic from the production slot to the deployment slot specified in the **Slot Deployment** step.

**Traffic Shift steps are** **not** **cumulative.** If you set 25% in one and 25% in the next one, only 25% of traffic is routed.

1. Open the **Traffic Shift** step.
2. In **Traffic %**, enter a number (without the % character).
3. Click **Apply Changes**.

You can use multiple **Traffic Shift** steps to incrementally increase traffic. In-between each **Traffic Shift** step, you can add a health check and/or Approval step.
