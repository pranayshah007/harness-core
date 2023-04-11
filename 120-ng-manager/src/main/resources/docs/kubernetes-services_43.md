### Permissions

The Github Personal Access Token (PAT) must have the `write:packages` and `read:packages` permissions.

Ensure that you enable **API access** in the Harness Github connector. In the Github connector YAML, the setting is `apiAccess`:

<details>
<summary>GitHub Packages connector YAML</summary>

```yaml
connector:
  name: GitHub Packages
  identifier: GitHub_Packages
  orgIdentifier: default
  projectIdentifier: CD_Docs
  type: Github
  spec:
    url: https://github.com/johndoe/myapp.git
    validationRepo: https://github.com/johndoe/test.git
    authentication:
      type: Http
      spec:
        type: UsernameToken
        spec:
          username: johndoe
          tokenRef: githubpackages
    apiAccess:
      type: Token
      spec:
        tokenRef: githubpackages
    delegateSelectors:
      - gcpdocplay
    executeOnDelegate: true
    type: Repo
```
</details>

You can use the same Harness secret that you used for user authentication.

</details>


