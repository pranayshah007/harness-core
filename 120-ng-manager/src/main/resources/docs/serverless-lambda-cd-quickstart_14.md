# Deploy and review

1. Save your Pipeline and then click **Run**, and then **Run Pipeline**. The Pipeline executes.
2. In the **Serverless AWS Lambda Deploy** step, click **Input** to see the deployment inputs:

   ![](./static/serverless-lambda-cd-quickstart-124.png)

3. Click **Output** to see what's deployed:
   
   ![](./static/serverless-lambda-cd-quickstart-125.png)
4. Click **Details** or **Console View** to see the logs.

In the logs you can see the successful deployment.

```
Deploying..  
  
Serverless Deployment Starting..  
serverless deploy --stage dev --region us-east-1  
Serverless: Deprecation warning: Support for Node.js versions below v12 will be dropped with next major release. Please upgrade at https://nodejs.org/en/  
            More Info: https://www.serverless.com/framework/docs/deprecations/#OUTDATED_NODEJS  
Serverless: Deprecation warning: Resolution of lambda version hashes was improved with better algorithm, which will be used in next major release.  
            Switch to it now by setting "provider.lambdaHashingVersion" to "20201221"  
            More Info: https://www.serverless.com/framework/docs/deprecations/#LAMBDA_HASHING_VERSION_V2  
Serverless: Packaging service...  
Serverless: Uploading CloudFormation file to S3...  
Serverless: Uploading artifacts...  
Serverless: Uploading service artifactFile file to S3 (721 B)...  
Serverless: Validating template...  
Serverless: Updating Stack...  
Serverless: Checking Stack update progress...  
.........  
Serverless: Stack update finished...  
Service Information  
service: myfunction  
stage: dev  
region: us-east-1  
stack: myfunction-dev  
resources: 11  
api keys:  
  None  
endpoints:  
  GET - https://85h6zffizc.execute-api.us-east-1.amazonaws.com/tello  
functions:  
  hello: myfunction-dev-hello  
layers:  
  None  
  
Deployment completed successfully.
```

Congratulations! You have successfully deployed a function using Serverless Lambda and Harness.
