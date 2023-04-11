## Script

Enter a script to fetch a JSON payload and add it to the Harness variable `$HARNESS_ARTIFACT_RESULT_PATH`. Here's an example:

```
curl -X GET "https://nexus3.dev.harness.io/service/rest/v1/components?repository=cdp-qa-automation-1" -H "accept: application/json" > $HARNESS_ARTIFACT_RESULT_PATH
```

The shell script you enter will query the Custom Artifact repository and output the JSON payload to a file on the Harness Delegate host using the environment variable `HARNESS_ARTIFACT_RESULT_PATH`, initialized by Harness. 

`HARNESS_ARTIFACT_RESULT_PATH` is a random, unique file path created on the Delegate by Harness.

You can use [Harness text secrets](../../../platform/6_Security/2-add-use-text-secrets.md) in the script. For example:

```
curl -u 'harness' <+secrets.getValue("repo_password")> https://myrepo.example.io/todolist/json/ > $HARNESS_ARTIFACT_RESULT_PATH
```

You must delete the Artifact Source and re-add it to re-collect the artifacts if the Artifact Source or its script information has been changed.
