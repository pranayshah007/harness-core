## Rollback timestamps

In a Serverless CLI rollback (`serverless rollback --timestamp timestamp`), you'd have to manually identify and select the timestamp of the last successful deployment. This can be difficult because you need to know which timestamp to use. With multiple developers deploying, there's the possibility of rolling back to the wrong version.

Harness avoids this issue by automatically identifying the last successful deployment using its timestamp. During the event of a rollback, Harness will automatically rollback to that deployment.

You can see the timestamps in the deployment logs:

```
Serverless: Listing deployments:  
Serverless: -------------  
Serverless: Timestamp: 1653065606430  
Serverless: Datetime: 2022-05-20T16:53:26.430Z  
Serverless: Files:  
Serverless: - compiled-cloudformation-template.json  
Serverless: - myfunction.zip  
Serverless: -------------  
Serverless: Timestamp: 1653344285842  
Serverless: Datetime: 2022-05-23T22:18:05.842Z  
Serverless: Files:  
Serverless: - artifactFile  
Serverless: - compiled-cloudformation-template.json  
Serverless: -------------  
Serverless: Timestamp: 1653415240343  
Serverless: Datetime: 2022-05-24T18:00:40.343Z  
Serverless: Files:  
Serverless: - artifactFile  
Serverless: - compiled-cloudformation-template.json
```

If there is no change in code, Serverless doesn't deploy anything new. In the logs you'll see `Serverless: Service files not changed. Skipping deployment...`.

While this is somewhat similar to how rollback is performed in Serverless CLI, Harness performs rollback automatically and always uses the timestamp of the last successful deployment.

During a Harness rollback, you can see the timestamp used to rollback to the last successful deployment (`rollback --timestamp 1653415240343 --region us-east-1 --stage dev`):

```
Rollback..  
  
Serverless Rollback Starting..  
serverless rollback --timestamp 1653415240343 --region us-east-1 --stage dev  
Serverless: Deprecation warning: Support for Node.js versions below v12 will be dropped with next major release. Please upgrade at https://nodejs.org/en/  
            More Info: https://www.serverless.com/framework/docs/deprecations/#OUTDATED_NODEJS  
Serverless: Deprecation warning: Resolution of lambda version hashes was improved with better algorithm, which will be used in next major release.  
            Switch to it now by setting "provider.lambdaHashingVersion" to "20201221"  
            More Info: https://www.serverless.com/framework/docs/deprecations/#LAMBDA_HASHING_VERSION_V2  
Serverless: Updating Stack...  
  
Rollback completed successfully.
```
