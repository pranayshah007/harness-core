/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitSyncSettings;

import static io.harness.gitsync.common.beans.GitSyncSettings.IS_EXECUTE_ON_DELEGATE;
import static io.harness.gitsync.common.beans.GitSyncSettings.IS_GIT_SIMPLIFICATION_ENABLED;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.HARI;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.beans.GitSyncSettings;
import io.harness.gitsync.common.dtos.GitSyncSettingsDTO;
import io.harness.gitsync.common.impl.GitSyncSettingsServiceImpl;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.repositories.gitSyncSettings.GitSyncSettingsRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.DX)
public class GitSyncSettingsTest extends GitSyncTestBase {
  @Inject GitSyncSettingsServiceImpl gitSyncSettingsService;
  @Inject GitSyncSettingsRepository gitSyncSettingsRepository;
  @Mock YamlGitConfigService yamlGitConfigService;
  public static final String accountIdentifier = "accountIdentifier";
  public static final String projectIdentifier = "projectIdentifier";
  public static final String orgIdentifier = "orgIdentifier";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HARI)
  @Category(UnitTests.class)
  public void testUpdateSetting() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IS_EXECUTE_ON_DELEGATE, String.valueOf(true));
    GitSyncSettings gitSyncSettings = GitSyncSettings.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .settings(settings)
                                          .build();
    gitSyncSettingsRepository.save(gitSyncSettings);
    GitSyncSettingsDTO request = GitSyncSettingsDTO.builder()
                                     .accountIdentifier(accountIdentifier)
                                     .orgIdentifier(orgIdentifier)
                                     .projectIdentifier(projectIdentifier)
                                     .executeOnDelegate(false)
                                     .build();
    final GitSyncSettingsDTO update = gitSyncSettingsService.update(request);
    assertThat(update.equals(request));
    final Optional<GitSyncSettingsDTO> gitSyncSettingsDTOOptional =
        gitSyncSettingsService.get(accountIdentifier, orgIdentifier, projectIdentifier);
    Boolean isExecuteOnDelegate = gitSyncSettingsDTOOptional.get().isExecuteOnDelegate();
    assertThat(isExecuteOnDelegate).isEqualTo(false);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testSaveSettings() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IS_EXECUTE_ON_DELEGATE, String.valueOf(true));
    GitSyncSettings gitSyncSettings = GitSyncSettings.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .settings(settings)
                                          .build();
    GitSyncSettings saved = gitSyncSettingsRepository.save(gitSyncSettings);
    assertThat(saved.equals(gitSyncSettings));
    final Optional<GitSyncSettingsDTO> gitSyncSettingsDTOOptional =
        gitSyncSettingsService.get(accountIdentifier, orgIdentifier, projectIdentifier);
    Boolean isExecuteOnDelegate = gitSyncSettingsDTOOptional.get().isExecuteOnDelegate();
    assertThat(isExecuteOnDelegate).isEqualTo(true);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testDelete() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IS_EXECUTE_ON_DELEGATE, String.valueOf(true));
    GitSyncSettings gitSyncSettings = GitSyncSettings.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .settings(settings)
                                          .build();
    gitSyncSettingsRepository.save(gitSyncSettings);
    gitSyncSettingsService.delete(accountIdentifier, orgIdentifier, projectIdentifier);
    assertThat(gitSyncSettingsService.get(accountIdentifier, orgIdentifier, projectIdentifier).isPresent()).isFalse();
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testEnableGitSimplificationIfGitSyncIsEnabled() {
    when(yamlGitConfigService.isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(true);
    try {
      gitSyncSettingsService.enableGitSimplification(accountIdentifier, orgIdentifier, projectIdentifier);
    } catch (Exception exception) {
      assertThat(exception).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testEnableGitSimplificationIfGitSimplificationIsAlreadyEnabled() {
    when(yamlGitConfigService.isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(false);
    createSettingWithGitSimplificationEnabled();
    boolean status =
        gitSyncSettingsService.enableGitSimplification(accountIdentifier, orgIdentifier, projectIdentifier);
    assertThat(status).isEqualTo(true);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testEnableGitSimplificationIfGitSyncSettingAlreadySet() {
    when(yamlGitConfigService.isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(false);
    createGitSyncSetting();
    try {
      gitSyncSettingsService.enableGitSimplification(accountIdentifier, orgIdentifier, projectIdentifier);
    } catch (Exception exception) {
      assertThat(exception).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testEnableGitSimplification() {
    when(yamlGitConfigService.isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(false);
    boolean status =
        gitSyncSettingsService.enableGitSimplification(accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<GitSyncSettingsDTO> optionalGitSyncSettingsDTO =
        gitSyncSettingsService.get(accountIdentifier, orgIdentifier, projectIdentifier);

    assertThat(status).isEqualTo(true);
    assertThat(optionalGitSyncSettingsDTO).isPresent();
    assertThat(optionalGitSyncSettingsDTO.get().isGitSimplificationEnabled()).isEqualTo(true);
  }

  private void createSettingWithGitSimplificationEnabled() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IS_GIT_SIMPLIFICATION_ENABLED, String.valueOf(true));
    GitSyncSettings gitSyncSettings = GitSyncSettings.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .settings(settings)
                                          .build();
    gitSyncSettingsRepository.save(gitSyncSettings);
  }

  private void createGitSyncSetting() {
    Map<String, String> settings = new HashMap<>();
    settings.put(IS_EXECUTE_ON_DELEGATE, String.valueOf(true));
    GitSyncSettings gitSyncSettings = GitSyncSettings.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .settings(settings)
                                          .build();
    gitSyncSettingsRepository.save(gitSyncSettings);
  }
}
