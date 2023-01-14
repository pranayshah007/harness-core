/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.aws.util;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AwsCallTracker {
  private static final String S3_MARKER = "S3";
  private static final String CF_MARKER = "CF";
  private static final String CD_MARKER = "CD";
  private static final String ECS_MARKER = "ECS";
  private static final String ASG_MARKER = "ASG";
  private static final String LAMBDA_MARKER = "LAMBDA";
  private static final String EC2_MARKER = "EC2";
  private static final String ECR_MARKER = "ECR";
  private static final String ELB_MARKER = "ELB";
  private static final String IAM_MARKER = "IAM";
  private static final String R53_MARKER = "R53";
  private static final String SERVICE_DISCOVERY_MARKER = "SERVICE_DISCOVERY";
  private static final String APP_ASG_MARKER = "APP_ASG";
  private static final String CLOUD_WATCH_MARKER = "CLOUD_WATCH";
  private static final String CLASSIC_ELB_MARKER = "CLASSIC_ELB";

  private void trackAWSCall(String marker, String callType) {
    log.info("AWS CALL:[{}] {}", marker, callType);
  }

  public void trackS3Call(String callType) {
    trackAWSCall(S3_MARKER, callType);
  }

  public void trackClassicELBCall(String callType) {
    trackAWSCall(CLASSIC_ELB_MARKER, callType);
  }

  public void trackCloudWatchCall(String callType) {
    trackAWSCall(CLOUD_WATCH_MARKER, callType);
  }

  public void trackAPPASGCall(String callType) {
    trackAWSCall(APP_ASG_MARKER, callType);
  }

  public void trackECSCall(String callType) {
    trackAWSCall(ECS_MARKER, callType);
  }

  public void trackASGCall(String callType) {
    trackAWSCall(ASG_MARKER, callType);
  }

  public void trackCDCall(String callType) {
    trackAWSCall(CD_MARKER, callType);
  }

  public void trackLambdaCall(String callType) {
    trackAWSCall(LAMBDA_MARKER, callType);
  }

  public void trackEC2Call(String callType) {
    trackAWSCall(EC2_MARKER, callType);
  }

  public void trackECRCall(String callType) {
    trackAWSCall(ECR_MARKER, callType);
  }

  public void trackELBCall(String callType) {
    trackAWSCall(ELB_MARKER, callType);
  }

  public void trackIAMCall(String callType) {
    trackAWSCall(IAM_MARKER, callType);
  }

  public void trackR53Call(String callType) {
    trackAWSCall(R53_MARKER, callType);
  }

  public void trackSDSCall(String callType) {
    trackAWSCall(SERVICE_DISCOVERY_MARKER, callType);
  }

  public void trackCFCall(String callType) {
    trackAWSCall(CF_MARKER, callType);
  }
}
