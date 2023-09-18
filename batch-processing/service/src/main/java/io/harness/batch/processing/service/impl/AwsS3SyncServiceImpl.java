/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import io.harness.batch.processing.ccm.S3SyncRecord;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.service.intfc.AwsS3SyncService;

import software.wings.security.authentication.AwsS3SyncConfig;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

/**
 * Performs aws s3 sync.
 */
@Slf4j
public class AwsS3SyncServiceImpl implements AwsS3SyncService {
  @Inject BatchMainConfig configuration;

  private static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
  private static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
  private static final String AWS_DEFAULT_REGION = "AWS_DEFAULT_REGION";
  private static final String SESSION_TOKEN = "AWS_SESSION_TOKEN";

  @Override
  @SuppressWarnings("PMD")
  public boolean syncBuckets(S3SyncRecord s3SyncRecord) {
    AwsS3SyncConfig awsCredentials = configuration.getAwsS3SyncConfig();

    ImmutableMap<String, String> envVariables = ImmutableMap.of(AWS_ACCESS_KEY_ID, awsCredentials.getAwsAccessKey(),
        AWS_SECRET_ACCESS_KEY, awsCredentials.getAwsSecretKey(), AWS_DEFAULT_REGION, awsCredentials.getRegion());
    String destinationBucketPath = null;
    try {
      final ArrayList<String> assumeRoleCmd = Lists.newArrayList("aws", "sts", "assume-role", "--role-arn",
          s3SyncRecord.getRoleArn(), String.format("--role-session-name=%s", s3SyncRecord.getAccountId()),
          "--external-id", s3SyncRecord.getExternalId());
      if (configuration.getCeProxyConfig() != null && configuration.getCeProxyConfig().isEnabled()) {
        assumeRoleCmd.addAll(
            Lists.newArrayList("--endpoint-url", configuration.getCeAwsServiceEndpointConfig().getStsEndPointUrl()));
      }
      log.info("Running the assume-role command '{}'...", String.join(" ", assumeRoleCmd));
      ProcessResult processResult =
          getProcessExecutor().command(assumeRoleCmd).environment(envVariables).readOutput(true).execute();
      JsonObject credentials =
          new Gson().fromJson(processResult.getOutput().getString(), JsonObject.class).getAsJsonObject("Credentials");
      ImmutableMap<String, String> roleEnvVariables =
          ImmutableMap.of(AWS_ACCESS_KEY_ID, credentials.get("AccessKeyId").getAsString(), AWS_SECRET_ACCESS_KEY,
              credentials.get("SecretAccessKey").getAsString(), AWS_DEFAULT_REGION, awsCredentials.getRegion(),
              SESSION_TOKEN, credentials.get("SessionToken").getAsString());

      JsonObject assumedRoleUser = new Gson()
                                       .fromJson(processResult.getOutput().getString(), JsonObject.class)
                                       .getAsJsonObject("AssumedRoleUser");

      String destinationBucket = s3SyncRecord.getDestinationBucket() != null ? s3SyncRecord.getDestinationBucket()
                                                                             : awsCredentials.getAwsS3BucketName();

      destinationBucketPath =
          String.join("/", "s3://" + destinationBucket, assumedRoleUser.get("AssumedRoleId").getAsString(),
              s3SyncRecord.getSettingId(), s3SyncRecord.getCurReportName());

      final ArrayList<String> cmd =
          Lists.newArrayList("aws", "s3", "sync", s3SyncRecord.getBillingBucketPath(), destinationBucketPath,
              "--source-region", s3SyncRecord.getBillingBucketRegion(), "--acl", "bucket-owner-full-control");
      if (configuration.getCeProxyConfig() != null && configuration.getCeProxyConfig().isEnabled()) {
        cmd.addAll(
            Lists.newArrayList("--endpoint-url", configuration.getCeAwsServiceEndpointConfig().getS3EndPointUrl()));
      }
      trySyncBucket(cmd, roleEnvVariables);
    } catch (InvalidExitValueException e) {
      log.error("output: {}", e.getResult().outputUTF8());
      return false;
    } catch (IOException | TimeoutException | JsonSyntaxException e) {
      log.error("An error has occured in sync", e);
      return false;
    } catch (InterruptedException e) {
      log.error(e.getMessage(), e);
      Thread.currentThread().interrupt();
      return false;
    }
    return true;
  }

  public void trySyncBucket(ArrayList<String> cmd, ImmutableMap<String, String> roleEnvVariables)
      throws InterruptedException, TimeoutException, IOException {
    log.info("Running the s3 sync command '{}'...", String.join(" ", cmd));
    getProcessExecutor()
        .command(cmd)
        .environment(roleEnvVariables)
        .timeout(configuration.getAwsS3SyncConfig().getAwsS3SyncTimeoutMinutes(), TimeUnit.MINUTES)
        .redirectOutput(Slf4jStream.of(log).asInfo())
        .exitValue(0) // Throws exception when a non zero return code is found
        .readOutput(true)
        .execute();
    log.info("s3 sync completed");
  }

  ProcessExecutor getProcessExecutor() {
    return new ProcessExecutor();
  }
}
