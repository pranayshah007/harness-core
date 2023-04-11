## ApplicationSet Support

* Harness supports both JSON and YAML formats for ApplicationSets.
* Harness supports all ApplicationSet generators. You can add an ApplicationSet for any generator as an Application in Harness:
	+ [List Generator](https://argocd-applicationset.readthedocs.io/en/stable/Generators-List/)
	+ [Cluster Generator](https://argocd-applicationset.readthedocs.io/en/stable/Generators-Cluster/)
	+ [Git Generator](https://argocd-applicationset.readthedocs.io/en/stable/Generators-Git/)
	+ [Matrix Generator](https://argocd-applicationset.readthedocs.io/en/stable/Generators-Matrix/)
	+ [Merge Generator](https://argocd-applicationset.readthedocs.io/en/stable/Generators-Merge/)
	+ [SCM Provider Generator](https://argocd-applicationset.readthedocs.io/en/stable/Generators-SCM-Provider/)
	+ [Cluster Decision Resource Generator](https://argocd-applicationset.readthedocs.io/en/stable/Generators-Cluster-Decision-Resource/)
	+ [Pull Request Generator](https://argocd-applicationset.readthedocs.io/en/stable/Generators-Pull-Request/)
* [Git Generator](https://argocd-applicationset.readthedocs.io/en/stable/Generators-Git/) has first class support with the **Update Release Repo** and **Merge PR** steps in the Pipeline.
* All generators can be used in Shell Script steps. For example, you could create a Cluster generator YAML spec in a Shell Script step as a bash variable, and then use git commands in the script to update the ApplicationSet in your repo with the spec in the step. The updated repo spec will be used in the next Application sync (manual or automatic).
