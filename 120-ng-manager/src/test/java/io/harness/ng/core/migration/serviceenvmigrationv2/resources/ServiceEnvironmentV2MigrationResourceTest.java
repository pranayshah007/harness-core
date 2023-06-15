/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.serviceenvmigrationv2.resources;

import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.migration.serviceenvmigrationv2.ServiceEnvironmentV2MigrationService;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationProjectWrapperRequestDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationProjectWrapperResponseDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationRequestDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationResponseDto;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ServiceEnvironmentV2MigrationResourceTest extends CategoryTest {
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @InjectMocks ServiceEnvironmentV2MigrationResource serviceEnvironmentV2MigrationResource;
  @Mock ServiceEnvironmentV2MigrationService serviceEnvironmentV2MigrationService;

  private final String ACCOUNT_ID = "account_01";
  private final String ORG_IDENTIFIER = "org_01";
  private final String PROJ_IDENTIFIER = "proj_01";
  private final String PIPLINE_IDENTIFIER = "pipeline_01";
  private final String INFRA_IDENTIFIER_FORMAT = "<+environment.identifier>_infra";
  private final String PIPELINE_V2_YAML = "v2 yaml";

  SvcEnvMigrationRequestDto svcEnvMigrationRequestDto;
  SvcEnvMigrationResponseDto expectedSvcEnvMigrationResponseDto;

  SvcEnvMigrationProjectWrapperRequestDto svcEnvMigrationProjectWrapperRequestDto;
  SvcEnvMigrationProjectWrapperResponseDto expectedSvcEnvMigrationProjectWrapperResponseDto;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    svcEnvMigrationRequestDto = SvcEnvMigrationRequestDto.builder()
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .projectIdentifier(PROJ_IDENTIFIER)
                                    .pipelineIdentifier(PIPLINE_IDENTIFIER)
                                    .infraIdentifierFormat(INFRA_IDENTIFIER_FORMAT)
                                    .isUpdatePipeline(true)
                                    .build();
    svcEnvMigrationProjectWrapperRequestDto = SvcEnvMigrationProjectWrapperRequestDto.builder()
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .infraIdentifierFormat(INFRA_IDENTIFIER_FORMAT)
                                                  .isUpdatePipeline(true)
                                                  .build();

    expectedSvcEnvMigrationResponseDto = SvcEnvMigrationResponseDto.builder().pipelineYaml(PIPELINE_V2_YAML).build();
    expectedSvcEnvMigrationProjectWrapperResponseDto = SvcEnvMigrationProjectWrapperResponseDto.builder().build();
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testMigratePipeline() {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEnvironmentV2MigrationService.migratePipeline(svcEnvMigrationRequestDto, ACCOUNT_ID))
        .thenReturn(expectedSvcEnvMigrationResponseDto);
    SvcEnvMigrationResponseDto actualResponse =
        serviceEnvironmentV2MigrationResource.migratePipelineWithServiceInfraV2(ACCOUNT_ID, svcEnvMigrationRequestDto)
            .getData();
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);
    assertThat(actualResponse).isEqualTo(expectedSvcEnvMigrationResponseDto);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testMigrateProject() {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEnvironmentV2MigrationService.migrateProject(svcEnvMigrationProjectWrapperRequestDto, ACCOUNT_ID))
        .thenReturn(expectedSvcEnvMigrationProjectWrapperResponseDto);
    SvcEnvMigrationProjectWrapperResponseDto actualResponse =
        serviceEnvironmentV2MigrationResource.migrateProject(ACCOUNT_ID, svcEnvMigrationProjectWrapperRequestDto)
            .getData();
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);
    assertThat(actualResponse).isEqualTo(expectedSvcEnvMigrationProjectWrapperResponseDto);
  }
}
