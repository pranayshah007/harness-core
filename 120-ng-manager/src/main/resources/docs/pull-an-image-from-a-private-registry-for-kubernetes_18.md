# Notes

* When you are using a public repo, the `dockercfg: <+artifact.imagePullSecret>` in values.yaml is ignored by Harness. You do not need to remove it.
* If you want to use a private repo and no imagePullSecret, then set `dockercfg` to empty in values.yaml.
