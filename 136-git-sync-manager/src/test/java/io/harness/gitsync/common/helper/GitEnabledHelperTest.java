/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.common.dtos.ConnectivityMode;
import io.harness.gitsync.common.dtos.GitEnabledDTO;
import io.harness.gitsync.common.dtos.GitSyncSettingsDTO;
import io.harness.gitsync.common.service.GitSyncSettingsService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(DX)
public class GitEnabledHelperTest extends CategoryTest {
  @Mock YamlGitConfigService yamlGitConfigService;
  @Mock GitSyncSettingsService gitSyncSettingsService;
  @InjectMocks GitEnabledHelper gitEnabledHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGitEnabled_1() throws IOException {
    GitSyncSettingsDTO gitSyncSettingsDTO = GitSyncSettingsDTO.builder().executeOnDelegate(true).build();
    doReturn(Optional.of(gitSyncSettingsDTO)).when(gitSyncSettingsService).get(anyString(), anyString(), anyString());
    doReturn(true).when(yamlGitConfigService).isGitSyncEnabled(anyString(), anyString(), anyString());
    final GitEnabledDTO gitEnabledDTO = gitEnabledHelper.getGitEnabledDTO("proj", "org", "acc");
    assertThat(gitEnabledDTO)
        .isEqualTo(GitEnabledDTO.builder().isGitSyncEnabled(true).connectivityMode(ConnectivityMode.DELEGATE).build());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGitEnabled_2() throws IOException {
    GitSyncSettingsDTO gitSyncSettingsDTO = GitSyncSettingsDTO.builder().executeOnDelegate(false).build();
    doReturn(Optional.of(gitSyncSettingsDTO)).when(gitSyncSettingsService).get(anyString(), anyString(), anyString());
    doReturn(true).when(yamlGitConfigService).isGitSyncEnabled(anyString(), anyString(), anyString());
    final GitEnabledDTO gitEnabledDTO = gitEnabledHelper.getGitEnabledDTO("proj", "org", "acc");
    assertThat(gitEnabledDTO)
        .isEqualTo(GitEnabledDTO.builder().isGitSyncEnabled(true).connectivityMode(ConnectivityMode.MANAGER).build());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGitEnabled_3() throws IOException {
    GitSyncSettingsDTO gitSyncSettingsDTO = GitSyncSettingsDTO.builder().executeOnDelegate(true).build();
    doReturn(Optional.of(gitSyncSettingsDTO)).when(gitSyncSettingsService).get(anyString(), anyString(), anyString());
    doReturn(false).when(yamlGitConfigService).isGitSyncEnabled(anyString(), anyString(), anyString());
    final GitEnabledDTO gitEnabledDTO = gitEnabledHelper.getGitEnabledDTO("proj", "org", "acc");
    assertThat(gitEnabledDTO).isEqualTo(GitEnabledDTO.builder().isGitSyncEnabled(false).build());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGitEnabled_4() throws IOException {
    doReturn(Optional.empty()).when(gitSyncSettingsService).get(anyString(), anyString(), anyString());
    doReturn(false).when(yamlGitConfigService).isGitSyncEnabled(anyString(), anyString(), anyString());
    final GitEnabledDTO gitEnabledDTO = gitEnabledHelper.getGitEnabledDTO("proj", "org", "acc");
    assertThat(gitEnabledDTO).isEqualTo(GitEnabledDTO.builder().isGitSyncEnabled(false).build());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGitEnabled_5() throws IOException {
    doReturn(Optional.empty()).when(gitSyncSettingsService).get(anyString(), anyString(), anyString());
    doReturn(true).when(yamlGitConfigService).isGitSyncEnabled(anyString(), anyString(), anyString());
    final GitEnabledDTO gitEnabledDTO = gitEnabledHelper.getGitEnabledDTO("proj", "org", "acc");
    assertThat(gitEnabledDTO)
        .isEqualTo(GitEnabledDTO.builder()
                       .isGitSyncEnabled(true)
                       .connectivityMode(ConnectivityMode.DELEGATE)
                       .isGitSyncEnabledOnlyForFF(false)
                       .build());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGitEnabledForGitSimplificationUseCase() throws IOException {
    GitSyncSettingsDTO gitSyncSettingsDTO = GitSyncSettingsDTO.builder().isGitSimplificationEnabled(true).build();
    doReturn(Optional.of(gitSyncSettingsDTO)).when(gitSyncSettingsService).get(anyString(), anyString(), anyString());
    doReturn(false).when(yamlGitConfigService).isGitSyncEnabled(anyString(), anyString(), anyString());
    GitEnabledDTO gitEnabledDTO = gitEnabledHelper.getGitEnabledDTO("proj", "org", "acc");
    assertThat(gitEnabledDTO).isEqualTo(GitEnabledDTO.builder().isGitSimplificationEnabled(true).build());

    gitSyncSettingsDTO.setGitSimplificationEnabled(false);
    doReturn(Optional.of(gitSyncSettingsDTO)).when(gitSyncSettingsService).get(anyString(), anyString(), anyString());
    gitEnabledDTO = gitEnabledHelper.getGitEnabledDTO("proj", "org", "acc");
    assertThat(gitEnabledDTO).isEqualTo(GitEnabledDTO.builder().isGitSimplificationEnabled(false).build());
  }
}
