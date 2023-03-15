/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.MANKRIT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.tools.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.GITOPS)
public class NGGitOpsCommandTaskTest extends CategoryTest {
  private static final String TEST_INPUT_ID = generateUuid();
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
  private static final String DEFAULT_PR_TITLE = "Harness: Updating config overrides";
  private static final String CUSTOM_PR_TITLE = "Custom PR Title Support Verified.";

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
  public void testResolvePRTitleDefault() {
    String result = ngGitOpsCommandTask.resolvePRTitle(NGGitOpsTaskParams.builder().build());
    assertThat(result).isEqualTo(DEFAULT_PR_TITLE);
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testResolvePRTitleCustom() {
    String result = ngGitOpsCommandTask.resolvePRTitle(NGGitOpsTaskParams.builder().prTitle(CUSTOM_PR_TITLE).build());
    assertThat(result).isEqualTo(CUSTOM_PR_TITLE);
  }
}
