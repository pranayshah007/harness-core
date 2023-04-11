# Notes

* [Harness Variables and Expressions](../../../platform/12_Variables-and-Expressions/harness-variables.md) may be added to values.yaml, not the manifests themselves. This provides more flexibility.
* The values.yaml file used in a stage Service doesn't support Helm templating, only Go templating. Helm templating is fully supported in the remote Helm charts you add to your Service.
* Harness uses Go template version 0.4. If you're used to Helm templates, you can download Go template and try it out locally to find out if your manifests will work. This can help you avoid issues when adding your manifests to Harness.  
- You can install Go template version 0.4 locally to test your manifests.
	+ Mac OS: curl -O https://app.harness.io/public/shared/tools/go-template/release/v0.4/bin/darwin/amd64/go-template
	+ Linux: curl -O https://app.harness.io/public/shared/tools/go-template/release/v0.4/bin/linux/amd64/go-template
	+ Windows: curl -O https://app.harness.io/public/shared/tools/go-template/release/v0.4/bin/windows/amd64/go-template
	+ For steps on doing local Go templating, see [Harness Local Go-Templating](https://community.harness.io/t/harness-local-go-templating/460) on Harness Community.
* Harness uses an internal build of Go templating. It cannot be upgraded. Harness uses [Spring templates functions](http://masterminds.github.io/sprig/), excluding those functions that provide access to the underlying OS (env, expandenv) for security reasons.  
In addition, Harness uses the functions ToYaml, FromYaml, ToJson, FromJson.

