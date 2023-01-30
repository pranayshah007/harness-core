/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import io.harness.aws.AwsClientImpl;
import io.harness.ccm.commons.beans.billing.CEBucketPolicyJson;
import io.harness.ccm.commons.beans.billing.CEBucketPolicyStatement;
import io.harness.ccm.service.intf.AWSBucketPolicyHelperService;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;

import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketPolicy;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AWSBucketPolicyHelperServiceImpl implements AWSBucketPolicyHelperService {
  @Inject AwsClientImpl awsClient;
  private static final String rolePrefix = "arn:aws:iam";
  private static final String aws = "AWS";

  @Override
  public boolean updateBucketPolicy(
      String crossAccountRoleArn, String awsS3Bucket, String awsAccessKey, String awsSecretKey) {
    AWSCredentialsProvider credentialsProvider =
        awsClient.constructStaticBasicAwsCredentials(awsAccessKey, awsSecretKey);
    try (CloseableAmazonWebServiceClient<AmazonS3Client> closeableAmazonS3Client =
             new CloseableAmazonWebServiceClient(awsClient.getAmazonS3Client(credentialsProvider))) {
      BucketPolicy bucketPolicy = closeableAmazonS3Client.getClient().getBucketPolicy(awsS3Bucket);
      String policyText = bucketPolicy.getPolicyText();
      CEBucketPolicyJson policyJson = new Gson().fromJson(policyText, CEBucketPolicyJson.class);
      List<CEBucketPolicyStatement> listStatements = new ArrayList<>();
      for (CEBucketPolicyStatement statement : policyJson.getStatement()) {
        Map<String, List<String>> principal = statement.getPrincipal();
        List<String> rolesList = principal.get(aws);
        rolesList = rolesList.stream().filter(roleArn -> roleArn.contains(rolePrefix)).collect(Collectors.toList());
        if (rolesList.contains(crossAccountRoleArn)) {
          return true;
        }
        rolesList.add(crossAccountRoleArn);
        principal.put(aws, rolesList);
        statement.setPrincipal(principal);
        listStatements.add(statement);
      }
      policyJson = CEBucketPolicyJson.builder().Version(policyJson.getVersion()).Statement(listStatements).build();
      String updatedBucketPolicy = new Gson().toJson(policyJson);
      closeableAmazonS3Client.getClient().setBucketPolicy(awsS3Bucket, updatedBucketPolicy);
    } catch (Exception e) {
      log.error("Exception updateBucketPolicy", e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
    return true;
  }
}
