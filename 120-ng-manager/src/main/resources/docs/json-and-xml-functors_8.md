# YAML Pipeline Example

Here's the YAML for a Pipeline that demonstrates all of the functors.

Functors Pipeline YAML:

```yaml
pipeline:  
    name: Functors  
    identifier: Functors  
    projectIdentifier: CD_Quickstart  
    orgIdentifier: default  
    tags: {}  
    stages:  
        - stage:  
              name: Functors  
              identifier: Functors  
              description: ""  
              type: Deployment  
              spec:  
                  serviceConfig:  
                      serviceDefinition:  
                          type: Kubernetes  
                          spec:  
                              manifestOverrideSets: []  
                              manifests: []  
                              artifacts:  
                                  sidecars: []  
                      serviceRef: nginx  
                  infrastructure:  
                      environmentRef: quickstart  
                      infrastructureDefinition:  
                          type: KubernetesDirect  
                          spec:  
                              connectorRef: account.k8s_cluster  
                              namespace: default  
                              releaseName: docs  
                      allowSimultaneousDeployments: false  
                      infrastructureKey: ""  
                  execution:  
                      steps:  
                          - step:  
                                type: Http  
                                name: json.select  
                                identifier: jsonselect  
                                timeout: 40s  
                                spec:  
                                    url: https://raw.githubusercontent.com/wings-software/harness-docs/main/functors/select.json  
                                    method: GET  
                                    headers: []  
                                    outputVariables:  
                                        - name: book  
                                          value: <+json.select("data.attributes.version_pins.mvn-service://new-construction-api", httpResponseBody)>  
                                          type: String  
                          - step:  
                                type: ShellScript  
                                name: json select export  
                                identifier: json_select_export  
                                timeout: 40s  
                                spec:  
                                    shell: Bash  
                                    onDelegate: true  
                                    source:  
                                        type: Inline  
                                        spec:  
                                            script: echo <+pipeline.stages.Functors.spec.execution.steps.jsonselect.output.outputVariables.book>  
                                    environmentVariables: []  
                                    outputVariables: []  
                                    executionTarget: {}  
                          - step:  
                                type: Http  
                                name: json.object  
                                identifier: jsonobject  
                                timeout: 40s  
                                spec:  
                                    url: https://raw.githubusercontent.com/wings-software/harness-docs/main/functors/object.json  
                                    method: GET  
                                    headers: []  
                                    outputVariables:  
                                        - name: item  
                                          value: <+json.object(httpResponseBody).item>  
                                          type: String  
                          - step:  
                                type: Http  
                                name: json.list  
                                identifier: jsonlist  
                                timeout: 40s  
                                spec:  
                                    url: https://raw.githubusercontent.com/wings-software/harness-docs/main/functors/books.json  
                                    method: GET  
                                    headers: []  
                                    outputVariables:  
                                        - name: list  
                                          value: <+json.list("books", httpResponseBody).get(2).pages>  
                                          type: String  
                          - step:  
                                type: Http  
                                name: jsonformat1  
                                identifier: jsonformat1  
                                timeout: 10s  
                                spec:  
                                    url: https://raw.githubusercontent.com/wings-software/harness-docs/main/functors/select.json  
                                    method: GET  
                                    headers: []  
                                    outputVariables: []  
                          - step:  
                                type: ShellScript  
                                name: jsonformatecho  
                                identifier: jsonformatecho  
                                timeout: 10m  
                                spec:  
                                    shell: Bash  
                                    onDelegate: true  
                                    source:  
                                        type: Inline  
                                        spec:  
                                            script: echo <+json.format(<+pipeline.stages.Functors.spec.execution.steps.jsonformat1.output.httpResponseBody>)>  
                                    environmentVariables: []  
                                    outputVariables: []  
                                    executionTarget: {}  
                          - step:  
                                type: Http  
                                name: XML select  
                                identifier: XML_select  
                                timeout: 10s  
                                spec:  
                                    url: https://raw.githubusercontent.com/wings-software/harness-docs/main/functors/books.xml  
                                    method: GET  
                                    headers: []  
                                    outputVariables:  
                                        - name: select  
                                          value: <+xml.select("/bookstore/book[1]/title", httpResponseBody)>  
                                          type: String  
                          - step:  
                                type: ShellScript  
                                name: echo XML select  
                                identifier: echo_XML_select  
                                timeout: 10m  
                                spec:  
                                    shell: Bash  
                                    onDelegate: true  
                                    source:  
                                        type: Inline  
                                        spec:  
                                            script: echo <+pipeline.stages.Functors.spec.execution.steps.XML_select.output.outputVariables.select>  
                                    environmentVariables: []  
                                    outputVariables: []  
                                    executionTarget: {}  
                      rollbackSteps: []  
              tags: {}  
              failureStrategies:  
                  - onFailure:  
                        errors:  
                            - AnyOther  
                        action:  
                            type: StageRollback  
              when:  
                  pipelineStatus: Success  

```
