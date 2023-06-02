/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.custom;

import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.task.shell.CustomArtifactScriptExecutionOnDelegateNG;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.delegate.task.shell.SshExecutorFactoryNG;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutionRequest;
import io.harness.shell.ShellExecutionResponse;
import io.harness.shell.ShellExecutionService;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CustomArtifactScriptExecutionOnDelegateNGTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks CustomArtifactScriptExecutionOnDelegateNG customArtifactScriptExecutionOnDelegateNG;
  @Mock SshExecutorFactoryNG sshExecutorFactoryNG;
  @Mock private ShellExecutionService shellExecutionService;
  @Mock ShellExecutorFactoryNG shellExecutorFactory;
  private static final String ARTIFACT_RESULT_PATH = "HARNESS_ARTIFACT_RESULT_PATH";

  private final LogCallback logCallback =
      new NGDelegateLogCallback(null, "Execute", false, CommandUnitsProgress.builder().build());

  private static final String JSON_RESPONSE =
      "{\"items\":[{\"id\":\"bWF2ZW4tcmVsZWFzZXM6MWM3ODdhMDNkYjgyMDllYjhjY2IyMDYwMTJhMWU0MmI\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"group\":\"mygroup\",\"name\":\"myartifact\",\"version\":\"1.0\",\"assets\":[{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war\",\"path\":\"mygroup/myartifact/1.0/myartifact-1.0.war\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6ZDQ4MTE3NTQxZGNiODllYzYxM2IyMzk3MzIwMWQ3YmE\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"da39a3ee5e6b4b0d3255bfef95601890afd80709\",\"md5\":\"d41d8cd98f00b204e9800998ecf8427e\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war.md5\",\"path\":\"mygroup/myartifact/1.0/myartifact-1.0.war.md5\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6MGFiODBhNzQzOTIxZTQyNjYxOWJlZjJiYmRhYTU5MWQ\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"67a74306b06d0c01624fe0d0249a570f4d093747\",\"md5\":\"74be16979710d4c4e7c6647856088456\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war.sha1\",\"path\":\"mygroup/myartifact/1.0/myartifact-1.0.war.sha1\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6MTNiMjllNDQ5ZjBlM2I4ZDM5OTY0ZWQzZTExMGUyZTM\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"10a34637ad661d98ba3344717656fcc76209c2f8\",\"md5\":\"0144712dd81be0c3d9724f5e56ce6685\"}}]},{\"id\":\"bWF2ZW4tcmVsZWFzZXM6ZGZiZWYwOWVmZTE2NDRlYTYzNTAwMWQ3MjVhYzgxMTY\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"group\":\"mygroup\",\"name\":\"myartifact\",\"version\":\"1.1\",\"assets\":[{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.1/myartifact-1.1.war\",\"path\":\"mygroup/myartifact/1.1/myartifact-1.1.war\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6MGFiODBhNzQzOTIxZTQyNmQ1ZThjYjBmNWY0ODYwODc\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"da39a3ee5e6b4b0d3255bfef95601890afd80709\",\"md5\":\"d41d8cd98f00b204e9800998ecf8427e\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.1/myartifact-1.1.war.md5\",\"path\":\"mygroup/myartifact/1.1/myartifact-1.1.war.md5\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6ZDQ4MTE3NTQxZGNiODllYzlhMzlhNjIzMGVkMzI2ZTY\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"67a74306b06d0c01624fe0d0249a570f4d093747\",\"md5\":\"74be16979710d4c4e7c6647856088456\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.1/myartifact-1.1.war.sha1\",\"path\":\"mygroup/myartifact/1.1/myartifact-1.1.war.sha1\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6ODUxMzU2NTJhOTc4YmU5YTRjOWY0MGI0ZWY0MjM1NTk\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"10a34637ad661d98ba3344717656fcc76209c2f8\",\"md5\":\"0144712dd81be0c3d9724f5e56ce6685\"}}]},{\"id\":\"bWF2ZW4tcmVsZWFzZXM6NzZkN2Q3ZTQxODZhMzkwZmQ5NmRiMjk1YjgwOTg2YWI\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"group\":\"mygroup\",\"name\":\"myartifact\",\"version\":\"1.2\",\"assets\":[{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.2/myartifact-1.2.war\",\"path\":\"mygroup/myartifact/1.2/myartifact-1.2.war\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6MTNiMjllNDQ5ZjBlM2I4ZDYwZGQ0ZjAyNmY4ZjVkYWU\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"da39a3ee5e6b4b0d3255bfef95601890afd80709\",\"md5\":\"d41d8cd98f00b204e9800998ecf8427e\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.2/myartifact-1.2.war.md5\",\"path\":\"mygroup/myartifact/1.2/myartifact-1.2.war.md5\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6ODUxMzU2NTJhOTc4YmU5YWZhNjRiNTEwYzAwODUzOGU\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"67a74306b06d0c01624fe0d0249a570f4d093747\",\"md5\":\"74be16979710d4c4e7c6647856088456\"}},{\"downloadUrl\":\"http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.2/myartifact-1.2.war.sha1\",\"path\":\"mygroup/myartifact/1.2/myartifact-1.2.war.sha1\",\"id\":\"bWF2ZW4tcmVsZWFzZXM6MGFiODBhNzQzOTIxZTQyNjRkOTU3MWZkZTEzNTJmYzQ\",\"repository\":\"maven-releases\",\"format\":\"maven2\",\"checksum\":{\"sha1\":\"10a34637ad661d98ba3344717656fcc76209c2f8\",\"md5\":\"0144712dd81be0c3d9724f5e56ce6685\"}}]}],\"continuationToken\":null}";

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testExecuteOnDelegate() {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .artifactsArrayPath("results")
                                                                      .versionPath("version")
                                                                      .script("echo script")
                                                                      .workingDirectory("/tmp")
                                                                      .build();
    ShellScriptTaskParametersNG shellScriptTaskParametersNG = ShellScriptTaskParametersNG.builder()
                                                                  .scriptType(ScriptType.BASH)
                                                                  .executeOnDelegate(true)
                                                                  .outputVars(Collections.EMPTY_LIST)
                                                                  .script(customArtifactDelegateRequest.getScript())
                                                                  .build();
    ScriptProcessExecutor scriptProcessExecutor = mock(ScriptProcessExecutor.class);
    doReturn(scriptProcessExecutor)
        .when(shellExecutorFactory)
        .getExecutorForCustomArtifactScriptExecution(any(), any());
    when(scriptProcessExecutor.executeCommandString(any(), anyList()))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build());
    ShellScriptTaskResponseNG shellScriptTaskResponseNG =
        customArtifactScriptExecutionOnDelegateNG.executeOnDelegate(shellScriptTaskParametersNG, logCallback);
    assertThat(shellScriptTaskResponseNG).isNotNull();
    verify(shellExecutorFactory).getExecutorForCustomArtifactScriptExecution(any(), any());
    verify(scriptProcessExecutor).executeCommandString(any(), anyList());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildDetails() throws IOException {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .artifactsArrayPath("results")
                                                                      .versionPath("buildNo")
                                                                      .script("echo \"hello\"")
                                                                      .workingDirectory("/tmp")
                                                                      .version("21")
                                                                      .attributes(Collections.EMPTY_MAP)
                                                                      .build();
    File file = new File(System.getProperty("java.io.tmpdir") + "/"
        + "testGetBuildDetails.json");
    String json = "{\n"
        + "  \"total\": 100,\n"
        + "  \"offset\": 20,\n"
        + "  \"limit\": 40,\n"
        + "  \"results\": [\n"
        + "    {\n"
        + "      \"buildNo\": \"21\",\n"
        + "      \"metadata\": {\n"
        + "        \"tag1\": \"value1\"\n"
        + "      }\n"
        + "    },\n"
        + "    {\n"
        + "      \"buildNo\": \"22\",\n"
        + "      \"metadata\": {\n"
        + "        \"tag1\": \"value1\"\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";
    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
    writer.write(json);

    writer.close();

    Map<String, String> map = new HashMap<>();
    map.put(ARTIFACT_RESULT_PATH, file.getAbsolutePath());
    ShellExecutionResponse shellExecutionResponse =
        ShellExecutionResponse.builder().exitValue(0).shellExecutionData(map).build();
    ArtifactStreamAttributes artifactStreamAttributes =
        ArtifactStreamAttributes.builder().customArtifactStreamScript("echo \"hello\"").build();
    List<BuildDetails> buildDetails = customArtifactScriptExecutionOnDelegateNG.getBuildDetails(
        file.getAbsolutePath(), customArtifactDelegateRequest, logCallback);
    assertThat(buildDetails).isNotNull();
    assertThat(buildDetails.size()).isEqualTo(2);
    assertThat(buildDetails.get(0).getNumber()).isEqualTo("21");

    FileUtils.deleteQuietly(file);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildDetailsNoArtifactArrayPath() throws IOException {
    Map<String, String> attributeMapping = new HashMap<>();
    attributeMapping.put("downloadUrl", "assets[0].downloadUrl");
    attributeMapping.put("repository", "assets[0].repository");
    attributeMapping.put("checksum", "assets[0].checksum");
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .versionPath("version")
                                                                      .artifactsArrayPath("$.items")
                                                                      .script("echo \"hello\"")
                                                                      .workingDirectory("/tmp")
                                                                      .version("21")
                                                                      .attributes(attributeMapping)
                                                                      .build();
    File file = new File(System.getProperty("java.io.tmpdir") + "/"
        + "raw.json");
    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
    writer.write(JSON_RESPONSE);

    writer.close();
    Map<String, String> map = new HashMap<>();
    map.put(ARTIFACT_RESULT_PATH, file.getAbsolutePath());
    ShellExecutionResponse shellExecutionResponse =
        ShellExecutionResponse.builder().exitValue(0).shellExecutionData(map).build();
    when(shellExecutionService.execute(any(ShellExecutionRequest.class))).thenReturn(shellExecutionResponse);

    List<BuildDetails> buildDetails = customArtifactScriptExecutionOnDelegateNG.getBuildDetails(
        file.getAbsolutePath(), customArtifactDelegateRequest, logCallback);
    assertThat(buildDetails).isNotNull();
    assertThat(buildDetails.size()).isEqualTo(3);
    assertThat(buildDetails.get(0).getNumber()).isEqualTo("1.0");
    assertThat(buildDetails.get(0).getMetadata().size()).isEqualTo(3);
    assertThat(buildDetails.get(0).getMetadata().get("downloadUrl"))
        .isEqualTo("http://localhost:8081/repository/maven-releases/mygroup/myartifact/1.0/myartifact-1.0.war");
    assertThat(buildDetails.get(0).getMetadata().get("repository")).isEqualTo("maven-releases");
    assertThat(buildDetails.get(0).getMetadata().get("checksum")).isNotNull();
    FileUtils.deleteQuietly(file);
  }

  @Test(expected = InvalidArtifactServerException.class)
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildsWithInvalidCustomMappingWithoutArtifactRoot() throws IOException {
    Map<String, String> attributeMapping = new HashMap<>();
    attributeMapping.put("assets[0].downloadUrl", "metadata.downloadUrl");
    File file = new File(System.getProperty("java.io.tmpdir") + "/"
        + "raw.json");
    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
    writer.write(JSON_RESPONSE);
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .versionPath("version")
                                                                      .artifactsArrayPath("$.items")
                                                                      .script("echo \"hello\"")
                                                                      .workingDirectory("/tmp")
                                                                      .version("21")
                                                                      .attributes(attributeMapping)
                                                                      .build();
    customArtifactScriptExecutionOnDelegateNG.getBuildDetails(
        file.getAbsolutePath(), customArtifactDelegateRequest, logCallback);
  }

  @Test(expected = InvalidArtifactServerException.class)
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildsWithInvalidCustomMappingWithoutBuildNo() throws IOException {
    Map<String, String> attributeMapping = new HashMap<>();
    attributeMapping.put("assets[0].downloadUrl", "metadata.downloadUrl");
    File file = new File(System.getProperty("java.io.tmpdir") + "/"
        + "raw.json");
    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
    writer.write(JSON_RESPONSE);
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .versionPath("version")
                                                                      .artifactsArrayPath("$.items")
                                                                      .script("echo \"hello\"")
                                                                      .workingDirectory("/tmp")
                                                                      .version("21")
                                                                      .attributes(attributeMapping)
                                                                      .build();
    customArtifactScriptExecutionOnDelegateNG.getBuildDetails(
        file.getAbsolutePath(), customArtifactDelegateRequest, logCallback);
  }

  @Test(expected = InvalidArtifactServerException.class)
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildDetailsArtifactArrayPathNotFound() throws IOException {
    Map<String, String> attributeMapping = new HashMap<>();
    attributeMapping.put("downloadUrl", "assets[0].downloadUrl");
    attributeMapping.put("repository", "assets[0].repository");
    attributeMapping.put("checksum", "assets[0].checksum");
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .versionPath("version")
                                                                      .artifactsArrayPath("$.itemss")
                                                                      .script("echo \"hello\"")
                                                                      .workingDirectory("/tmp")
                                                                      .version("21")
                                                                      .attributes(attributeMapping)
                                                                      .build();
    File file = new File(System.getProperty("java.io.tmpdir") + "/"
        + "raw.json");
    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
    writer.write(JSON_RESPONSE);

    writer.close();
    Map<String, String> map = new HashMap<>();
    map.put(ARTIFACT_RESULT_PATH, file.getAbsolutePath());
    ShellExecutionResponse shellExecutionResponse =
        ShellExecutionResponse.builder().exitValue(0).shellExecutionData(map).build();
    when(shellExecutionService.execute(any(ShellExecutionRequest.class))).thenReturn(shellExecutionResponse);

    List<BuildDetails> buildDetails = customArtifactScriptExecutionOnDelegateNG.getBuildDetails(
        file.getAbsolutePath(), customArtifactDelegateRequest, logCallback);
    assertThat(buildDetails).isNotNull();
    FileUtils.deleteQuietly(file);
  }
}