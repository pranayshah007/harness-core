### Version selection

If chart version is left blank, Harness fetches the latest chart the first deployment. Subsequently, Harness checks if the chart the is present in the location specified using this format:

`<basePath>/<repoName(encoded)>/<chartName>/latest/chartName/`
