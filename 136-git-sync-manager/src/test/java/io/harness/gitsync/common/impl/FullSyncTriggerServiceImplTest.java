/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.dtos.TriggerFullSyncResponseDTO;
import io.harness.gitsync.common.helper.UserProfileHelper;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.core.fullsync.GitFullSyncConfigService;
import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;
import io.harness.gitsync.core.fullsync.service.FullSyncJobService;
import io.harness.gitsync.fullsync.dtos.GitFullSyncConfigDTO;
import io.harness.rule.Owner;
import io.harness.security.UserPrincipal;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class FullSyncTriggerServiceImplTest extends GitSyncTestBase {
  public static final String YAML_GIT_CONFIG = "yamlGitConfig";
  public static final String BRANCH = "branch";
  public static final String PR_TITLE = "pr title";
  public static final String ACCOUNT = "account";
  public static final String ORG = "org";
  public static final String PROJECT = "project";
  public static final String REPO_URL = "repo_url";
  public static final String ROOT_FOLDER = "root_folder";
  public static final String NAME = "name";
  public static final String USER_ID = "user_Id";
  private GitFullSyncConfigDTO gitFullSyncConfigDTO;
  private GitFullSyncJob gitFullSyncJob;
  private UserPrincipal userPrincipal;
  @Inject private FullSyncTriggerServiceImpl fullSyncTriggerService;
  @Mock Producer eventProducer;
  @Mock FullSyncJobService fullSyncJobService;
  @Mock GitBranchService gitBranchService;
  @Mock GitFullSyncConfigService gitFullSyncConfigService;
  @Mock UserProfileHelper userProfileHelper;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    gitFullSyncConfigDTO = GitFullSyncConfigDTO.builder()
                               .accountIdentifier(ACCOUNT)
                               .orgIdentifier(ORG)
                               .projectIdentifier(PROJECT)
                               .branch(BRANCH)
                               .repoIdentifier(YAML_GIT_CONFIG)
                               .prTitle(PR_TITLE)
                               .rootFolder(ROOT_FOLDER)
                               .isNewBranch(false)
                               .baseBranch(BRANCH)
                               .createPullRequest(false)
                               .targetBranch(null)
                               .build();
    gitFullSyncJob = GitFullSyncJob.builder().build();
    userPrincipal =
        UserPrincipal.newBuilder().setUserId(StringValue.of(USER_ID)).setUserName(StringValue.of(NAME)).build();

    when(eventProducer.send(any())).thenReturn(randomAlphabetic(10));
    when(userProfileHelper.getUserPrincipal()).thenReturn(userPrincipal);
    when(userProfileHelper.validateIfScmUserProfileIsSet(ACCOUNT)).thenReturn(true);
    FieldUtils.writeField(fullSyncTriggerService, "userProfileHelper", userProfileHelper, true);
    FieldUtils.writeField(fullSyncTriggerService, "gitBranchService", gitBranchService, true);
    FieldUtils.writeField(fullSyncTriggerService, "fullSyncJobService", fullSyncJobService, true);
    FieldUtils.writeField(fullSyncTriggerService, "gitFullSyncConfigService", gitFullSyncConfigService, true);
    FieldUtils.writeField(fullSyncTriggerService, "eventProducer", eventProducer, true);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testTriggerFullSync() {
    when(gitFullSyncConfigService.get(any(), any(), any())).thenReturn(Optional.of(gitFullSyncConfigDTO));
    when(gitBranchService.isBranchExists(any(), any(), any(), any(), any())).thenReturn(true);
    when(fullSyncJobService.getRunningOrQueuedJob(any(), any(), any())).thenReturn(Optional.empty());

    TriggerFullSyncResponseDTO triggerFullSyncResponseDTO =
        fullSyncTriggerService.triggerFullSync(ACCOUNT, ORG, PROJECT);
    assertThat(triggerFullSyncResponseDTO.getIsFullSyncTriggered()).isEqualTo(true);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testTriggerFullSync_whenFullSyncConfigIsNotSet() {
    when(gitFullSyncConfigService.get(any(), any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> fullSyncTriggerService.triggerFullSync(ACCOUNT, ORG, PROJECT))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex
            -> ex.getMessage().equals(
                "There is no configuration saved for performing full sync, please save and try again"));
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testTriggerFullSync_whenGivenBranchDoesNotExist() {
    when(gitFullSyncConfigService.get(any(), any(), any())).thenReturn(Optional.of(gitFullSyncConfigDTO));
    when(gitBranchService.isBranchExists(any(), any(), any(), any(), any())).thenReturn(false);

    assertThatThrownBy(() -> fullSyncTriggerService.triggerFullSync(ACCOUNT, ORG, PROJECT))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex
            -> ex.getMessage().equals(String.format("Branch [%s] does not exist. Please check the config.", BRANCH)));
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testTriggerFullSync_whenNewBranchExist() {
    gitFullSyncConfigDTO = GitFullSyncConfigDTO.builder().branch(BRANCH).isNewBranch(true).build();
    when(gitFullSyncConfigService.get(any(), any(), any())).thenReturn(Optional.of(gitFullSyncConfigDTO));
    when(gitBranchService.isBranchExists(any(), any(), any(), any(), any())).thenReturn(true);

    assertThatThrownBy(() -> fullSyncTriggerService.triggerFullSync(ACCOUNT, ORG, PROJECT))
        .isInstanceOf(InvalidRequestException.class)
        .matches(
            ex -> ex.getMessage().equals(String.format("Branch [%s] already exist. Please check the config.", BRANCH)));
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testTriggerFullSync_whenFullSyncIsInProgress() {
    when(gitFullSyncConfigService.get(any(), any(), any())).thenReturn(Optional.of(gitFullSyncConfigDTO));
    when(gitBranchService.isBranchExists(any(), any(), any(), any(), any())).thenReturn(true);
    when(fullSyncJobService.getRunningOrQueuedJob(any(), any(), any())).thenReturn(Optional.of(gitFullSyncJob));

    assertThatThrownBy(() -> fullSyncTriggerService.triggerFullSync(ACCOUNT, ORG, PROJECT))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Last Sync is in progress"));
  }
}
