/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.gitapi.client.impl;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.gitapi.GitApiMergePRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse.GitApiTaskResponseBuilder;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AzureRepoClientTest extends CategoryTest {
  AzureRepoApiClient azureRepoApiClient = new AzureRepoApiClient(null, null);

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetValue() {
    assertThat(azureRepoApiClient.getValue(null, null)).isEqualTo(null);
    JSONObject jsonObject = new JSONObject();
    assertThat(azureRepoApiClient.getValue(jsonObject, null)).isEqualTo(null);
    jsonObject.put("key1", "val");
    assertThat(azureRepoApiClient.getValue(jsonObject, "key1")).isEqualTo("val");
    jsonObject.put("key2", 2);
    assertThat(azureRepoApiClient.getValue(jsonObject, "key2")).isEqualTo(2);
    assertThat(azureRepoApiClient.getValue(jsonObject, "key3")).isEqualTo(null);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldSetFailureForNullMergePRResponse() {
    GitApiTaskResponseBuilder gitApiTaskResponseBuilder = GitApiTaskResponse.builder();
    azureRepoApiClient.prepareResponseBuilder(gitApiTaskResponseBuilder, "REPO", "PR1", "SHA", null);
    assertThat(gitApiTaskResponseBuilder.build().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(gitApiTaskResponseBuilder.build().getErrorMessage())
        .isEqualTo("Merging PR encountered a problem. sha:SHA Repo:REPO PrNumber:PR1");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldSetFailureForNonMergedPRResponse() {
    GitApiTaskResponseBuilder gitApiTaskResponseBuilder = GitApiTaskResponse.builder();
    JSONObject mergePRResponse = new JSONObject();
    azureRepoApiClient.prepareResponseBuilder(gitApiTaskResponseBuilder, "REPO", "PR1", "SHA", mergePRResponse);
    assertThat(gitApiTaskResponseBuilder.build().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(gitApiTaskResponseBuilder.build().getErrorMessage())
        .isEqualTo("Merging PR encountered a problem. sha:SHA Repo:REPO PrNumber:PR1 Message:null Code:null");

    // Non-Boolean merged response
    mergePRResponse.put("merged", 1);
    mergePRResponse.put("error", "MSG");
    mergePRResponse.put("code", 1);
    azureRepoApiClient.prepareResponseBuilder(gitApiTaskResponseBuilder, "REPO", "PR1", "SHA", mergePRResponse);
    assertThat(gitApiTaskResponseBuilder.build().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(gitApiTaskResponseBuilder.build().getErrorMessage())
        .isEqualTo("Merging PR encountered a problem. sha:SHA Repo:REPO PrNumber:PR1 Message:MSG Code:1");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldSetSuccessForMergedPRResponse() {
    GitApiTaskResponseBuilder gitApiTaskResponseBuilder = GitApiTaskResponse.builder();
    JSONObject mergePRResponse = new JSONObject();
    mergePRResponse.put("merged", true);
    azureRepoApiClient.prepareResponseBuilder(gitApiTaskResponseBuilder, "REPO", "PR1", "SHA", mergePRResponse);
    assertThat(gitApiTaskResponseBuilder.build().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(((GitApiMergePRTaskResponse) gitApiTaskResponseBuilder.build().getGitApiResult()).getSha())
        .isEqualTo(null);
    mergePRResponse.put("sha", "12");
    azureRepoApiClient.prepareResponseBuilder(gitApiTaskResponseBuilder, "REPO", "PR1", "SHA", mergePRResponse);
    assertThat(((GitApiMergePRTaskResponse) gitApiTaskResponseBuilder.build().getGitApiResult()).getSha())
        .isEqualTo("12");
  }
}
