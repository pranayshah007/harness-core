## Agent Took Too Long to Respond

If you see the error `the Agent took too long to respond` during installation of an Agent with an existing Argo CD instance, the Agent cannot connect to the Redis/repo server and needs additional `NetworkPolicy` settings.

Add the following `podSelector` settings to the `NetworkPolicy` objects defined in your existing Argo CD **argocd-redis** and **argocd-repo-server** services.

The following table lists the `NetworkPolicy` objects for HA and non-HA Agents, and include the YAML before and after the new `podSelector` is added.



| **NetworkPolicy** | **HA Agent** |
| --- | --- |
| `argocd-redis-ha-proxy-network-policy` | ![](static/argocd-redis-ha-proxy-network-policy.png)  |
| `argocd-repo-server-network-policy` | ![](static/argocd-repo-server-network-policy.png)  |
|  | **Non-HA Agent** |
| `argocd-redis-network-policy` | ![](static/argocd-redis-network-policy.png)  |
| `argocd-repo-server-network-policy` | ![](static/argocd-repo-server-network-policy-nonha.png)  |

 

