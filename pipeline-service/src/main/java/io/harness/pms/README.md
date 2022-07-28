# Plan creation

Plan creation converts pipeline YAML into an execution plan.

## Services involved

- Pipeline Mini Service or PMS
- Other domain specific services like CD, CI, CV, CF
- PMS acts as an orchestrator for all the other services

## Plan creation

PMS receives a request to execute a pipeline YAML.

### PMS

Here's what the flow looks like in the PMS:

1. As a first step, PMS does some pre-processing of the YAML. It assigns a UUID
   to every YAML field of type object. If any service in the cluster needs to
   refer to another step for their adviser, it can directly find the node id
   without asking the PMS to do some orchestration.

2. PMS maintains a bucket of dependencies (YAML blobs, and some metadata) that
   need to be resolved for the pipeline creation process to complete. It also
   stores other things like plan nodes parsed till now and the starting node.
   
   ```java
   class PlanCreationResponse {
     Map<String, PlanNode> nodes;
     Map<String, YamlField> dependencies;
     String startingNodeId;
   }
   ```

3. PMS initializes the dependencies bucket with one blob - the whole pipeline
   field.

4. PMS sends all the dependencies to all the different services in the cluster.

5. Each service responds with the new nodes it was able to parse, any new
   dependencies that the parsed nodes require, and a starting node if it was
   able to find a node where the pipeline would start - typically this is only
   returned by the service parsing the whole pipeline blob.

6. PMS stores all the new nodes found.

   It checks if any dependency out of the bucket is not in the parsed node
   list. If there is, PMS throws an error saying there is an invalid YAML node.

   If multiple services return the same node, PMS chooses the service having
   the latest version. This information is part of the returned nodes.

7. PMS also checks if a starting node has been found, and if yes stores it.
   Note that at any point 2 different starting nodes should not be returned. If
   they are, PMS throws an error.

8. PMS also collects all the new dependencies that the services returned.

9. This forms the new bucket of dependencies.

10. If the new bucket is not empty, PMS goes back to step 4 with this new
    bucket.

11. If the bucket is empty, it means PMS has been able to parse the whole
    pipeline, and it can stop, returning the list of nodes and the starting
    node. If the starting node has not yet been set, PMS throws an error
    because it parsed all the nodes, but it couldn't figure out where to start.

### Domain specific services

Here's what the flow looks like in one of the other domain specific services:

1. Service receives a bucket of dependencies from PMS.

2. Service maintains a bucket of dependencies, parsed nodes and starting node
   similar to PMS.
   
   ```java
   class PlanCreationResponse {  
     Map<String, PlanNode> nodes;
     Map<String, YamlField> dependencies;
     String startingNodeId;
   }
   ```

3. Service contains a set of partial plan creators that know how to parse
   certain types of YAML nodes.

4. Service maintains a separate bucket for the dependencies it needs to parse.
   Let's call it the working bucket. It initializes this bucket with
   dependencies received from PMS.

5. Service tries to parse all the working dependencies using its partial plan
   creators in parallel.

7. Service creates a new working bucket of dependencies.

6. For each dependency, the service receives the new nodes that the partial
   plan creator corresponding to it was able to parse, any new dependencies
   that the parsed nodes require, and a starting node if it was able to find a
   node where the pipeline would start - typically this is only returned by the
   service parsing the whole pipeline blob. If no partial plan creator was able
   to parse a dependency, service receives a null response.

7. Service processes all the responses for the dependencies.

8. If there is a null response, it is added to the overall dependencies bucket
   in the final response - not the working bucket.

9. Otherwise, service stores all the new nodes found.

   Service also checks if a starting node has been found, and if yes stores it.
   Note that at any point 2 different starting nodes should not be returned. If
   they are, service throws an error.

   Service also puts all the new dependencies that the partial plan creator
   returned into the new working dependencies bucket.

10. If the new bucket working bucket is not empty after processing all the
    responses, service goes back to step 5 with this new working bucket.

11. If the bucket is empty, it means service has parsed all the dependencies it
    could, returning the list of nodes, any new dependencies found, and the
    starting node if found.

13. Service removes any dependencies that were in the initial list of
    dependencies received from the PMS because PMS already knows about them.

14. Finally, service sends the overall response back to the PMS.

## Example

Consider this YAML:

```yaml
pipeline:
  identifier: p1
  name: pipeline1
  stages:
    - stage:
        identifier: harnessbuild
        type: deployment
        name: managerDeployment
        spec:
          service: ...
          infrastructure: ...
          execution:
            steps:
              - step:
                  identifier: managerCanary
                  type: k8sCanary
                  spec:
                    field11: value1
                    field12: value2
              - step:
                  identifier: managerAppdVerify
                  type: appdVerify
                  spec: ...
              - step:
                  identifier: managerRolling
                  type: k8sRolling
                  spec: ...
    - stage:
        identifier: managerVerify
        type: verify
        name: managerVerify
        spec:
          verifySteps:
          - step:
              identifier: managerNewRelicVerify
              type: newRelicVerify
              spec: ...
```

- There are 3 services - PMS, CD service and CV service
- CD service knows about pipeline node, deployment stage node, k8sCanary step
  node and k8sRolling step node
- CV service knows about pipeline node, verify stage node, appdVerify step node
  and newRelicVerify step node

### PMS

Here's what the flow looks like in the PMS:

1. First is the pre-processing step. PMS generates a new YAML that looks
   something like:
   
   ```yaml
   pipeline:
     uuid: random1
     identifier: p1
     name: pipeline1
     stages:
       - stage:
           uuid: random2
           identifier: managerDeployment
           type: deployment
           name: managerDeployment
           spec:
             uuid: random3
             service: ...
             infrastructure: ...
             execution:
               uuid: random4
               steps:
                 - step:
                     uuid: random5
                     identifier: managerCanary
                     type: k8sCanary
                     spec:
                       field11: value1
                       field12: value2
                 - step:
                     uuid: random6
                     identifier: managerAppdVerify
                     type: appdVerify
                     spec: ...
                 - step:
                     uuid: random7
                     identifier: managerRolling
                     type: k8sRolling
                     spec: ...
       - stage:
           uuid: random8
           identifier: managerVerifyStage
           type: verify
           name: managerVerifyStage
           spec:
             uuid: random9
             verifySteps:
             - step:
                 uuid: random10
                 identifier: managerNewRelicVerify
                 type: newRelicVerify
                 spec: ...
   ```

2. Initial state:
   
   ```
   PMS ->
   nodes = empty
   dependencies = pipeline
   startingNodeId = empty
   ```

3. PMS sends all the dependencies to all the different service types in the
   cluster. These are the responses:
   
   ```
   CD service ->
   nodes = pipeline, stages, managerDeployment, service, infrastructure, steps,
           managerCanary, managerRolling
   dependencies = managerVerifyStage, managerAppdVerify
   startingNodeId = pipeline
   ```
   
   ```
   CV service ->
   nodes = pipeline, stages, managerVerifyStage, verifySteps,
           managerNewRelicVerify
   dependencies = managerDeployment
   startingNodeId = pipeline
   ```

4. After PMS collects all nodes, dependencies and starting nodes, the new state
   looks like:
   
   ```
   PMS ->
   nodes = pipeline, stages, managerDeployment, service, infrastructure, steps,
           managerCanary, managerRolling, managerVerifyStage, verifySteps,
           managerNewRelicVerify
   dependencies = managerAppdVerify
   startingNodeId = pipeline
   ```

5. PMS sends all the dependencies to all the different service types in the
   cluster. These are the responses:
   
   ```
   CD service ->
   nodes = empty
   dependencies = empty
   startingNodeId = empty
   ```
   
   ```
   CV service ->
   nodes = managerAppdVerify
   dependencies = empty
   startingNodeId = empty
   ```

6. After PMS collects all nodes, dependencies and starting nodes, the new state
   looks like:
   
   ```
   PMS ->
   nodes = pipeline, stages, managerDeployment, service, infrastructure, steps,
           managerCanary, managerRolling, managerVerifyStage, verifySteps,
           managerNewRelicVerify, managerAppdVerify
   dependencies = empty
   startingNodeId = pipeline
   ```

### CV service

Here's what the flow looks like in the CD service when the PMS sends pipeline
blob as dependency:

1. Service receives a bucket of dependencies.
   
   ```
   dependencies = pipeline
   ```

2. Initial state:
   
   ```
   workingDependencies = empty
   nodes = empty
   dependencies = empty
   startingNodeId = empty
   ```

3. Partial plan creators CD service knows about:
   
   ```
   planCreators = pipelinePlanCreator, deploymentStagePlanCreator,
                  k8sCanaryPlanCreator, k8sCanaryDeletePlanCreator,
                  k8sRollingPlanCreator
   ```

4. All the dependencies sent by PMS get put in a working bucket which the
   service maintains. New state:
   
   ```
   workingDependencies = pipeline
   nodes = empty
   dependencies = empty
   startingNodeId = empty
   ```

5. The working bucket is not empty, so service picks the `pipeline` blob from
   the bucket.

6. Service finds `pipelinePlanCreator` that can parse this blob.

7. Response from the partial plan creator:
   
   ```
   nodes = pipeline, stages
   dependencies = managerDeployment, managerDeployment
   startingNodeId = pipeline
   ```

8. New state for the service after processing the response:
   
   ```
   workingDependencies = managerDeployment, managerVerifyStage
   nodes = pipeline, stages
   dependencies = empty
   startingNodeId = pipeline
   ```

9. The working bucket is not empty, so pipeline picks the `managerDeployment`
   and `managerVerifyStage` blobs from the bucket.

10. Service finds `deploymentStagePlanCreator` that can parse the
    `managerDeployment` blob. No plan creator can parse the
    `managerVerifyStage` blob.

11. Response from the partial plan creators:
    
    ```
    managerDeployment ->
    nodes = deploymentStage, service, infrastructure, steps
    dependencies = managerCanary, managerAppdVerify, managerRolling
    startingNodeId = empty
    ```
    
    ```
    managerVerifyStage -> null
    ```

12. New state for the service after processing the response:
    
    ```
    workingDependencies = managerCanary, managerAppdVerify, managerRolling
    nodes = pipeline, stages, deploymentStage, service, infrastructure, steps
    dependencies = managerVerifyStage
    startingNodeId = pipeline
    ```

13. Service continues like this and is able to parse `managerCanary` and
    `managerRolling` but not `managerAppdVerify`.

14. Final state for the service:
    
    ```
    workingDependencies = empty
    nodes = pipeline, stages, deploymentStage, service, infrastructure, steps,
            managerCanary, managerRolling
    dependencies = managerVerifyStage, managerAppdVerify
    startingNodeId = pipeline
    ```

15. Response from the partial plan creator:
    
    ```
    nodes = deploymentStage, service, infrastructure, steps
    dependencies = managerCanary, managerAppdVerify, managerRolling
    startingNodeId = empty
    ```

16. Finally, service sends the overall response back to the PMS.

## Things to note

- Use schema to find out which YAML nodes can be parsed by which services and
  send dependencies accordingly.
