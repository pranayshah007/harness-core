/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.services.impl;

import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGEntitiesTestBase;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.data.structure.UUIDGenerator;
import io.harness.eventsframework.api.Producer;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentInputsMergedResponseDto;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.repositories.environment.spring.EnvironmentRepository;
import io.harness.rule.Owner;
import io.harness.setupusage.EnvironmentEntitySetupUsageHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.CDC)
@RunWith(JUnitParamsRunner.class)
public class EnvironmentServiceImplTest extends CDNGEntitiesTestBase {
  @Mock private InfrastructureEntityService infrastructureEntityService;
  @Mock private ClusterService clusterService;
  @Mock EntitySetupUsageService entitySetupUsageService;
  @Mock Producer producer;
  @Mock EnvironmentEntitySetupUsageHelper environmentEntitySetupUsageHelper;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private EnvironmentRepository environmentRepository;
  @Mock private EnvironmentRepository mockEnvironmentRepository;

  @Inject private OutboxService outboxService;
  @Mock private NGSettingsClient ngSettingsClient;
  @Mock private ServiceOverrideService serviceOverrideService;
  @Mock private ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Mock NGSettingsClient settingsClient;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @InjectMocks private EnvironmentServiceImpl environmentService;
  @InjectMocks private EnvironmentServiceImpl environmentServiceUsingMocks;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String ENV_ID = "ENV_ID";

  @Before
  public void setup() {
    Reflect.on(environmentService).set("transactionTemplate", transactionTemplate);
    Reflect.on(environmentService).set("environmentEntitySetupUsageHelper", environmentEntitySetupUsageHelper);
    Reflect.on(environmentService).set("entitySetupUsageService", entitySetupUsageService);
    Reflect.on(environmentService).set("environmentRepository", environmentRepository);
    Reflect.on(environmentService).set("outboxService", outboxService);
    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(new PageImpl<>(Collections.emptyList()));
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder().value("false").build();
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(settingValueResponseDTO);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(() -> environmentService.validatePresenceOfRequiredFields("", null, "2"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("One of the required fields is null.");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testForceDeleteAll() {
    Environment e1 = Environment.builder()
                         .accountId("ACCOUNT_ID")
                         .identifier(UUIDGenerator.generateUuid())
                         .orgIdentifier("ORG_ID")
                         .projectIdentifier("PROJECT_ID")
                         .build();
    Environment e2 = Environment.builder()
                         .accountId("ACCOUNT_ID")
                         .identifier(UUIDGenerator.generateUuid())
                         .orgIdentifier("ORG_ID")
                         .projectIdentifier("PROJECT_ID")
                         .build();

    // env from different project
    Environment e3 = Environment.builder()
                         .accountId("ACCOUNT_ID")
                         .identifier(UUIDGenerator.generateUuid())
                         .orgIdentifier("ORG_ID")
                         .projectIdentifier("PROJECT_ID_1")
                         .build();

    environmentService.create(e1);
    environmentService.create(e2);
    environmentService.create(e3);

    boolean deleted = environmentService.forceDeleteAllInProject("ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
    assertThat(deleted).isTrue();

    Optional<Environment> environment1 =
        environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", e1.getIdentifier(), false);
    Optional<Environment> environment2 =
        environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", e2.getIdentifier(), false);
    assertThat(environment1).isNotPresent();
    assertThat(environment2).isNotPresent();

    Optional<Environment> environment3 =
        environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID_1", e3.getIdentifier(), false);
    assertThat(environment3).isPresent();
    assertThat(environment3.get().getIdentifier()).isEqualTo(e3.getIdentifier());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testForceDeleteAllIdentifiersMustBeSpecified() {
    Environment e1 = Environment.builder()
                         .accountId("ACCOUNT_ID")
                         .identifier(UUIDGenerator.generateUuid())
                         .orgIdentifier("ORG_ID")
                         .projectIdentifier("PROJECT_ID")
                         .build();

    environmentService.create(e1);

    assertThatExceptionOfType(Exception.class)
        .isThrownBy(() -> environmentService.forceDeleteAllInProject("ACCOUNT_ID", "ORG_ID", null));
    assertThatExceptionOfType(Exception.class)
        .isThrownBy(() -> environmentService.forceDeleteAllInProject("ACCOUNT_ID", "ORG_ID", ""));
    assertThatExceptionOfType(Exception.class)
        .isThrownBy(() -> environmentService.forceDeleteAllInProject("ACCOUNT_ID", "", "PROJ_ID"));
    assertThatExceptionOfType(Exception.class)
        .isThrownBy(() -> environmentService.forceDeleteAllInProject("", "ORG_ID", "PROJ_ID"));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testForceDeleteAllOrgLevelEnvironments() {
    Environment e1 = Environment.builder()
                         .accountId("ACCOUNT_ID")
                         .identifier(UUIDGenerator.generateUuid())
                         .orgIdentifier("ORG_ID")
                         .build();
    Environment e2 = Environment.builder()
                         .accountId("ACCOUNT_ID")
                         .identifier(UUIDGenerator.generateUuid())
                         .orgIdentifier("ORG_ID")
                         .build();

    // env from different org
    Environment e3 = Environment.builder()
                         .accountId("ACCOUNT_ID")
                         .identifier(UUIDGenerator.generateUuid())
                         .orgIdentifier("ORG_ID_1")
                         .build();

    environmentService.create(e1);
    environmentService.create(e2);
    environmentService.create(e3);

    boolean deleted = environmentService.forceDeleteAllInOrg("ACCOUNT_ID", "ORG_ID");
    assertThat(deleted).isTrue();

    Optional<Environment> environment1 =
        environmentService.get("ACCOUNT_ID", "ORG_ID", null, e1.getIdentifier(), false);
    Optional<Environment> environment2 =
        environmentService.get("ACCOUNT_ID", "ORG_ID", null, e2.getIdentifier(), false);
    assertThat(environment1).isNotPresent();
    assertThat(environment2).isNotPresent();

    Optional<Environment> environment3 =
        environmentService.get("ACCOUNT_ID", "ORG_ID_1", null, e3.getIdentifier(), false);
    assertThat(environment3).isPresent();
    assertThat(environment3.get().getIdentifier()).isEqualTo(e3.getIdentifier());
    verify(producer, times(5)).send(any());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDelete() {
    final String id = UUIDGenerator.generateUuid();
    Environment createEnvironmentRequest = Environment.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier(id)
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("PROJECT_ID")
                                               .build();
    Environment createdEnvironment = environmentService.create(createEnvironmentRequest);

    boolean deleted = environmentService.delete("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", id, 0L, false);
    assertThat(deleted).isTrue();

    Optional<Environment> environment = environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", id, true);
    assertThat(environment).isNotPresent();
    environment = environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", id, false);
    assertThat(environment).isNotPresent();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDeleteIfSetUpUsagesPresent() {
    final String id = UUIDGenerator.generateUuid();
    Environment createEnvironmentRequest = Environment.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier(id)
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("PROJECT_ID")
                                               .build();
    Environment createdEnvironment = environmentService.create(createEnvironmentRequest);

    List<EntitySetupUsageDTO> referencedByEntities =
        Arrays.asList(EntitySetupUsageDTO.builder().referredByEntity(EntityDetail.builder().build()).build());
    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(new PageImpl<>(referencedByEntities));

    assertThatThrownBy(() -> environmentService.delete("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", id, 0L, false))
        .isInstanceOf(ReferencedEntityException.class)
        .hasMessageContaining(format(
            "The environment %s cannot be deleted because it is being referenced in 1 entity. To delete your environment, please remove the environment references from these entities.",
            id));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDeleteWhenDoesNotExist() {
    final String id = UUIDGenerator.generateUuid();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> environmentService.delete("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", id, 0L, false));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> environmentService.delete("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", id, 0L, false));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testEnvironmentServiceLayer() {
    Environment createEnvironmentRequest = Environment.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier("IDENTIFIER")
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("PROJECT_ID")
                                               .build();

    // Create operations
    Environment createdEnvironment = environmentService.create(createEnvironmentRequest);
    assertThat(createdEnvironment).isNotNull();
    assertThat(createdEnvironment.getAccountId()).isEqualTo(createEnvironmentRequest.getAccountId());
    assertThat(createdEnvironment.getOrgIdentifier()).isEqualTo(createEnvironmentRequest.getOrgIdentifier());
    assertThat(createdEnvironment.getProjectIdentifier()).isEqualTo(createEnvironmentRequest.getProjectIdentifier());
    assertThat(createdEnvironment.getIdentifier()).isEqualTo(createEnvironmentRequest.getIdentifier());
    assertThat(createdEnvironment.getName()).isEqualTo(createEnvironmentRequest.getIdentifier());
    assertThat(createdEnvironment.getVersion()).isEqualTo(0L);

    // Get Operations
    Optional<Environment> getEnvironment =
        environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER", false);
    assertThat(getEnvironment).isPresent();
    assertThat(getEnvironment.get()).isEqualTo(createdEnvironment);

    // Update Operations
    Environment updateEnvironmentRequest = Environment.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier("IDENTIFIER")
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("PROJECT_ID")
                                               .name("UPDATED_ENV")
                                               .description("NEW_DESCRIPTION")
                                               .build();

    Environment updatedEnvironment = environmentService.update(updateEnvironmentRequest);
    assertThat(updatedEnvironment).isNotNull();
    assertThat(updatedEnvironment.getAccountId()).isEqualTo(updateEnvironmentRequest.getAccountId());
    assertThat(updatedEnvironment.getOrgIdentifier()).isEqualTo(updateEnvironmentRequest.getOrgIdentifier());
    assertThat(updatedEnvironment.getProjectIdentifier()).isEqualTo(updateEnvironmentRequest.getProjectIdentifier());
    assertThat(updatedEnvironment.getIdentifier()).isEqualTo(updateEnvironmentRequest.getIdentifier());
    assertThat(updatedEnvironment.getName()).isEqualTo(updateEnvironmentRequest.getName());
    assertThat(updatedEnvironment.getDescription()).isEqualTo(updateEnvironmentRequest.getDescription());
    assertThat(updatedEnvironment.getVersion()).isEqualTo(1L);

    updateEnvironmentRequest.setIdentifier("NEW_ENV");
    assertThatThrownBy(() -> environmentService.update(updateEnvironmentRequest))
        .isInstanceOf(InvalidRequestException.class);
    updatedEnvironment.setIdentifier("IDENTIFIER");

    // Upsert operations
    Environment upsertEnvironmentRequest = Environment.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier("NEW_ENV")
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("NEW_PROJECT")
                                               .name("UPSERTED_ENV")
                                               .description("NEW_DESCRIPTION")
                                               .build();
    Environment upsertEnv = environmentService.upsert(upsertEnvironmentRequest, UpsertOptions.DEFAULT);
    assertThat(upsertEnv).isNotNull();
    assertThat(upsertEnv.getAccountId()).isEqualTo(upsertEnvironmentRequest.getAccountId());
    assertThat(upsertEnv.getOrgIdentifier()).isEqualTo(upsertEnvironmentRequest.getOrgIdentifier());
    assertThat(upsertEnv.getProjectIdentifier()).isEqualTo(upsertEnvironmentRequest.getProjectIdentifier());
    assertThat(upsertEnv.getIdentifier()).isEqualTo(upsertEnvironmentRequest.getIdentifier());
    assertThat(upsertEnv.getName()).isEqualTo(upsertEnvironmentRequest.getName());
    assertThat(upsertEnv.getDescription()).isEqualTo(upsertEnvironmentRequest.getDescription());

    // List services operations.
    Criteria criteriaFromFilter =
        CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", false);
    Pageable pageRequest = PageUtils.getPageRequest(0, 100, null);
    Page<Environment> list = environmentService.list(criteriaFromFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);
    assertThat(EnvironmentMapper.writeDTO(list.getContent().get(0)))
        .isEqualTo(EnvironmentMapper.writeDTO(updatedEnvironment));

    criteriaFromFilter = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", null, false);
    pageRequest = PageUtils.getPageRequest(0, 100, null);

    list = environmentService.list(criteriaFromFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(0);

    // Upsert operations in org level
    Environment upsertEnvironmentRequestOrgLevel = Environment.builder()
                                                       .accountId("ACCOUNT_ID")
                                                       .identifier("NEW_ENV")
                                                       .orgIdentifier("ORG_ID")
                                                       .name("UPSERTED_ENV")
                                                       .description("NEW_DESCRIPTION")
                                                       .build();
    upsertEnv = environmentService.upsert(upsertEnvironmentRequestOrgLevel, UpsertOptions.DEFAULT);

    assertThat(upsertEnv).isNotNull();
    assertThat(upsertEnv.getAccountId()).isEqualTo(upsertEnvironmentRequest.getAccountId());
    assertThat(upsertEnv.getOrgIdentifier()).isEqualTo(upsertEnvironmentRequest.getOrgIdentifier());
    assertThat(upsertEnv.getProjectIdentifier()).isNull();
    assertThat(upsertEnv.getIdentifier()).isEqualTo(upsertEnvironmentRequest.getIdentifier());
    assertThat(upsertEnv.getName()).isEqualTo(upsertEnvironmentRequest.getName());
    assertThat(upsertEnv.getDescription()).isEqualTo(upsertEnvironmentRequest.getDescription());

    criteriaFromFilter = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", null, false);
    pageRequest = PageUtils.getPageRequest(0, 100, null);

    list = environmentService.list(criteriaFromFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);
    List<EnvironmentResponseDTO> dtoList =
        list.getContent().stream().map(EnvironmentMapper::writeDTO).collect(Collectors.toList());
    assertThat(dtoList).containsOnly(EnvironmentMapper.writeDTO(upsertEnv));

    // Delete operations
    boolean delete = environmentService.delete("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER", 1L, false);
    assertThat(delete).isTrue();

    Optional<Environment> deletedEnvironment =
        environmentService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "UPDATED_ENV", false);
    assertThat(deletedEnvironment.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testUpsertWithoutOutbox() {
    Environment createEnvironmentRequest = Environment.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier(UUIDGenerator.generateUuid())
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("PROJECT_ID")
                                               .build();

    Environment createdEnvironment = environmentService.create(createEnvironmentRequest);

    Environment upsertRequest = Environment.builder()
                                    .accountId("ACCOUNT_ID")
                                    .identifier(createdEnvironment.getIdentifier())
                                    .orgIdentifier("ORG_ID")
                                    .projectIdentifier("PROJECT_ID")
                                    .name("UPSERTED_ENV")
                                    .description("NEW_DESCRIPTION")
                                    .build();

    Environment upsertedEnv = environmentService.upsert(upsertRequest, UpsertOptions.DEFAULT.withNoOutbox());

    assertThat(upsertedEnv).isNotNull();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateEnvironmentInputs() {
    String filename = "env-with-runtime-inputs.yaml";
    String yaml = readFile(filename);
    Environment createEnvironmentRequest = Environment.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier("IDENTIFIER")
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("PROJECT_ID")
                                               .yaml(yaml)
                                               .build();

    environmentService.create(createEnvironmentRequest);

    String environmentInputsYaml =
        environmentService.createEnvironmentInputsYaml("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER");
    String resFile = "env-with-runtime-inputs-res.yaml";
    String resInputs = readFile(resFile);
    assertThat(environmentInputsYaml).isEqualTo(resInputs);

    String updateYaml = readFile("env-without-runtime-inputs.yaml");
    Environment updateEnvironmentRequest = Environment.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier("IDENTIFIER")
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("PROJECT_ID")
                                               .yaml(updateYaml)
                                               .build();

    environmentService.update(updateEnvironmentRequest);
    String environmentInputsYaml2 =
        environmentService.createEnvironmentInputsYaml("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER");
    assertThat(environmentInputsYaml2).isNull();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCreateEnvironmentInputsErrorCases() {
    assertThatThrownBy(
        () -> environmentService.createEnvironmentInputsYaml("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage(
            "Environment with identifier [IDENTIFIER] in project [PROJECT_ID], org [ORG_ID], account [ACCOUNT_ID] scope not found");

    assertThatThrownBy(() -> environmentService.createEnvironmentInputsYaml("ACCOUNT_ID", "ORG_ID", "", "IDENTIFIER"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Environment with identifier [IDENTIFIER] in org [ORG_ID], account [ACCOUNT_ID] scope not found");

    assertThatThrownBy(() -> environmentService.createEnvironmentInputsYaml("ACCOUNT_ID", null, "", "IDENTIFIER"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Environment with identifier [IDENTIFIER] in account [ACCOUNT_ID] scope not found");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  @Parameters(method = "data")
  public void testMergeEnvironmentInputs(String pipelineInputYamlPath, String actualEntityYamlPath,
      String mergedInputYamlPath, boolean isMergedYamlEmpty) {
    String yaml = readFile(actualEntityYamlPath);
    Environment createRequest = Environment.builder()
                                    .accountId(ACCOUNT_ID)
                                    .orgIdentifier(ORG_ID)
                                    .projectIdentifier(PROJECT_ID)
                                    .name(ENV_ID)
                                    .identifier(ENV_ID)
                                    .yaml(yaml)
                                    .build();

    environmentService.create(createRequest);

    String oldTemplateInputYaml = readFile(pipelineInputYamlPath);
    String mergedTemplateInputsYaml = readFile(mergedInputYamlPath);
    EnvironmentInputsMergedResponseDto responseDto =
        environmentService.mergeEnvironmentInputs(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, oldTemplateInputYaml);
    String mergedYaml = responseDto.getMergedEnvironmentInputsYaml();
    if (isMergedYamlEmpty) {
      assertThat(mergedYaml).isEmpty();
    } else {
      assertThat(mergedYaml).isNotNull().isNotEmpty();
      assertThat(mergedYaml).isEqualTo(mergedTemplateInputsYaml);
    }
    assertThat(responseDto.getEnvironmentYaml()).isNotNull().isNotEmpty().isEqualTo(yaml);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testFetchesNonDeletedEnvironmentFromListOfRefs() {
    List<String> envRefs = Arrays.asList("env1", "org.env2", "account.env3");
    Criteria projectCriteria = Criteria.where(EnvironmentKeys.accountId)
                                   .is(ACCOUNT_ID)
                                   .and(EnvironmentKeys.orgIdentifier)
                                   .is(ORG_ID)
                                   .and(EnvironmentKeys.projectIdentifier)
                                   .is(PROJECT_ID)
                                   .and(EnvironmentKeys.identifier)
                                   .in(Arrays.asList("env1"));

    Criteria orgCriteria = Criteria.where(EnvironmentKeys.accountId)
                               .is(ACCOUNT_ID)
                               .and(EnvironmentKeys.orgIdentifier)
                               .is(ORG_ID)
                               .and(EnvironmentKeys.projectIdentifier)
                               .is(null)
                               .and(EnvironmentKeys.identifier)
                               .in(Arrays.asList("env2"));

    Criteria accountCriteria = Criteria.where(EnvironmentKeys.accountId)
                                   .is(ACCOUNT_ID)
                                   .and(EnvironmentKeys.orgIdentifier)
                                   .is(null)
                                   .and(EnvironmentKeys.projectIdentifier)
                                   .is(null)
                                   .and(EnvironmentKeys.identifier)
                                   .in(Arrays.asList("env3"));

    Environment projectEnv = Environment.builder().build();
    Environment orgEnv = Environment.builder().build();
    Environment accEnv = Environment.builder().build();

    doReturn(Arrays.asList(projectEnv))
        .when(mockEnvironmentRepository)
        .fetchesNonDeletedEnvironmentFromListOfIdentifiers(projectCriteria);
    doReturn(Arrays.asList(orgEnv))
        .when(mockEnvironmentRepository)
        .fetchesNonDeletedEnvironmentFromListOfIdentifiers(orgCriteria);
    doReturn(Arrays.asList(accEnv))
        .when(mockEnvironmentRepository)
        .fetchesNonDeletedEnvironmentFromListOfIdentifiers(accountCriteria);

    List<Environment> environments = environmentServiceUsingMocks.fetchesNonDeletedEnvironmentFromListOfRefs(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, envRefs);

    assertThat(environments).hasSize(3);
    assertThat(environments).contains(accEnv);
    assertThat(environments).contains(orgEnv);
    assertThat(environments).contains(projectEnv);
  }

  private Object[][] data() {
    return new Object[][] {{"environment/env-inputs-in-pipeline.yaml", "environment/env-with-few-inputs.yaml",
                               "environment/environmentInput-merged.yaml", false},
        {"environment/env-inputs-in-pipeline.yaml", "environment/env-with-no-inputs.yaml",
            "infrastructure/empty-file.yaml", true},
        {"infrastructure/empty-file.yaml", "environment/env-with-few-inputs.yaml",
            "environment/environmentInput-merged.yaml", false}};
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }
}
