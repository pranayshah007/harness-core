# Notes

* Harness honors Argo CD project permissions. If the project selected for the Harness Application does not have permission for the repository or cluster, then Harness will return a permission error. You will need to go into Argo CD and adjust the projects **scoped repositories** and **destinations**.
* A non-BYOA setup does not support multiple Argo CD mappings to a single Harness Project. A non-BYOA setup is a setup where Harness installs Argo CD for you when you install a Harness GitOps Agent.
* If you need to uninstall a GitOps Agent, you can use `kubectl delete` with the same manifest you used to install it. For example, `kubectl delete -f gitops-agent.yml -n argocd`.

