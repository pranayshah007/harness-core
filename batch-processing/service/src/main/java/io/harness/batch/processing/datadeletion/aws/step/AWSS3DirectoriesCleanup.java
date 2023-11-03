/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion.aws.step;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStep.AWS_S3_DIRECTORIES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;

import software.wings.security.authentication.AwsS3SyncConfig;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

@Slf4j
@Component
@OwnedBy(CE)
public class AWSS3DirectoriesCleanup {
  private static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
  private static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
  private static final String AWS_DEFAULT_REGION = "AWS_DEFAULT_REGION";

  @Autowired BatchMainConfig configuration;

  public boolean delete(String accountId, DataDeletionRecord dataDeletionRecord, boolean dryRun)
      throws IOException, TimeoutException, InterruptedException {
    List<String> buckets;
    AwsS3SyncConfig awsCredentials = configuration.getAwsS3SyncConfig();
    ImmutableMap<String, String> envVariables = ImmutableMap.of(AWS_ACCESS_KEY_ID, awsCredentials.getAwsAccessKey(),
        AWS_SECRET_ACCESS_KEY, awsCredentials.getAwsSecretKey(), AWS_DEFAULT_REGION, awsCredentials.getRegion());
    try {
      buckets = listBuckets(envVariables);
      log.info("Buckets to look into: {}", buckets);
    } catch (InvalidExitValueException e) {
      log.error("output: {}", e.getResult().outputUTF8());
      throw e;
    } catch (IOException | TimeoutException | JsonSyntaxException e) {
      log.error("An error has occurred in listing buckets ", e);
      throw e;
    } catch (InterruptedException e) {
      log.error(e.getMessage(), e);
      Thread.currentThread().interrupt();
      throw e;
    }
    List<String> directories;
    Long directoriesCount = 0L;
    // List directories in each bucket, and delete all directories belonging to the deleted account.
    for (String bucket : buckets) {
      try {
        directories = listDirectories(bucket, envVariables);
        for (String directory : directories) {
          if (directory.endsWith(accountId + "/")) {
            log.info("Deleting directory: {} in bucket: {}", directory, bucket);
            directoriesCount++;
            if (!dryRun) {
              deleteDirectory(bucket, directory, envVariables);
            }
          }
        }
      } catch (InvalidExitValueException e) {
        log.error("output: {}", e.getResult().outputUTF8());
        throw e;
      } catch (IOException | TimeoutException | JsonSyntaxException e) {
        log.error("An error has occurred in listing directories ", e);
        throw e;
      } catch (InterruptedException e) {
        log.error(e.getMessage(), e);
        Thread.currentThread().interrupt();
        throw e;
      }
    }
    dataDeletionRecord.getRecords().get(AWS_S3_DIRECTORIES.name()).setRecordsCount(directoriesCount);
    return true;
  }

  private List<String> listBuckets(ImmutableMap<String, String> envVariables)
      throws IOException, InterruptedException, TimeoutException {
    final ArrayList<String> listBucketsCmd = Lists.newArrayList("aws", "s3api", "list-buckets");
    log.info("Running the list buckets command '{}'...", String.join(" ", listBucketsCmd));
    ProcessResult processResult =
        getProcessExecutor()
            .command(listBucketsCmd)
            .environment(envVariables)
            .timeout(configuration.getAwsS3SyncConfig().getAwsS3SyncTimeoutMinutes(), TimeUnit.MINUTES)
            .redirectOutput(Slf4jStream.of(log).asInfo())
            .exitValue(0) // Throws exception when a non-zero return code is found
            .readOutput(true)
            .execute();
    JsonArray result =
        new Gson().fromJson(processResult.getOutput().getString(), JsonObject.class).getAsJsonArray("Buckets");
    List<String> buckets = new ArrayList<>();
    for (JsonElement element : result) {
      String bucketName = element.getAsJsonObject().get("Name").getAsString();
      if (bucketName.startsWith(configuration.getAwsS3SyncConfig().getAwsS3BucketName())) {
        buckets.add(bucketName);
      }
    }
    return buckets;
  }

  private List<String> listDirectories(String bucketName, ImmutableMap<String, String> envVariables)
      throws IOException, InterruptedException, TimeoutException {
    final ArrayList<String> listDirectoriesCmd = Lists.newArrayList("aws", "s3", "ls", bucketName);
    log.info("Running the list directories command '{}'...", String.join(" ", listDirectoriesCmd));
    ProcessResult processResult =
        getProcessExecutor()
            .command(listDirectoriesCmd)
            .environment(envVariables)
            .timeout(configuration.getAwsS3SyncConfig().getAwsS3SyncTimeoutMinutes(), TimeUnit.MINUTES)
            .redirectOutput(Slf4jStream.of(log).asInfo())
            .exitValue(0) // Throws exception when a non-zero return code is found
            .readOutput(true)
            .execute();
    String result = processResult.getOutput().getString();
    String[] directoriesArray = result.split("\n");
    return Arrays.stream(directoriesArray)
        .map((String directory) -> directory.strip().split(" ")[1])
        .collect(Collectors.toList());
  }

  private void deleteDirectory(String bucketName, String directoryName, ImmutableMap<String, String> envVariables)
      throws IOException, InterruptedException, TimeoutException {
    if (directoryName.isEmpty()) {
      return;
    }
    final ArrayList<String> deleteDirectoryCmd =
        Lists.newArrayList("aws", "s3", "rm", "--recursive", "s3://" + bucketName + "/" + directoryName);
    log.info("Running the delete directory command '{}'...", String.join(" ", deleteDirectoryCmd));
    ProcessResult processResult =
        getProcessExecutor()
            .command(deleteDirectoryCmd)
            .environment(envVariables)
            .timeout(configuration.getAwsS3SyncConfig().getAwsS3SyncTimeoutMinutes(), TimeUnit.MINUTES)
            .redirectOutput(Slf4jStream.of(log).asInfo())
            .exitValue(0) // Throws exception when a non-zero return code is found
            .readOutput(true)
            .execute();
    String result = processResult.getOutput().getString();
    log.info("Delete result: {}", result);
  }

  ProcessExecutor getProcessExecutor() {
    return new ProcessExecutor();
  }
}
