## Infrastructure definition

Harness has taken the infrastructure definition that was originally defined in the pipeline and moved it to the environment. For more information, go to [ Services and environments overview](../cd-concepts/services-and-environments-overview).

Here are the changes to infrastructure definition:
 
- The infrastructure definition can be associated with one environment only.
- The infrastructure definition is required to run a pipeline execution. 
- Users now need to pick an environment and infrastructure definition.

The infrastructure definition configuration now contains:

- **Name**, **Description**, **Tag**, **Deployment Type**.
- Connector details.
- Deployment target details.
