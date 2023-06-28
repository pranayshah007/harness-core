/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.services.ServiceOverrideV2SettingsUpdateService;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.serviceoverridev2.beans.AccountLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OrgLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ProjectLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.MongoTemplate;

public class ServiceOverrideV2SettingsUpdateServiceImplTest extends CDNGTestBase {
  @Inject MongoTemplate mongoTemplate;

  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final List<String> projectIds = List.of("project0", "project1");

  @Inject ServiceOverrideV2SettingsUpdateService v2SettingsUpdateService;

  @Before
  public void setup() {
    Reflect.on(v2SettingsUpdateService).set("mongoTemplate", mongoTemplate);
    Mockito.mockStatic(NGRestUtils.class);
    when(NGRestUtils.getResponse(any())).thenReturn(null);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testProjectScopeSettingsUpdate() {
    createTestOrgAndProject();
    OverrideV2SettingsUpdateResponseDTO responseDTO =
        v2SettingsUpdateService.settingsUpdateToV2(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, projectIds.get(0), false, false);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    assertProjectLevelResponseDTO(responseDTO.getProjectLevelUpdateInfo(), 1);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testOrgScopeSettingsUpdate() {
    createTestOrgAndProject();
    OverrideV2SettingsUpdateResponseDTO responseDTO =
        v2SettingsUpdateService.settingsUpdateToV2(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, false, false);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    List<ProjectLevelOverrideV2SettingsUpdateResponseDTO> projectLevelInfoList =
        responseDTO.getProjectLevelUpdateInfo();
    assertThat(projectLevelInfoList).isEmpty();
    assertOrgLevelResponseDTO(responseDTO.getOrgLevelUpdateInfo());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testAccountScopeSettingsUpdate() {
    createTestOrgAndProject();
    OverrideV2SettingsUpdateResponseDTO responseDTO =
        v2SettingsUpdateService.settingsUpdateToV2(ACCOUNT_IDENTIFIER, null, null, false, false);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    assertThat(responseDTO.getProjectLevelUpdateInfo()).isEmpty();
    assertThat(responseDTO.getOrgLevelUpdateInfo()).isEmpty();

    AccountLevelOverrideV2SettingsUpdateResponseDTO accountResponseDto = responseDTO.getAccountLevelUpdateInfo();
    assertAccountResponseDTO(accountResponseDto);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testAccountLevelWithChildScopes() {
    createTestOrgAndProject();
    OverrideV2SettingsUpdateResponseDTO responseDTO =
        v2SettingsUpdateService.settingsUpdateToV2(ACCOUNT_IDENTIFIER, null, null, true, false);

    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    assertThat(responseDTO.getProjectLevelUpdateInfo()).isNotEmpty();
    assertThat(responseDTO.getOrgLevelUpdateInfo()).isNotEmpty();

    assertAccountResponseDTO(responseDTO.getAccountLevelUpdateInfo());
    assertOrgLevelResponseDTO(responseDTO.getOrgLevelUpdateInfo());
    assertProjectLevelResponseDTO(responseDTO.getProjectLevelUpdateInfo(), 2);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testOrgScopeSettingsUpdateRevert() {
    createTestOrgAndProject();
    OverrideV2SettingsUpdateResponseDTO responseDTO =
        v2SettingsUpdateService.settingsUpdateToV2(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, null, false, true);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.isSuccessful()).isTrue();
    List<ProjectLevelOverrideV2SettingsUpdateResponseDTO> projectLevelInfoList =
        responseDTO.getProjectLevelUpdateInfo();
    assertThat(projectLevelInfoList).isEmpty();
    assertOrgLevelSettingsUpdateRevertResponseDTO(responseDTO.getOrgLevelUpdateInfo());
  }

  private void assertAccountResponseDTO(AccountLevelOverrideV2SettingsUpdateResponseDTO accountResponseDto) {
    assertThat(accountResponseDto).isNotNull();
    assertThat(accountResponseDto.isSettingsUpdateSuccessFul()).isTrue();
  }

  private void assertOrgLevelResponseDTO(List<OrgLevelOverrideV2SettingsUpdateResponseDTO> orgLevelInfoList) {
    assertThat(orgLevelInfoList).isNotEmpty();
    assertThat(orgLevelInfoList).hasSize(1);
    OrgLevelOverrideV2SettingsUpdateResponseDTO orgResponseDTO = orgLevelInfoList.get(0);
    assertThat(orgResponseDTO.isSettingsUpdateSuccessFul()).isTrue();
  }

  private void assertProjectLevelResponseDTO(
      List<ProjectLevelOverrideV2SettingsUpdateResponseDTO> projectLevelInfoList, int projectsNumber) {
    assertThat(projectLevelInfoList).isNotEmpty();
    assertThat(projectLevelInfoList).hasSize(projectsNumber);
    ProjectLevelOverrideV2SettingsUpdateResponseDTO projectResponseDTO = projectLevelInfoList.get(0);
    assertThat(projectResponseDTO.isSettingsUpdateSuccessFul()).isTrue();
  }

  private void assertOrgLevelSettingsUpdateRevertResponseDTO(
      List<OrgLevelOverrideV2SettingsUpdateResponseDTO> orgLevelInfoList) {
    assertThat(orgLevelInfoList).isNotEmpty();
    assertThat(orgLevelInfoList).hasSize(1);
    OrgLevelOverrideV2SettingsUpdateResponseDTO orgResponseDTO = orgLevelInfoList.get(0);
    assertThat(orgResponseDTO.isSettingsUpdateSuccessFul()).isTrue();
  }

  private void createTestOrgAndProject() {
    mongoTemplate.save(Organization.builder()
                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                           .identifier(ORG_IDENTIFIER)
                           .name(ORG_IDENTIFIER)
                           .build());
    mongoTemplate.save(Project.builder()
                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                           .orgIdentifier(ORG_IDENTIFIER)
                           .identifier(projectIds.get(0))
                           .name(projectIds.get(0))
                           .build());

    mongoTemplate.save(Project.builder()
                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                           .orgIdentifier(ORG_IDENTIFIER)
                           .identifier(projectIds.get(1))
                           .name(projectIds.get(1))
                           .build());
  }
}