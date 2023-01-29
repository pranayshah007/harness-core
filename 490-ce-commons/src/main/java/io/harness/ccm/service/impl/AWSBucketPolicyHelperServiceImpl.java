/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import io.harness.aws.AwsClientImpl;
import io.harness.aws.CloseableAmazonWebServiceClient;
import io.harness.ccm.commons.beans.billing.CEBucketPolicyJson;
import io.harness.ccm.commons.beans.billing.CEBucketPolicyStatement;
import io.harness.ccm.service.intf.AWSBucketPolicyHelperService;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.apache.commons.lang3.StringUtils;

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
      if (StringUtils.isEmpty(policyText)) {
        // It's a new bucket. Initialize bucket policy with default json
        policyText = initializeBucketPolicy(awsS3Bucket);
      }
      CEBucketPolicyJson policyJson;
      try {
        policyJson = new Gson().fromJson(policyText, CEBucketPolicyJson.class);
      } catch (Exception e) {
        log.info("Handled exception while updating bucket policy: ", e);
        JSONObject jsonObject = new JSONObject(policyText);
        List<String> awsPrincipalRoleList =
            List.of(jsonObject.getJSONArray("Statement").getJSONObject(0).getJSONObject("Principal").getString("AWS"));
        jsonObject.getJSONArray("Statement")
            .getJSONObject(0)
            .getJSONObject("Principal")
            .put("AWS", awsPrincipalRoleList);
        jsonObject.getJSONArray("Statement")
            .getJSONObject(1)
            .getJSONObject("Principal")
            .put("AWS", awsPrincipalRoleList);
        policyJson = new Gson().fromJson(jsonObject.toString(), CEBucketPolicyJson.class);
      }

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

  public String initializeBucketPolicy(String awsS3BucketName)
      throws JsonProcessingException {
    CEBucketPolicyJson ceBucketPolicyJson =
        CEBucketPolicyJson.builder()
            .Statement(List.of(CEBucketPolicyStatement.builder()
                                   .Sid("DelegateS3Access")
                                   .Effect("Allow")
                                   .Principal(Map.of("AWS", Collections.emptyList()))
                                   .Action(List.of("s3:PutObject", "s3:PutObjectAcl"))
                                   .Resource(List.of(String.format("arn:aws:s3:::%s/${aws:userid}", awsS3BucketName),
                                       String.format("arn:aws:s3:::%s/${aws:userid}/*", awsS3BucketName)))
                                   .build(),
                CEBucketPolicyStatement.builder()
                    .Sid("AllowStatement3")
                    .Effect("Allow")
                    .Principal(Map.of("AWS", Collections.emptyList()))
                    .Action("s3:ListBucket")
                    .Resource(String.format("arn:aws:s3:::%s", awsS3BucketName))
                    .build()))
            .Version("2012-10-17")
            .build();
    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    return ow.writeValueAsString(ceBucketPolicyJson);
  }
}
