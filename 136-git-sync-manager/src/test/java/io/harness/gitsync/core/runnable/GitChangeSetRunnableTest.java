/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.runnable;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.beans.YamlChangeSetEventType;
import io.harness.gitsync.core.dtos.YamlChangeSetDTO;
import io.harness.gitsync.core.dtos.YamlChangeSetSaveDTO;
import io.harness.gitsync.core.dtos.YamlChangeSetSaveDTO.YamlChangeSetSaveDTOBuilder;
import io.harness.gitsync.core.impl.YamlChangeSetServiceImpl;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(DX)
public class GitChangeSetRunnableTest extends GitSyncTestBase {
  @Inject @Spy @InjectMocks GitChangeSetRunnable gitChangeSetRunnable;
  @Inject @Spy YamlChangeSetServiceImpl yamlChangeSetService;

  final String accountId = "accountId";
  final String branch = "branch";
  final String repo = "repo";
  final IdentifierRef connectorRef = IdentifierRef.builder().accountIdentifier(accountId).build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    doReturn(true).when(gitChangeSetRunnable).shouldRun();
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testRun() {
    YamlChangeSetSaveDTOBuilder yamlChangeSetBuilder =
        YamlChangeSetSaveDTO.builder().accountId(accountId).branch(branch).repoUrl(repo);
    yamlChangeSetService.save(yamlChangeSetBuilder.eventType(YamlChangeSetEventType.BRANCH_CREATE).build());
    ArgumentCaptor<YamlChangeSetDTO> argumentCaptor = ArgumentCaptor.forClass(YamlChangeSetDTO.class);
    gitChangeSetRunnable.run();
    verify(gitChangeSetRunnable).processChangeSet(argumentCaptor.capture());
    assertThat(argumentCaptor.getAllValues().size()).isEqualTo(1);
    assertThat(argumentCaptor.getAllValues().get(0).getEventType()).isEqualTo(YamlChangeSetEventType.BRANCH_CREATE);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testRun_1() {
    YamlChangeSetSaveDTOBuilder yamlChangeSetBuilder =
        YamlChangeSetSaveDTO.builder().accountId(accountId).repoUrl(repo);
    yamlChangeSetService.save(
        yamlChangeSetBuilder.eventType(YamlChangeSetEventType.BRANCH_SYNC).branch(branch).build());
    yamlChangeSetService.save(
        yamlChangeSetBuilder.eventType(YamlChangeSetEventType.BRANCH_PUSH).branch("branch1").build());
    ArgumentCaptor<YamlChangeSetDTO> argumentCaptor = ArgumentCaptor.forClass(YamlChangeSetDTO.class);
    gitChangeSetRunnable.run();
    verify(gitChangeSetRunnable, times(2)).processChangeSet(argumentCaptor.capture());
    assertThat(argumentCaptor.getAllValues().size()).isEqualTo(2);
    assertThat(argumentCaptor.getAllValues().stream().anyMatch(
        yamlChangeSet -> yamlChangeSet.getEventType().equals(YamlChangeSetEventType.BRANCH_SYNC)));
    assertThat(argumentCaptor.getAllValues().stream().anyMatch(
        yamlChangeSet -> yamlChangeSet.getEventType().equals(YamlChangeSetEventType.BRANCH_PUSH)));
  }
}
