/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.customrepository;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ReportTarget.DELEGATE_LOG_SYSTEM;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.deleteFileIfExists;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.WingsException;
import io.harness.serializer.JsonUtils;
import io.harness.shell.ShellExecutionRequest;
import io.harness.shell.ShellExecutionResponse;
import io.harness.shell.ShellExecutionService;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.CustomRepositoryResponse;
import software.wings.helpers.ext.jenkins.CustomRepositoryResponse.CustomRepositoryResponseBuilder;
import software.wings.helpers.ext.jenkins.CustomRepositoryResponse.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jayway.jsonpath.DocumentContext;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class CustomRepositoryServiceImpl implements CustomRepositoryService {
  private static final String ARTIFACT_RESULT_PATH = "ARTIFACT_RESULT_PATH";

  @Inject private ShellExecutionService shellExecutionService;

  @Override
  public List<BuildDetails> getBuilds(ArtifactStreamAttributes artifactStreamAttributes) {
    if (artifactStreamAttributes.isCustomAttributeMappingNeeded()) {
      validateAttributeMapping(artifactStreamAttributes.getArtifactRoot(), artifactStreamAttributes.getBuildNoPath());
    }
    // Call Shell Executor with Request
    ShellExecutionRequest shellExecutionRequest =
        ShellExecutionRequest.builder()
            .workingDirectory(System.getProperty("java.io.tmpdir", "/tmp"))
            .scriptString(artifactStreamAttributes.getCustomArtifactStreamScript())
            .timeoutSeconds(artifactStreamAttributes.getCustomScriptTimeout() == null
                    ? 60
                    : Long.parseLong(artifactStreamAttributes.getCustomScriptTimeout()))
            .build();
    log.info("Retrieving build details of Custom Repository");
    ShellExecutionResponse shellExecutionResponse;
    try {
      shellExecutionResponse = shellExecutionService.execute(shellExecutionRequest);
    } catch (WingsException ex) {
      ex.excludeReportTarget(ErrorCode.SHELL_EXECUTION_EXCEPTION, EnumSet.of(DELEGATE_LOG_SYSTEM));
      throw ex;
    }
    List<BuildDetails> buildDetails = new ArrayList<>();
    // Get the output variables
    if (shellExecutionResponse.getExitValue() == 0) {
      Map<String, String> map = shellExecutionResponse.getShellExecutionData();
      // Read the file
      String artifactResultPath = map.get(ARTIFACT_RESULT_PATH);
      if (artifactResultPath == null) {
        log.info("ShellExecution did not return artifact result path");
        throw new InvalidArtifactServerException("ShellExecution did not return artifact result path", USER);
      }
      // Convert to Build details
      File file = new File(artifactResultPath);
      CustomRepositoryResponse customRepositoryResponse;
      try {
        if (EmptyPredicate.isNotEmpty(artifactStreamAttributes.getArtifactRoot())) {
          JsonNode jsonObject = (JsonNode) JsonUtils.readFromFile(file, JsonNode.class);
          String json = JsonUtils.asJson(jsonObject);
          customRepositoryResponse = mapToCustomRepositoryResponse(json, artifactStreamAttributes.getArtifactRoot(),
              artifactStreamAttributes.getBuildNoPath(), artifactStreamAttributes.getArtifactAttributes());
        } else {
          customRepositoryResponse =
              (CustomRepositoryResponse) JsonUtils.readFromFile(file, CustomRepositoryResponse.class);
        }

        List<Result> results = customRepositoryResponse.getResults();
        List<String> buildNumbers = new ArrayList<>();
        if (isNotEmpty(results)) {
          results.forEach(result -> {
            final String buildNo = result.getBuildNo();
            if (isNotEmpty(buildNo)) {
              if (buildNumbers.contains(buildNo)) {
                log.warn(
                    "There is an entry with buildNo {} already exists. So, skipping the result. Please ensure that buildNo is unique across the results",
                    buildNo);
                return;
              }
              buildDetails.add(aBuildDetails()
                                   .withNumber(buildNo)
                                   .withMetadata(result.getMetadata())
                                   .withUiDisplayName("Build# " + buildNo)
                                   .build());
              buildNumbers.add(buildNo);
            } else {
              log.warn("There is an object in output without mandatory build number");
            }
          });
        } else {
          log.warn("Results are empty");
        }
        log.info("Retrieving build details of Custom Repository success");
      } catch (Exception ex) {
        String msg =
            "Failed to transform results to the Custom Repository Response. Please verify if the script output is in the required format. Reason ["
            + ExceptionUtils.getMessage(ex) + "]";
        log.error(msg);
        throw new InvalidArtifactServerException(msg, Level.INFO, USER);
      } finally {
        // Finally delete the file
        try {
          deleteFileIfExists(file.getAbsolutePath());
        } catch (IOException e) {
          log.warn("Error occurred while deleting the file {}", file.getAbsolutePath());
        }
      }

    } else {
      throw new InvalidArtifactServerException("ShellExecution returned non-zero exit code...", USER);
    }

    return buildDetails;
  }

  private void validateAttributeMapping(String artifactRoot, String buildNoPath) {
    if (EmptyPredicate.isEmpty(artifactRoot)) {
      throw new InvalidArtifactServerException(
          "Artifacts Array Path cannot be null or empty. Please provide a valid value for Artifacts Array Path.", USER);
    }
    if (EmptyPredicate.isEmpty(buildNoPath)) {
      throw new InvalidArtifactServerException(
          "BuildNo Path cannot be null or empty. Please provide a valid value for BuildNo Path", USER);
    }
  }

  public CustomRepositoryResponse mapToCustomRepositoryResponse(
      String json, String artifactRoot, String buildNoPath, Map<String, String> map) {
    DocumentContext ctx = JsonUtils.parseJson(json);
    CustomRepositoryResponseBuilder customRepositoryResponse = CustomRepositoryResponse.builder();
    List<Result> result = new ArrayList<>();

    LinkedList<LinkedHashMap> children = JsonUtils.jsonPath(ctx, artifactRoot + "[*]");
    for (int i = 0; i < children.size(); i++) {
      Map<String, String> metadata = new HashMap<>();
      CustomRepositoryResponse.Result.ResultBuilder res = CustomRepositoryResponse.Result.builder();
      res.buildNo(JsonUtils.jsonPath(ctx, artifactRoot + "[" + i + "]." + buildNoPath));
      for (Entry<String, String> entry : map.entrySet()) {
        String mappedAttribute = EmptyPredicate.isEmpty(entry.getValue())
            ? entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1)
            : entry.getValue().substring(entry.getValue().lastIndexOf('.') + 1);
        String value = JsonUtils.jsonPath(ctx, artifactRoot + "[" + i + "]." + entry.getKey()).toString();
        metadata.put(mappedAttribute, value);
      }
      res.metadata(metadata);
      result.add(res.build());
    }
    customRepositoryResponse.results(result);
    return customRepositoryResponse.build();
  }

  @Override
  public boolean validateArtifactSource(ArtifactStreamAttributes artifactStreamAttributes) {
    List<BuildDetails> buildDetails = getBuilds(artifactStreamAttributes);
    if (isEmpty(buildDetails)) {
      throw new InvalidArtifactServerException(
          "Script execution was successful. However, no artifacts were found matching the criteria provided in script.",
          USER);
    }
    return true;
  }
}
