package io.harness.ng.core.migration.serviceenvmigrationv2.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.migration.serviceenvmigrationv2.ServiceEnvironmentV2MigrationService;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.StageRequestDto;
import io.harness.ng.core.migration.serviceenvmigrationv2.dto.StageResponseDto;
import io.harness.rule.Owner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static io.harness.rule.OwnerRule.PRAGYESH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@OwnedBy(HarnessTeam.CDP)
public class ServiceEnvironmentV2MigrationResourceTest {
    @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
    @InjectMocks ServiceEnvironmentV2MigrationResource serviceEnvironmentV2MigrationResource;
    @Mock ServiceEnvironmentV2MigrationService serviceEnvironmentV2MigrationService;

    private final String ACCOUNT_ID = "account_01";
    private final String ORG_IDENTIFIER = "org_01";
    private final String PROJ_IDENTIFIER = "proj_01";
    private final String INFRA_IDENTIFIER = "infra_01";
    private final String STAGE_V1_YAML = "v1 yaml";
    private final String STAGE_V2_YAML = "v2 yaml";

    StageRequestDto stageRequestDto;
    StageResponseDto stageResponseDto;


    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        stageRequestDto = StageRequestDto.builder()
                .orgIdentifier(ORG_IDENTIFIER)
                .projectIdentifier(PROJ_IDENTIFIER)
                .infraIdentifier(INFRA_IDENTIFIER)
                .yaml(STAGE_V1_YAML)
                .build();

        stageResponseDto = StageResponseDto.builder()
                .yaml(STAGE_V2_YAML)
                .build();
    }

    @Test
    @Category(UnitTests.class)
    @Owner(developers = PRAGYESH)
    public void testMigrateOldServiceInfraFromStage() {
            when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
                    ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
                    .thenReturn(true);
            when(serviceEnvironmentV2MigrationService
                    .createServiceInfraV2(stageRequestDto, ACCOUNT_ID))
                    .thenReturn(STAGE_V2_YAML);
            StageResponseDto responseDto = serviceEnvironmentV2MigrationResource.migrateOldServiceInfraFromStage(ACCOUNT_ID, stageRequestDto).getData();
            verify(orgAndProjectValidationHelper, times(1))
                    .checkThatTheOrganizationAndProjectExists(ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);
        assertThat(responseDto).isEqualTo(stageResponseDto);
    }
}
