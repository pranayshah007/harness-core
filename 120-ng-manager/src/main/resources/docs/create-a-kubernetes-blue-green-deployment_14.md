## Wait for Steady State

The Wait for Steady State section shows Harness confirming the rollout and that the pods have reached steady state.

Next, the **Swap Primary with Stage** step will swap the primary and stage services to route primary network traffic to the pod set for the app.

If this were the second deployment, Harness would also swap the stage service to the pod set for the old app version.
