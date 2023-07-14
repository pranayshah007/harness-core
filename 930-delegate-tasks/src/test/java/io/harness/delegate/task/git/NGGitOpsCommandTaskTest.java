/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.MANAVJOT;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.gitops.GitOpsTaskHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.YamlException;
import io.harness.git.model.CommitResult;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.git.model.RevertAndPushResult;
import io.harness.logging.LogCallback;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jooq.tools.json.JSONArray;
import org.jooq.tools.json.JSONObject;
import org.jooq.tools.json.ParseException;
import org.jose4j.lang.JoseException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.GITOPS)
public class NGGitOpsCommandTaskTest extends CategoryTest {
  private static final String TEST_INPUT_ID = generateUuid();
  @Mock private GitDecryptionHelper gitDecryptionHelper;
  @Mock private NGGitService gitService;
  @Mock private ScmFetchFilesHelperNG scmFetchFilesHelper;

  @Mock private GitOpsTaskHelper gitOpsTaskHelper;
  @InjectMocks
  NGGitOpsCommandTask ngGitOpsCommandTask = new NGGitOpsCommandTask(
      DelegateTaskPackage.builder()
          .delegateId(TEST_INPUT_ID)
          .delegateTaskId(TEST_INPUT_ID)
          .data(TaskData.builder().parameters(new Object[] {}).taskType(TEST_INPUT_ID).async(false).build())
          .accountId(TEST_INPUT_ID)
          .build(),
      null, null, null);
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private JSONObject existingFile;
  private JSONObject variableInputJSON;
  private Map<String, String> variableInputMap;
  @Mock private LogCallback logCallback;
  private static final String DEFAULT_PR_TITLE = "Harness: Updating config overrides";
  private static final String DEFAULT_REVERT_PR_TITLE = "Harness: Reverting config overrides";
  private static final String CUSTOM_PR_TITLE = "Custom PR Title Support Verified.";
  private String sampleJSON;
  private String sampleYAML;
  private String sampleYAMLtoJSON;

  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "testJson.json";
    sampleJSON = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    filename = "testYaml.yaml";
    sampleYAML = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    filename = "testYamlToJson.json";
    sampleYAMLtoJSON =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    doNothing().when(gitDecryptionHelper).decryptGitConfig(any(GitConfigDTO.class), anyList());
    doNothing().when(gitDecryptionHelper).decryptApiAccessConfig(any(ScmConnector.class), anyList());
  }

  private void setUpForHierarchical() {
    Map<Object, Object> tmp1 = new HashMap<>();
    Map<Object, Object> tmp2 = new HashMap<>();
    tmp1.put("a", "val1");
    tmp2.put("c", "val2");
    tmp1.put("b", tmp2);
    existingFile = new JSONObject(tmp1);
    /*
    File ->
    {
      a: val1
      b:
        c: val2
    }
     */

    variableInputMap = new HashMap<>();
    variableInputMap.put("a", "val3");
    variableInputMap.put("b.c", "val4");
    variableInputMap.put("b.d", "val5");
    variableInputMap.put("f.g.h", "val6");

    tmp1 = new HashMap<>();
    tmp2 = new HashMap<>();
    tmp1.put("a", "val3");
    tmp2.put("c", "val4");
    tmp2.put("d", "val5");
    tmp1.put("b", tmp2);
    tmp2 = new HashMap<>();
    Map<Object, Object> tmp3 = new HashMap<>();
    tmp3.put("h", "val6");
    tmp2.put("g", tmp3);
    tmp1.put("f", tmp2);
    /*
    Final ->
    {
      a: val3
      b:
        c: val4
        d: val5
      f:
        g:
          h: val6
    }
     */

    variableInputJSON = new JSONObject(tmp1);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testGitRevertAndPush() throws JoseException, IOException {
    doReturn(CreateBranchResponse.newBuilder().build())
        .when(scmFetchFilesHelper)
        .createNewBranch(any(), anyString(), anyString());
    doReturn(CreatePRResponse.newBuilder().build()).when(scmFetchFilesHelper).createPR(any(), any());
    RevertAndPushResult gitRevertAndPushResult =
        RevertAndPushResult.builder().gitCommitResult(CommitResult.builder().commitId("sha").build()).build();
    doReturn(gitRevertAndPushResult)
        .when(gitService)
        .revertCommitAndPush(any(GitConfigDTO.class), any(), anyString(), any(), anyBoolean());
    GitFetchFilesConfig gitFetchFilesConfig =
        GitFetchFilesConfig.builder()
            .gitStoreDelegateConfig(
                GitStoreDelegateConfig.builder().gitConfigDTO(GitConfigDTO.builder().build()).build())
            .build();
    doNothing().when(logCallback).saveExecutionLog(any());
    doNothing().when(gitOpsTaskHelper).setGitConfigCred(gitFetchFilesConfig, logCallback);
    TaskParameters params = NGGitOpsTaskParams.builder()
                                .connectorInfoDTO(ConnectorInfoDTO.builder().name("connector").build())
                                .gitOpsTaskType(GitOpsTaskType.REVERT_PR)
                                .gitFetchFilesConfig(gitFetchFilesConfig)
                                .isRevertPR(true)
                                .build();

    NGGitOpsResponse response = (NGGitOpsResponse) ngGitOpsCommandTask.run(params);
    assertThat(response.getTaskStatus()).isEqualTo(TaskStatus.SUCCESS);
    assertThat(response.getCommitId()).isEqualTo(gitRevertAndPushResult.getGitCommitResult().getCommitId());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testGitRevertAndPushWithInvalidGitCredsShouldFail() throws JoseException, IOException {
    doReturn(CreateBranchResponse.newBuilder().build())
        .when(scmFetchFilesHelper)
        .createNewBranch(any(), anyString(), anyString());
    doReturn(CreatePRResponse.newBuilder().build()).when(scmFetchFilesHelper).createPR(any(), any());
    RevertAndPushResult gitRevertAndPushResult =
        RevertAndPushResult.builder().gitCommitResult(CommitResult.builder().commitId("sha").build()).build();
    doThrow(new YamlException(""))
        .when(gitService)
        .revertCommitAndPush(any(GitConfigDTO.class), any(), anyString(), any(), anyBoolean());
    TaskParameters params =
        NGGitOpsTaskParams.builder()
            .connectorInfoDTO(ConnectorInfoDTO.builder().name("connector").build())
            .gitOpsTaskType(GitOpsTaskType.REVERT_PR)
            .gitFetchFilesConfig(
                GitFetchFilesConfig.builder()
                    .gitStoreDelegateConfig(
                        GitStoreDelegateConfig.builder().gitConfigDTO(GitConfigDTO.builder().build()).build())
                    .build())
            .build();

    NGGitOpsResponse response = (NGGitOpsResponse) ngGitOpsCommandTask.run(params);
    assertThat(response.getTaskStatus()).isEqualTo(TaskStatus.FAILURE);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testGitRevertAndPushWithoutGitConfigShouldFail() throws JoseException, IOException {
    doReturn(CreateBranchResponse.newBuilder().build())
        .when(scmFetchFilesHelper)
        .createNewBranch(any(), anyString(), anyString());
    doReturn(CreatePRResponse.newBuilder().build()).when(scmFetchFilesHelper).createPR(any(), any());
    TaskParameters params = NGGitOpsTaskParams.builder()
                                .connectorInfoDTO(ConnectorInfoDTO.builder().name("connector").build())
                                .gitOpsTaskType(GitOpsTaskType.REVERT_PR)
                                .build();

    NGGitOpsResponse response = (NGGitOpsResponse) ngGitOpsCommandTask.run(params);
    assertThat(response.getTaskStatus()).isEqualTo(TaskStatus.FAILURE);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testUpdateFilesNotFoundWithEmptyContent() {
    List<String> paths = Arrays.asList("path1", "path2");
    List<GitFile> gitfiles = Arrays.asList(GitFile.builder().filePath("path1").fileContent("content1").build());
    FetchFilesResult fetchFilesResult = FetchFilesResult.builder().files(gitfiles).build();

    ngGitOpsCommandTask.updateFilesNotFoundWithEmptyContent(fetchFilesResult, paths);
    assertThat(fetchFilesResult.getFiles()).hasSize(2);
    assertThat(fetchFilesResult.getFiles().get(0).getFilePath()).isEqualTo("path1");
    assertThat(fetchFilesResult.getFiles().get(0).getFileContent()).isEqualTo("content1");
    assertThat(fetchFilesResult.getFiles().get(1).getFilePath()).isEqualTo("path2");
    assertThat(fetchFilesResult.getFiles().get(1).getFileContent()).isEqualTo("");
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testMapToJSONBasic() {
    JSONObject jsonObject = ngGitOpsCommandTask.mapToJson("a", "b");
    Map<Object, Object> map = new HashMap<>();
    map.put("a", "b");
    assertThat(jsonObject).isEqualTo(new JSONObject(map));
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testMapToJSONNested() {
    JSONObject jsonObject = ngGitOpsCommandTask.mapToJson("a.b", "c");
    Map<Object, Object> tmp1 = new HashMap<>();
    Map<Object, Object> tmp2 = new HashMap<>();
    tmp2.put("b", "c");
    tmp1.put("a", tmp2);
    assertThat(jsonObject).isEqualTo(new JSONObject(tmp1));
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testMergeJSON() {
    setUpForHierarchical();
    JSONObject jsonObject = ngGitOpsCommandTask.mergeJSON(existingFile, variableInputJSON);
    assertThat(jsonObject).isEqualTo(variableInputJSON);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testMergeJSONFailure() {
    setUpForHierarchical();
    JSONObject jsonObject = new JSONObject(variableInputJSON);
    jsonObject.put("b", "val");
    ngGitOpsCommandTask.mergeJSON(existingFile, jsonObject);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testUpdateJSONFile() {
    setUpForHierarchical();
    String fileContent = existingFile.toString();
    String result = "";
    try {
      result = ngGitOpsCommandTask.updateJSONFile(fileContent, variableInputMap);
    } catch (Exception e) {
    }
    String expectedResult = "{\n"
        + "  \"a\" : \"val3\",\n"
        + "  \"b\" : {\n"
        + "    \"d\" : \"val5\",\n"
        + "    \"c\" : \"val4\"\n"
        + "  },\n"
        + "  \"f\" : {\n"
        + "    \"g\" : {\n"
        + "      \"h\" : \"val6\"\n"
        + "    }\n"
        + "  }\n"
        + "}";
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testUpdateJSONFileEmpty() {
    setUpForHierarchical();
    String fileContent = "";
    String result = "";
    try {
      result = ngGitOpsCommandTask.updateJSONFile(fileContent, new HashMap<>());
    } catch (Exception e) {
    }
    String expectedResult = "{ }";
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testUpdateJSONFileInvalidType() throws ParseException, JsonProcessingException {
    Map<Object, Object> values = new HashMap<>();
    values.put("a", List.of("val1"));
    JSONArray fileContent = new JSONArray(List.of(values));
    ngGitOpsCommandTask.updateJSONFile(fileContent.toString(), new HashMap<>());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testUpdateFilesBasic() {
    String filePath = "app/service/env/cluster/config.yaml";
    String fileContent = "";
    GitFile gitFile = GitFile.builder().filePath(filePath).fileContent(fileContent).build();
    List<GitFile> gitFiles = new ArrayList<>();
    gitFiles.add(gitFile);
    FetchFilesResult fetchFilesResult = new FetchFilesResult(null, null, gitFiles);

    Map<String, String> variablesMap = new HashMap<>();
    variablesMap.put("key", "value");
    Map<String, Map<String, String>> filesToVariablesMap = new HashMap<>();
    filesToVariablesMap.put(filePath, variablesMap);

    doNothing().when(logCallback).saveExecutionLog(any());
    doNothing().when(logCallback).saveExecutionLog(any(), any());

    try {
      ngGitOpsCommandTask.updateFiles(filesToVariablesMap, fetchFilesResult);
    } catch (Exception e) {
      assertThat(e).doesNotThrowAnyException();
    }

    assertThat(fetchFilesResult.getFiles().get(0).getFileContent()).isEqualTo("key: \"value\"\n");
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testUpdateFilesNested() {
    setUpForHierarchical();
    String filePath = "app/service/env/cluster/config.yaml";
    String fileContent = "key: \"value\"\n";
    GitFile gitFile = GitFile.builder().filePath(filePath).fileContent(fileContent).build();
    List<GitFile> gitFiles = new ArrayList<>();
    gitFiles.add(gitFile);
    FetchFilesResult fetchFilesResult = new FetchFilesResult(null, null, gitFiles);

    Map<String, Map<String, String>> filesToVariablesMap = new HashMap<>();
    filesToVariablesMap.put(filePath, variableInputMap);

    doNothing().when(logCallback).saveExecutionLog(any());
    doNothing().when(logCallback).saveExecutionLog(any(), any());

    try {
      ngGitOpsCommandTask.updateFiles(filesToVariablesMap, fetchFilesResult);
    } catch (Exception e) {
      assertThat(e).doesNotThrowAnyException();
    }

    String expected = "a: \"val3\"\n"
        + "b:\n"
        + "  d: \"val5\"\n"
        + "  c: \"val4\"\n"
        + "f:\n"
        + "  g:\n"
        + "    h: \"val6\"\n"
        + "key: \"value\"\n";

    assertThat(fetchFilesResult.getFiles().get(0).getFileContent()).isEqualTo(expected);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testResolvePRTitleDefault() {
    String result = ngGitOpsCommandTask.resolvePRTitle(NGGitOpsTaskParams.builder().build(), false);
    assertThat(result).isEqualTo(DEFAULT_PR_TITLE);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testResolvePRTitleCustom() {
    String result =
        ngGitOpsCommandTask.resolvePRTitle(NGGitOpsTaskParams.builder().prTitle(CUSTOM_PR_TITLE).build(), false);
    assertThat(result).isEqualTo(CUSTOM_PR_TITLE);
  }

  @Test
  @Owner(developers = MANAVJOT)
  @Category(UnitTests.class)
  public void testResolveRevertPRTitleDefault() {
    String result = ngGitOpsCommandTask.resolvePRTitle(NGGitOpsTaskParams.builder().build(), true);
    assertThat(result).isEqualTo(DEFAULT_REVERT_PR_TITLE);
  }

  @Test
  @Owner(developers = MANAVJOT)
  @Category(UnitTests.class)
  public void testResolveRevertPRTitleCustom() {
    String result =
        ngGitOpsCommandTask.resolvePRTitle(NGGitOpsTaskParams.builder().prTitle(CUSTOM_PR_TITLE).build(), true);
    assertThat(result).isEqualTo(CUSTOM_PR_TITLE);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testConvertToPrettyJson() {
    String actualJSON = "";
    try {
      actualJSON = ngGitOpsCommandTask.convertToPrettyJson(sampleJSON);
    } catch (Exception e) {
    }
    assertThat(sampleYAMLtoJSON).isEqualTo(actualJSON);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testConvertYamlToJson() {
    String actualJSON = "";
    try {
      actualJSON = ngGitOpsCommandTask.convertYamlToJson(sampleYAML);
    } catch (Exception e) {
    }
    assertThat(sampleYAMLtoJSON).isEqualTo(actualJSON);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testConvertJsonToYaml() {
    String actualYaml = "";
    try {
      actualYaml = ngGitOpsCommandTask.convertJsonToYaml(sampleJSON);
    } catch (Exception e) {
    }
    assertThat(sampleYAML).isEqualTo(actualYaml);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetPRLinkGithub() {
    GithubConnectorDTO connectorDTO = GithubConnectorDTO.builder()
                                          .connectionType(GitConnectionType.REPO)
                                          .url("https://github.com/harness/harness-core")
                                          .build();
    String result = ngGitOpsCommandTask.getPRLink(0, connectorDTO, ConnectorType.GITHUB);
    assertThat(result).isEqualTo("https://github.com/harness/harness-core/pull/0");
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetPRLinkAzureRepo() {
    AzureRepoConnectorDTO connectorDTO = AzureRepoConnectorDTO.builder()
                                             .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                             .url("https://mankritsingh@dev.azure.com/org/project/_git/repo")
                                             .build();
    String result = ngGitOpsCommandTask.getPRLink(0, connectorDTO, ConnectorType.AZURE_REPO);
    assertThat(result).isEqualTo("https://mankritsingh@dev.azure.com/org/project/_git/repo/pullrequest/0");
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetPRLinkGitlab() {
    GitlabConnectorDTO connectorDTO = GitlabConnectorDTO.builder()
                                          .connectionType(GitConnectionType.REPO)
                                          .url("https://gitlab.com/gitlab160412/testRepo")
                                          .build();
    String result = ngGitOpsCommandTask.getPRLink(0, connectorDTO, ConnectorType.GITLAB);
    assertThat(result).isEqualTo("https://gitlab.com/gitlab160412/testRepo/merge_requests/0");
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetPRLinkBitbucket() {
    BitbucketConnectorDTO connectorDTO = BitbucketConnectorDTO.builder()
                                             .connectionType(GitConnectionType.REPO)
                                             .url("https://bitbucket.org/user/repo.git")
                                             .build();
    String result = ngGitOpsCommandTask.getPRLink(0, connectorDTO, ConnectorType.BITBUCKET);
    assertThat(result).isEqualTo("https://bitbucket.org/user/repo/pull-requests/0");
  }
}
