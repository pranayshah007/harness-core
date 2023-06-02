/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gitapi.client.impl;

import static io.harness.rule.OwnerRule.MEENA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.gitapi.GitApiMergePRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse.GitApiTaskResponseBuilder;
import io.harness.git.model.MergePRResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class BitbucketApiClientTest {
  BitbucketApiClient bitbucketApiClient = new BitbucketApiClient(null, null);
  private static final String REPO_SLUG = "test-repo";
  private static final String PR_NUMBER = "1";
  private static final String SHA = "abc123xyz";

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testSuccessMergePRResponse() {
    MergePRResponse prResponse = new MergePRResponse();
    prResponse.setSha(SHA);
    prResponse.setMerged(true);
    GitApiTaskResponseBuilder gitApiTaskResponseBuilder =
        bitbucketApiClient.prepareResponse(REPO_SLUG, PR_NUMBER, SHA, true, true, prResponse);
    assertThat(gitApiTaskResponseBuilder.build().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFailedToMergePRResponse() {
    String errMsg = "The pull request is already closed.";
    MergePRResponse prResponse = new MergePRResponse();
    prResponse.setErrorCode(500);
    prResponse.setMerged(false);
    prResponse.setErrorMessage(errMsg);
    GitApiTaskResponseBuilder gitApiTaskResponseBuilder =
        bitbucketApiClient.prepareResponse(REPO_SLUG, PR_NUMBER, SHA, true, true, prResponse);
    assertThat(gitApiTaskResponseBuilder.build().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(gitApiTaskResponseBuilder.build().getErrorMessage())
        .isEqualTo("Merging PR encountered a problem. SHA:" + SHA + " Repo:" + REPO_SLUG + " PrNumber:" + PR_NUMBER
            + " Message:" + errMsg + " Code:500");
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFailedToDeleteOnPremRefResponse() {
    String errMsg = "Failed to delete source ref.";
    MergePRResponse prResponse = new MergePRResponse();
    prResponse.setErrorCode(500);
    prResponse.setMerged(true);
    prResponse.setSha(SHA);
    prResponse.setErrorMessage(errMsg);
    prResponse.setSourceBranchDeleted(false);
    GitApiTaskResponseBuilder gitApiTaskResponseBuilder =
        bitbucketApiClient.prepareResponse(REPO_SLUG, PR_NUMBER, SHA, false, true, prResponse);
    assertThat(gitApiTaskResponseBuilder.build().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(gitApiTaskResponseBuilder.build().getErrorMessage())
        .contains("PR merged successfully, but encountered a problem while deleting the source branch.");
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testDeleteSourceBranchFalseResponse() {
    MergePRResponse prResponse = new MergePRResponse();
    prResponse.setMerged(true);
    prResponse.setSha(SHA);
    prResponse.setSourceBranchDeleted(false);
    GitApiTaskResponseBuilder gitApiTaskResponseBuilder =
        bitbucketApiClient.prepareResponse(REPO_SLUG, PR_NUMBER, SHA, false, false, prResponse);
    assertThat(gitApiTaskResponseBuilder.build().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(((GitApiMergePRTaskResponse) gitApiTaskResponseBuilder.build().getGitApiResult()).getSha())
        .isEqualTo(SHA);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testNullMergeResponse() {
    GitApiTaskResponseBuilder gitApiTaskResponseBuilder =
        bitbucketApiClient.prepareResponse(REPO_SLUG, PR_NUMBER, SHA, false, false, null);
    assertThat(gitApiTaskResponseBuilder.build().getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }
}
