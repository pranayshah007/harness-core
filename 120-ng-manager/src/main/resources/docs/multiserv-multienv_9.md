# Triggers and multiple Services

Triggers are applied at the pipeline level. If you have a Trigger that runs a pipeline when a Service's manifest or artifact changes, and that Service is part of a multi Service stage, the Trigger will initiate the deployment of all Services in that pipeline.

The Trigger runs the entire pipeline, not just the Service with the manifest or artifact that initiated the Trigger.
