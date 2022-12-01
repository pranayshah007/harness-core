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
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.migration.serviceenvmigrationv2.ServiceEnvironmentV2MigrationService;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationRequestDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.SvcEnvMigrationResponseDto;
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

  SvcEnvMigrationRequestDto requestDto;
  SvcEnvMigrationResponseDto expectedResponseDto;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    requestDto = SvcEnvMigrationRequestDto.builder()
                     .orgIdentifier(ORG_IDENTIFIER)
                     .projectIdentifier(PROJ_IDENTIFIER)
                     .pipelineIdentifier(PIPLINE_IDENTIFIER)
                     .infraIdentifierFormat(INFRA_IDENTIFIER_FORMAT)
                     .isUpdatePipeline(true)
                     .build();

    expectedResponseDto = SvcEnvMigrationResponseDto.builder().pipelineYaml(PIPELINE_V2_YAML).build();
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testMigrateOldServiceInfraFromStage() {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEnvironmentV2MigrationService.migratePipeline(requestDto, ACCOUNT_ID)).thenReturn(expectedResponseDto);
    SvcEnvMigrationResponseDto actualResponse =
        serviceEnvironmentV2MigrationResource.migratePipelineWithServiceInfraV2(ACCOUNT_ID, requestDto).getData();
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);
    assertThat(actualResponse).isEqualTo(expectedResponseDto);
  }
}
