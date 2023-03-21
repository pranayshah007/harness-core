/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.custom;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.deleteFileIfExists;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.comparator.BuildDetailsComparatorDescending;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.expression.DelegateExpressionEvaluator;
import io.harness.delegate.task.artifacts.mappers.CustomRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.shell.CustomArtifactScriptExecutionOnDelegateNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.eraro.Level;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.shell.ScriptType;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by shivam.
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
public class CustomArtifactService {
  @Inject private CustomArtifactScriptExecutionOnDelegateNG customArtifactScriptExecutionOnDelegateNG;
  @Inject private DelegateDecryptionService delegateDecryptionService;
  private static final String ARTIFACT_RESULT_PATH = "HARNESS_ARTIFACT_RESULT_PATH";

  public ArtifactTaskExecutionResponse getBuilds(
      CustomArtifactDelegateRequest attributesRequest, LogCallback executionLogCallback) {
    List<BuildDetails> buildDetails = getBuildDetails(attributesRequest, executionLogCallback);
    List<CustomArtifactDelegateResponse> customArtifactDelegateResponseList =
        buildDetails.stream()
            .sorted(new BuildDetailsComparatorDescending())
            .map(build -> CustomRequestResponseMapper.toCustomArtifactDelegateResponse(build, attributesRequest))
            .collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(customArtifactDelegateResponseList, buildDetails);
  }

  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(
      CustomArtifactDelegateRequest attributesRequest, LogCallback executionLogCallback) {
    List<BuildDetails> buildDetails = getBuildDetails(attributesRequest, executionLogCallback);
    if (filterVersion(buildDetails, attributesRequest) != null
        && EmptyPredicate.isNotEmpty(filterVersion(buildDetails, attributesRequest))) {
      CustomArtifactDelegateResponse customArtifactDelegateResponse =
          CustomRequestResponseMapper.toCustomArtifactDelegateResponse(
              filterVersion(buildDetails, attributesRequest).get(0), attributesRequest);
      return getSuccessTaskExecutionResponse(
          Collections.singletonList(customArtifactDelegateResponse), filterVersion(buildDetails, attributesRequest));
    } else {
      log.error("Artifact Version Not found");
      throw new InvalidArtifactServerException("Artifact version not found", Level.INFO, USER);
    }
  }

  private List<BuildDetails> getBuildDetails(
      CustomArtifactDelegateRequest attributesRequest, LogCallback executionLogCallback) {
    String script = resolveNgSecretExpression(attributesRequest);
    UUID uuid = UUID.randomUUID();
    String scriptOutputFilename = "harness-" + uuid + ".out";
    File workingDirectory = new File(attributesRequest.getWorkingDirectory());
    File scriptOutputFile = new File(workingDirectory, scriptOutputFilename);
    try {
      script = addEnvVariablesCollector(script, scriptOutputFile.getAbsolutePath());
      ShellScriptTaskParametersNG shellScriptTaskParametersNG =
          getShellScriptTaskParametersNG(script, attributesRequest);
      ShellScriptTaskResponseNG shellScriptTaskResponseNG = customArtifactScriptExecutionOnDelegateNG.executeOnDelegate(
          shellScriptTaskParametersNG, executionLogCallback);
      log.info("Script executed with response status :  {}", shellScriptTaskResponseNG.getStatus().name());
      executionLogCallback.saveExecutionLog(
          "Script executed with response status : " + shellScriptTaskResponseNG.getStatus().name(), LogLevel.INFO);
      if (shellScriptTaskResponseNG.getStatus().name().equals("SUCCESS")) {
        return customArtifactScriptExecutionOnDelegateNG.getBuildDetails(
            scriptOutputFile.getAbsolutePath(), attributesRequest, executionLogCallback);
      } else {
        String msg = "No Artifact found in " + ARTIFACT_RESULT_PATH;
        log.error(msg);
        throw new InvalidArtifactServerException(msg, Level.INFO, USER);
      }

    } finally {
      try {
        deleteFileIfExists(scriptOutputFile.getAbsolutePath());
      } catch (IOException e) {
        log.warn("Failed to delete file: {} ", scriptOutputFile.getAbsolutePath(), e);
      }
    }
  }

  private String resolveNgSecretExpression(CustomArtifactDelegateRequest attributesRequest) {
    String script = attributesRequest.getScript();
    if (isNotEmpty(attributesRequest.getSecretDetails()) && isNotEmpty(attributesRequest.getEncryptionConfigs())
        && isNotEmpty(String.valueOf(attributesRequest.getExpressionFunctorToken()))) {
      try {
        Map<String, EncryptionConfig> encryptionConfigs = attributesRequest.getEncryptionConfigs();
        Map<String, SecretDetail> secretDetails = attributesRequest.getSecretDetails();
        Map<EncryptionConfig, List<EncryptedRecord>> encryptionConfigListMap = new HashMap<>();
        secretDetails.forEach(
            (key, secretDetail)
                -> addToEncryptedConfigListMap(encryptionConfigListMap,
                    encryptionConfigs.get(secretDetail.getConfigUuid()), secretDetail.getEncryptedRecord()));

        Map<String, char[]> decryptedRecords = delegateDecryptionService.decrypt(encryptionConfigListMap);
        Map<String, char[]> secretUuidToValues = new HashMap<>();

        secretDetails.forEach((key, value) -> {
          char[] secretValue = decryptedRecords.get(value.getEncryptedRecord().getUuid());
          secretUuidToValues.put(key, secretValue);
        });

        DelegateExpressionEvaluator delegateExpressionEvaluator =
            new DelegateExpressionEvaluator(secretUuidToValues, attributesRequest.getExpressionFunctorToken());
        script = delegateExpressionEvaluator.substitute(script, new HashMap<>());
      } catch (Exception e) {
        log.warn("Failed to resolve the expression ", e);
        throw new ArtifactoryRegistryException("Failed to resolve the expression : " + e);
      }
    }
    return script;
  }

  private void addToEncryptedConfigListMap(Map<EncryptionConfig, List<EncryptedRecord>> encryptionConfigListMap,
      EncryptionConfig encryptionConfig, EncryptedRecord encryptedRecord) {
    if (encryptionConfigListMap.containsKey(encryptionConfig)) {
      encryptionConfigListMap.get(encryptionConfig).add(encryptedRecord);
    } else {
      List<EncryptedRecord> encryptedRecordList = new ArrayList<>();
      encryptedRecordList.add(encryptedRecord);
      encryptionConfigListMap.put(encryptionConfig, encryptedRecordList);
    }
  }

  private String addEnvVariablesCollector(String command, String scriptOutputFilePath) {
    StringBuilder wrapperCommand = new StringBuilder();
    wrapperCommand.append("export " + ARTIFACT_RESULT_PATH + "=" + scriptOutputFilePath + "\n" + command);
    return wrapperCommand.toString();
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<CustomArtifactDelegateResponse> responseList, List<BuildDetails> buildDetails) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .buildDetails(buildDetails)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  private ShellScriptTaskParametersNG getShellScriptTaskParametersNG(
      String script, CustomArtifactDelegateRequest attributesRequest) {
    return ShellScriptTaskParametersNG.builder()
        .script(script)
        .scriptType(ScriptType.BASH)
        .environmentVariables(attributesRequest.getInputs())
        .executionId(attributesRequest.getExecutionId())
        .workingDirectory(attributesRequest.getWorkingDirectory())
        .outputVars(new ArrayList<>())
        .executeOnDelegate(true)
        .build();
  }

  private List<BuildDetails> filterVersion(
      List<BuildDetails> buildDetails, CustomArtifactDelegateRequest attributesRequest) {
    return buildDetails.stream()
        .filter(build -> build.getNumber().equals(attributesRequest.getVersion()))
        .collect(Collectors.toList());
  }
}