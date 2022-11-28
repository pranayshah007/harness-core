/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.connectors.configyamlhandlers;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.DHRUV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.GitConfig;
import software.wings.beans.GitConfig.ProviderType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.setting.sourcerepoprovider.GitConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class GitConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private GitConfigYamlHandler yamlHandler;
  public static final String username = "dummyUsername";
  public static final String password = "dummyPassword";
  public static final String SAMPLE_STRING = "sample-string";
  private Class yamlClass = GitConfig.Yaml.class;

  @Test
  @Owner(developers = DHRUV)
  @Category(UnitTests.class)
  public void testToBean() {
    GitConfig.Yaml yaml = GitConfig.Yaml.builder()
                              .branch(SAMPLE_STRING)
                              .reference(SAMPLE_STRING)
                              .keyAuth(true)
                              .sshKeyName(null)
                              .authorName(SAMPLE_STRING)
                              .authorEmailId(SAMPLE_STRING)
                              .commitMessage(SAMPLE_STRING)
                              .description(SAMPLE_STRING)
                              .password(SAMPLE_STRING)
                              .username(SAMPLE_STRING)
                              .url(SAMPLE_STRING)
                              .delegateSelectors(Collections.emptyList())
                              .providerType(ProviderType.GIT)
                              .build();

    Change change = Change.Builder.aFileChange()
                        .withAccountId("ABC")
                        .withFilePath("Setup/Source Repo Provider/test-harness.yaml")
                        .build();
    ChangeContext<GitConfig.Yaml> changeContext = ChangeContext.Builder.aChangeContext()
                                                      .withYamlType(YamlType.SOURCE_REPO_PROVIDER)
                                                      .withYaml(yaml)
                                                      .withChange(change)
                                                      .build();

    SettingAttribute settingAttribute = yamlHandler.toBean(null, changeContext, null);
    GitConfig gitConfig = (GitConfig) settingAttribute.getValue();

    assertThat(gitConfig).isNotNull();
    assertThat(gitConfig.getCommitMessage()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getAuthorEmailId()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getAuthorName()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getSshSettingId()).isNull();
    assertThat(gitConfig.getUsername()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getBranch()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getRepoUrl()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getDelegateSelectors()).isEqualTo(Collections.emptyList());
    assertThat(gitConfig.getProviderType()).isEqualTo(ProviderType.GIT);
  }

  @Test
  @Owner(developers = DHRUV)
  @Category(UnitTests.class)
  public void testToYaml() {
    GitConfig gitConfig = GitConfig.builder()
                              .authorEmailId(SAMPLE_STRING)
                              .authorName(SAMPLE_STRING)
                              .commitMessage(SAMPLE_STRING)
                              .branch(SAMPLE_STRING)
                              .description(SAMPLE_STRING)
                              .sshSettingId(null)
                              .delegateSelectors(Collections.emptyList())
                              .providerType(ProviderType.GIT)
                              .build();

    SettingValue settingValue = gitConfig;
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withName(SAMPLE_STRING)
                                            .withUuid(null)
                                            .withValue(settingValue)
                                            .build();

    GitConfig.Yaml yaml = yamlHandler.toYaml(settingAttribute, null);

    assertThat(yaml).isNotNull();
    assertThat(yaml.getCommitMessage()).isEqualTo(SAMPLE_STRING);
    assertThat(yaml.getAuthorEmailId()).isEqualTo(SAMPLE_STRING);
    assertThat(yaml.getAuthorName()).isEqualTo(SAMPLE_STRING);
    assertThat(yaml.getBranch()).isEqualTo(SAMPLE_STRING);
    assertThat(yaml.getDescription()).isEqualTo(SAMPLE_STRING);
    assertThat(yaml.getSshKeyName()).isNull();
    assertThat(yaml.getDelegateSelectors()).isEqualTo(Collections.emptyList());
    assertThat(yaml.getProviderType()).isEqualTo(ProviderType.GIT);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testToBean_1() {
    SettingAttribute prev = SettingAttribute.Builder.aSettingAttribute()
                                .withUuid("uuid")
                                .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
                                .withValue(GitConfig.builder().webhookToken("test").build())
                                .build();

    GitConfig.Yaml yaml = GitConfig.Yaml.builder()
                              .branch(SAMPLE_STRING)
                              .reference(SAMPLE_STRING)
                              .keyAuth(true)
                              .sshKeyName(null)
                              .authorName(SAMPLE_STRING)
                              .authorEmailId(SAMPLE_STRING)
                              .commitMessage(SAMPLE_STRING)
                              .description(SAMPLE_STRING)
                              .password(SAMPLE_STRING)
                              .username(SAMPLE_STRING)
                              .url(SAMPLE_STRING)
                              .delegateSelectors(Collections.emptyList())
                              .providerType(ProviderType.GIT)
                              .build();

    Change change = Change.Builder.aFileChange()
                        .withAccountId("ABC")
                        .withFilePath("Setup/Source Repo Provider/test-harness.yaml")
                        .build();
    ChangeContext<GitConfig.Yaml> changeContext = ChangeContext.Builder.aChangeContext()
                                                      .withYamlType(YamlType.SOURCE_REPO_PROVIDER)
                                                      .withYaml(yaml)
                                                      .withChange(change)
                                                      .build();

    SettingAttribute settingAttribute = yamlHandler.toBean(prev, changeContext, null);
    GitConfig gitConfig = (GitConfig) settingAttribute.getValue();

    assertThat(gitConfig).isNotNull();
    assertThat(gitConfig.getCommitMessage()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getAuthorEmailId()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getAuthorName()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getSshSettingId()).isNull();
    assertThat(gitConfig.getUsername()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getBranch()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getRepoUrl()).isEqualTo(SAMPLE_STRING);
    assertThat(gitConfig.getDelegateSelectors()).isEqualTo(Collections.emptyList());
    assertThat(gitConfig.getProviderType()).isEqualTo(ProviderType.GIT);
    assertThat(gitConfig.getWebhookToken()).isNotBlank();
    assertThat(settingAttribute.getUuid()).isNotEmpty();
  }
}
