# Runtime inputs and expressions in template settings

**Artifact Repository Type** is the only setting in the template that must be a fixed value. The remaining settings can be runtime inputs and expressions also. 

For information on runtime inputs and expressions, go to [Fixed values runtime inputs and expressions](https://developer.harness.io/docs/platform/references/runtime-inputs/).

Runtime inputs can be useful in artifact source templates because they let your team members select the repository, path, and tags to use when they run pipelines using artifact source templates.

Expressions can be useful in artifact source templates because you can use default Harness expressions, like `<+stage.name>`, and service variables, like `<+serviceVariables.myapp>`, in place of the connector name and/or image path. When the pipeline using the artifact source template runs, those expressions are resolved and their related connector and/or image path is selected.

