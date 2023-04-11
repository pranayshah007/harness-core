# Option: add secrets for a script

Typically, your script to pull the remote package will use a user account. For example:


```bash
curl -sSf -u "johndoe:mypwd" -O 'https://mycompany.jfrog.io/module/example/manifest.zip'
```
You can use Harness secrets for the username and password in your script. For example:


```bash
curl -sSf -u "<+secrets.getValue("username")>:<+secrets.getValue("password")>" -O 'https://mycompany.jfrog.io/module/example/manifest.zip'
```
For more information, see [Add and Reference Text Secrets](../../../platform/6_Security/2-add-use-text-secrets.md).

