/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.cloudwatch;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
/**
 * Created by rsingh on 3/30/18.
 */
@OwnedBy(CDP)
public enum AwsNameSpace {
  APIGATEWAY("AWS/ApiGateway", false),
  APPSTREAM("AWS/AppStream", false),
  AUTOSCALING("AWS/AutoScaling", false),
  BILLING("AWS/Billing", false),
  CLOUDFRONT("AWS/CloudFront", false),
  CLOUDSEARCH("AWS/CloudSearch", false),
  EVENTS("AWS/Events", false),
  LOGS("AWS/Logs", false),
  CONNECT("AWS/Connect", false),
  DMS("AWS/DMS", false),
  DX("AWS/DX", false),
  DYNAMODB("AWS/DynamoDB", false),
  EC2("AWS/EC2", true),
  EC2SPOT("AWS/EC2Spot", false),
  ECS("AWS/ECS", false),
  ELASTICBEANSTALK("AWS/ElasticBeanstalk", false),
  EBS("AWS/EBS", false),
  EFS("AWS/EFS", false),
  ELB("AWS/ELB", false),
  APPLICATIONELB("AWS/ApplicationELB", false),
  NETWORKELB("AWS/NetworkELB", false),
  ELASTICTRANSCODER("AWS/ElasticTranscoder", false),
  ELASTICACHE("AWS/ElastiCache", false),
  ES("AWS/ES", false),
  ELASTICMAPREDUCE("AWS/ElasticMapReduce", false),
  GAMELIFT("AWS/GameLift", false),
  INSPECTOR("AWS/Inspector", false),
  IOT("AWS/IoT", false),
  KMS("AWS/KMS", false),
  KINESISANALYTICS("AWS/KinesisAnalytics", false),
  FIREHOSE("AWS/Firehose", false),
  KINESIS("AWS/Kinesis", false),
  KINESISVIDEO("AWS/KinesisVideo", false),
  LAMBDA("AWS/Lambda", false),
  LEX("AWS/Lex", false),
  ML("AWS/ML", false),
  OPSWORKS("AWS/OpsWorks", false),
  POLLY("AWS/Polly", false),
  REDSHIFT("AWS/Redshift", false),
  RDS("AWS/RDS", false),
  ROUTE53("AWS/Route53", false),
  SAGEMAKER("AWS/SageMaker", false),
  DDOSPROTECTION("AWS/DDoSProtection", false),
  SES("AWS/SES", false),
  SNS("AWS/SNS", false),
  SQS("AWS/SQS", false),
  S3("AWS/S3", false),
  SWF("AWS/SWF", false),
  STATES("AWS/States", false),
  STORAGEGATEWAY("AWS/StorageGateway", false),
  NATGATEWAY("AWS/NATGateway", false),
  VPN("AWS/VPN", false),
  WAF("WAF", false),
  WORKSPACES("AWS/WorkSpaces", false);

  private final String nameSpace;
  private final boolean isHostMetric;

  AwsNameSpace(String nameSpace, boolean isHostMetric) {
    this.nameSpace = nameSpace;
    this.isHostMetric = isHostMetric;
  }

  public String getNameSpace() {
    return nameSpace;
  }

  public boolean isHostMetric() {
    return isHostMetric;
  }
}
