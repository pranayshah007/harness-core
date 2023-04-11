## Output variables

Output variables are outputs that are defined and captured after the Container step execution.

Output variables expose environment variables for use by other steps/stages of the pipeline. You may reference the output variable of a step using the step Id and the name of the variable in output variables.

Let's look at a simple example.

1. In the Command in a step, export a new variable using the following syntax:

    ```
    export myVar=varValue
    ```

    ![picture 1](static/beced40976dc4e9f479faaa2a93c1d8f6e7b7a7ed20ca412cc2afd1cf8f7c1a1.png)
2. In a later Shell Script step, reference the output variable:

    ```
    echo <+steps.S1.output.outputVariables.myVar>
    ```
    
    The syntax for referencing output variables between steps in the same stage looks similar to the example below.

    ```
    <+[stepId].output.outputVariables.[varName]>
    ```

    The syntax for referencing output variables between steps in different stages looks similar to the example below.

    ```
    <+stages.[stageID].execution.steps.[stepId].output.outputVariables.[varName]>
    ```
