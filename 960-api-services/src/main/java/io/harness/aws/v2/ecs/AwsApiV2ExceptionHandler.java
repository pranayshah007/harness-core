package io.harness.aws.v2.ecs;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;

public class AwsApiV2ExceptionHandler {
    public void handleAwsServiceException(AwsServiceException awsServiceException){
        throw awsServiceException;
    }

    public void handleSdkException(SdkException sdkException) {
        throw sdkException;
    }

    public void handleException(Exception exception) {
        throw new RuntimeException(exception);
    }
}
