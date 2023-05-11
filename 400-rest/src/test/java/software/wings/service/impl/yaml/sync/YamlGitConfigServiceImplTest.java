/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.sync;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.security.UserThreadLocal.userGuard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.security.AppPermissionSummary;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.yaml.sync.YamlGitConfigService;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class YamlGitConfigServiceImplTest extends WingsBaseTest {
  @Inject @InjectMocks YamlGitConfigService yamlGitConfigService;
  @Inject private HPersistence persistence;
  @Mock AccountService accountService;
  @Mock AuthService authService;
  @Mock AppService appService;
  String accountId = "accountId";

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_getGitConfigAccessibleToUserWithEntityName() {
    String accountName = "accountName";
    String appId = "appId";
    String applicationName = "applicationName";
    when(accountService.get(eq(accountId))).thenReturn(anAccount().withAccountName(accountName).build());
    Map<String, AppPermissionSummary> appsAllowed = new HashMap<>();
    appsAllowed.put(appId, AppPermissionSummary.builder().build());
    when(authService.getUserPermissionInfo(eq(accountId), any(), eq(false)))
        .thenReturn(UserPermissionInfo.builder().appPermissionMapInternal(appsAllowed).build());
    PageResponse<Application> appResponse =
        aPageResponse().withResponse(Arrays.asList(anApplication().uuid(appId).name(applicationName).build())).build();
    when(appService.list(any())).thenReturn(appResponse);
    try (UserThreadLocal.Guard guard = userGuard(anUser().uuid(generateUuid()).build())) {
      String gitConnectorId = "gitConnectorId";
      final YamlGitConfig yamlGitConfig = YamlGitConfig.builder()
                                              .accountId(accountId)
                                              .branchName("branchName")
                                              .enabled(true)
                                              .entityId(appId)
                                              .entityType(EntityType.APPLICATION)
                                              .gitConnectorId(gitConnectorId)
                                              .build();
      final YamlGitConfig yamlGitConfig1 = YamlGitConfig.builder()
                                               .accountId(accountId)
                                               .branchName("branchName")
                                               .enabled(true)
                                               .entityId(accountId)
                                               .entityType(EntityType.ACCOUNT)
                                               .gitConnectorId(gitConnectorId)
                                               .build();
      persistence.save(yamlGitConfig);
      persistence.save(yamlGitConfig1);
      List<YamlGitConfig> yamlGitConfigSet =
          yamlGitConfigService.getYamlGitConfigAccessibleToUserWithEntityName(accountId);
      assertThat(yamlGitConfigSet.size()).isEqualTo(2);
      List<YamlGitConfig> gitConfigList = new ArrayList<>(yamlGitConfigSet);
      YamlGitConfig gitConfig1 = gitConfigList.get(0);
      YamlGitConfig gitConfig2 = gitConfigList.get(1);
      YamlGitConfig accountLevelGitDetail = gitConfig1.getEntityType() == EntityType.ACCOUNT ? gitConfig1 : gitConfig2;
      YamlGitConfig appLevelGitDetail = gitConfig1.getEntityType() == EntityType.APPLICATION ? gitConfig1 : gitConfig2;

      assertThat(accountLevelGitDetail.getEntityName()).isEqualTo(accountName);
      assertThat(appLevelGitDetail.getEntityName()).isEqualTo(applicationName);
    }
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_getYamlGitConfigFromAppId() {
    String appId = "appId";
    String gitConnectorId = "gitConnectorId";
    final YamlGitConfig yamlGitConfig = YamlGitConfig.builder()
                                            .accountId(accountId)
                                            .branchName("branchName")
                                            .enabled(true)
                                            .appId(appId)
                                            .gitConnectorId(gitConnectorId)
                                            .build();

    persistence.save(yamlGitConfig);
    YamlGitConfig yamlGitConfigTest = yamlGitConfigService.getYamlGitConfigFromAppId(appId, accountId);
    YamlGitConfig yamlGitConfigTest1 = yamlGitConfigService.getYamlGitConfigFromAppId("appId1", accountId);
    assertThat(yamlGitConfigTest).isEqualTo(yamlGitConfig);
    assertThat(yamlGitConfigTest1).isNull();
  }
}
