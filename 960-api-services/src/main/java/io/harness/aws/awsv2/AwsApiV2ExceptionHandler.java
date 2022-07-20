package io.harness.aws.awsv2;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;

public class AwsApiV2ExceptionHandler {
  public void handleAwsServiceException(AwsServiceException awsServiceException) {}

  public void handleSdkException(SdkException sdkException) {}

  public void handleException(Exception exception) {}
}
