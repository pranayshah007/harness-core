# Add Queue Steps

1. In the stage **Execution**, determine where you want to queue deployments and click **Add Step**.
2. In **Flow Control**, click **Queue**.
3. Enter a name and timeout for the Queue step.
4. In **Resource Key**, enter a unique key. This is the same key you will add to the Queue steps in other Pipelines.
5. The **Resource Key** supports Fixed Values, Runtime Inputs, and Expressions. See [Fixed Values, Runtime Inputs, and Expressions](../../platform/20_References/runtime-inputs.md).
6. In **Run next queued execution after completion of**, select one of the following:
   + **Pipeline:** the entire Pipeline must complete before the queued Pipelines can deploy.
   + **Stage:** the current Stage must complete before the queued Pipelines can deploy.

Queue steps can be used on different Pipelines or even multiple executions of the same Pipeline.
