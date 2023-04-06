/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.mapper;

import static io.harness.EntityType.PIPELINES;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.eventsframework.schemas.entity.ScopeProtoEnum.ACCOUNT;
import static io.harness.eventsframework.schemas.entity.ScopeProtoEnum.PROJECT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType;
import io.harness.ng.core.entitysetupusage.entity.EntitySetupUsage;
import io.harness.ng.core.entitysetupusage.helper.GitInfoPopulatorForConnector;
import io.harness.ng.core.entitysetupusage.helper.SetupUsageGitInfoPopulator;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.PageUtils;

import com.google.protobuf.StringValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(DX)
public class EntitySetupUsageEventDTOMapperTest extends NgManagerTestBase {
  private final String accountIdentifier = "accountIdentifier";
  private final String orgIdentifier = "orgIdentifier";
  private final String projectIdentifier = "projectIdentifier";
  EntitySetupUsageEventDTOMapper entitySetupUsageEventDTOMapper;
  ConnectorService connectorService;
  YamlGitConfigService yamlGitConfigService;
  String repo = "repo";
  String branch = "branch";

  @Before
  public void setUp() throws Exception {
    yamlGitConfigService = mock(YamlGitConfigService.class);
    connectorService = mock(ConnectorService.class);
    GitInfoPopulatorForConnector gitInfoPopulatorForConnector = new GitInfoPopulatorForConnector(connectorService);
    entitySetupUsageEventDTOMapper =
        new EntitySetupUsageEventDTOMapper(new EntityDetailProtoToRestMapper(), new SetupUsageDetailProtoToRestMapper(),
            new SetupUsageGitInfoPopulator(gitInfoPopulatorForConnector, yamlGitConfigService));
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void toEntityDTO() {
    EntityDetailProtoDTO referredByEntity = createEntityDetailDTO("pipelineIdentifier", EntityTypeProtoEnum.PIPELINES);
    List<EntityDetailProtoDTO> referredEntityList = createListOfReferredEntities();
    List<EntityDetailWithSetupUsageDetailProtoDTO> referredEntityWithSetupUsage = createRecordsWithSetupUsageDetails();
    EntitySetupUsageCreateV2DTO createSetupUsageDTO =
        EntitySetupUsageCreateV2DTO.newBuilder()
            .setAccountIdentifier(accountIdentifier)
            .setReferredByEntity(referredByEntity)
            .addAllReferredEntities(referredEntityList)
            .setDeleteOldReferredByRecords(true)
            .addAllReferredEntityWithSetupUsageDetail(referredEntityWithSetupUsage)
            .build();
    List<EntitySetupUsage> entitySetupUsages = entitySetupUsageEventDTOMapper.toEntityDTO(createSetupUsageDTO);
    assertThat(entitySetupUsages.size()).isEqualTo(8);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void toEntityDTOWithGitBranchPopulated() {
    EntityDetailProtoDTO referredByEntity = createEntityDetailDTO("pipelineIdentifier", EntityTypeProtoEnum.PIPELINES);
    // Creating a list of project level referredEntities
    List<EntityDetailProtoDTO> referredEntityList = createListOfReferredEntities();
    // Creating a list of project level referredEntities with details
    List<EntityDetailWithSetupUsageDetailProtoDTO> referredEntityWithSetupUsage = createRecordsWithSetupUsageDetails();
    // Creating a list of account level referredEntities with details
    List<EntityDetailProtoDTO> accountLevelEntitiesReferenced = createRecordsForAccountLevelReferredEntities();
    referredEntityList.addAll(accountLevelEntitiesReferenced);

    EntitySetupUsageCreateV2DTO createSetupUsageDTO =
        EntitySetupUsageCreateV2DTO.newBuilder()
            .setAccountIdentifier(accountIdentifier)
            .setReferredByEntity(referredByEntity)
            .addAllReferredEntities(referredEntityList)
            .setDeleteOldReferredByRecords(true)
            .addAllReferredEntityWithSetupUsageDetail(referredEntityWithSetupUsage)
            .build();

    when(yamlGitConfigService.get(any(), any(), any(), any()))
        .thenReturn(YamlGitConfigDTO.builder().branch(branch).repo(repo).build());

    Page<ConnectorResponseDTO> mockedConnectorResponseDTO = createTheMockedConnectorResponse();
    Pageable pageable1 =
        PageUtils.getPageRequest(0, 100, List.of(ConnectorKeys.lastModifiedAt, Sort.Direction.DESC.toString()));
    Pageable pageable2 =
        PageUtils.getPageRequest(1, 100, List.of(ConnectorKeys.lastModifiedAt, Sort.Direction.DESC.toString()));
    when(connectorService.list(any(), any(), any(), any(), any(), any(), any(), (Boolean) any(), eq(pageable1)))
        .thenReturn(mockedConnectorResponseDTO);
    when(connectorService.list(any(), any(), any(), any(), any(), any(), any(), (Boolean) any(), eq(pageable2)))
        .thenReturn(Page.empty());
    final GitEntityInfo newBranch = GitEntityInfo.builder().branch(branch).yamlGitConfigId(repo).build();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(newBranch).build());
      List<EntitySetupUsage> entitySetupUsages = entitySetupUsageEventDTOMapper.toEntityDTO(createSetupUsageDTO);
      assertThat(entitySetupUsages.size()).isEqualTo(10);
      checkThatTheSetupUsageRecordsAreCorrect(entitySetupUsages);
    }
  }

  private void checkThatTheSetupUsageRecordsAreCorrect(List<EntitySetupUsage> entitySetupUsages) {
    checkTheReferredByEntityIsCorrect(entitySetupUsages.get(0).getReferredByEntity());
    checkTheReferredEntitiesAreCorrect(entitySetupUsages);
  }

  private void checkTheReferredEntitiesAreCorrect(List<EntitySetupUsage> entitySetupUsages) {
    Map<String, EntitySetupUsage> referredEntityFQNMap = new HashMap<>();
    for (EntitySetupUsage entitySetupUsage : entitySetupUsages) {
      referredEntityFQNMap.put(entitySetupUsage.getReferredEntity().getEntityRef().getIdentifier(), entitySetupUsage);
    }
    checkTheRepoBranchEntries(referredEntityFQNMap,
        Arrays.asList("connectorIdentifier0", "connectorIdentifierForDetail0"), repo, branch, true);
    checkTheRepoBranchEntries(referredEntityFQNMap,
        Arrays.asList("connectorIdentifier1", "connectorIdentifierForDetail1"), "repo1", "master", true);
    List<String> identifiersOfNotGitSyncedEntries =
        Arrays.asList("secretIdentifier0", "secretIdentifier1", "secretIdentifierForDetail0",
            "secretIdentifierForDetail1", "secretIdentifierForAccount0", "connectorIdentifierForAccount0");
    checkTheRepoBranchEntries(referredEntityFQNMap, identifiersOfNotGitSyncedEntries, null, null, true);
  }

  private void checkTheRepoBranchEntries(Map<String, EntitySetupUsage> referredEntityFQNMap, List<String> identifiers,
      String repo, String branch, Boolean isDefault) {
    for (String identifier : identifiers) {
      EntitySetupUsage entitySetupUsage = referredEntityFQNMap.get(identifier);
      final EntityReference entityRef = entitySetupUsage.getReferredEntity().getEntityRef();
      checkTheRepoAndBranchValueInRecords(entityRef, repo, branch, isDefault, entitySetupUsage);
    }
  }

  private void checkTheReferredByEntityIsCorrect(EntityDetail referredByEntity) {
    assertThat(referredByEntity.getType()).isEqualTo(PIPELINES);
    EntityReference pipelineRef = referredByEntity.getEntityRef();
    assertThat(pipelineRef.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(pipelineRef.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(pipelineRef.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(pipelineRef.getIdentifier()).isEqualTo("pipelineIdentifier");
    checkTheRepoAndBranchValue(pipelineRef, repo, branch, true);
  }

  private void checkTheRepoAndBranchValueInRecords(EntityReference entityReference, String repo, String branch,
      Boolean isDefault, EntitySetupUsage entitySetupUsage) {
    checkTheRepoAndBranchValue(entityReference, repo, branch, isDefault);
    assertThat(entitySetupUsage.getReferredEntityRepoIdentifier()).isEqualTo(repo);
    assertThat(entitySetupUsage.getReferredEntityBranch()).isEqualTo(branch);
    assertThat(entitySetupUsage.getReferredEntityIsDefault()).isEqualTo(isDefault);
  }

  private void checkTheRepoAndBranchValue(
      EntityReference entityReference, String repo, String branch, Boolean isDefault) {
    assertThat(entityReference.getRepoIdentifier()).isEqualTo(repo);
    assertThat(entityReference.getBranch()).isEqualTo(branch);
    assertThat(entityReference.isDefault()).isEqualTo(isDefault);
  }

  private Page<ConnectorResponseDTO> createTheMockedConnectorResponse() {
    ConnectorInfoDTO connectorInBranch1 = ConnectorInfoDTO.builder()
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier("connectorIdentifier0")
                                              .build();
    ConnectorInfoDTO connectorInBranch2 = ConnectorInfoDTO.builder()
                                              .orgIdentifier(orgIdentifier)
                                              .projectIdentifier(projectIdentifier)
                                              .identifier("connectorIdentifierForDetail0")
                                              .build();
    ConnectorInfoDTO connectorNotInBranch1 = ConnectorInfoDTO.builder()
                                                 .orgIdentifier(orgIdentifier)
                                                 .projectIdentifier(projectIdentifier)
                                                 .identifier("connectorIdentifier1")
                                                 .build();
    ConnectorInfoDTO connectorNotInBranch2 = ConnectorInfoDTO.builder()
                                                 .orgIdentifier(orgIdentifier)
                                                 .projectIdentifier(projectIdentifier)
                                                 .identifier("connectorIdentifierForDetail1")
                                                 .build();
    ConnectorResponseDTO connectorResponseDTOInBranch1 =
        ConnectorResponseDTO.builder()
            .connector(connectorInBranch1)
            .gitDetails(EntityGitDetails.builder().repoIdentifier("repo").branch("branch").build())
            .build();
    ConnectorResponseDTO connectorResponseDTOInBranch2 =
        ConnectorResponseDTO.builder()
            .connector(connectorInBranch2)
            .gitDetails(EntityGitDetails.builder().repoIdentifier("repo").branch("branch").build())
            .build();
    ConnectorResponseDTO connectorResponseDTONotInBranch1 =
        ConnectorResponseDTO.builder()
            .connector(connectorNotInBranch1)
            .gitDetails(EntityGitDetails.builder().repoIdentifier("repo1").branch("master").build())
            .build();
    ConnectorResponseDTO connectorResponseDTONotInBranch2 =
        ConnectorResponseDTO.builder()
            .connector(connectorNotInBranch2)
            .gitDetails(EntityGitDetails.builder().repoIdentifier("repo1").branch("master").build())
            .build();
    List<ConnectorResponseDTO> allConnectorResponse = Arrays.asList(connectorResponseDTOInBranch1,
        connectorResponseDTOInBranch2, connectorResponseDTONotInBranch1, connectorResponseDTONotInBranch2);
    return new PageImpl<>(allConnectorResponse);
  }

  private List<EntityDetailProtoDTO> createRecordsForAccountLevelReferredEntities() {
    List<EntityDetailProtoDTO> entityDetailProtoDTOS = new ArrayList<>();
    entityDetailProtoDTOS.add(
        createAccountLevelEntityDetailDTO("connectorIdentifierForAccount0", EntityTypeProtoEnum.CONNECTORS));
    entityDetailProtoDTOS.add(
        createAccountLevelEntityDetailDTO("secretIdentifierForAccount0", EntityTypeProtoEnum.SECRETS));
    return entityDetailProtoDTOS;
  }

  private List<EntityDetailWithSetupUsageDetailProtoDTO> createRecordsWithSetupUsageDetails() {
    List<EntityDetailWithSetupUsageDetailProtoDTO> allReferredEntities = new ArrayList<>();
    List<EntityDetailWithSetupUsageDetailProtoDTO> connectorEntityDetailProtoDTOList =
        createConnectorEntityWithDetailsList();
    List<EntityDetailWithSetupUsageDetailProtoDTO> secretEntityDetailProtoDTOList = createSecretEntityWithDetailsList();
    allReferredEntities.addAll(connectorEntityDetailProtoDTOList);
    allReferredEntities.addAll(secretEntityDetailProtoDTOList);
    return allReferredEntities;
  }

  private List<EntityDetailWithSetupUsageDetailProtoDTO> createSecretEntityWithDetailsList() {
    List<EntityDetailWithSetupUsageDetailProtoDTO> entityDetailProtoDTOS = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      // SECRET_REFERRED_BY_PIPELINE is being sent right now, if SECRET_REFERRED_BY_CONNECTOR is required, make
      // setupUsageDetailType a method parameter
      entityDetailProtoDTOS.add(createEntityDetailDTOWithDetails("secretIdentifierForDetail" + i,
          EntityTypeProtoEnum.SECRETS, SetupUsageDetailType.SECRET_REFERRED_BY_PIPELINE));
    }
    return entityDetailProtoDTOS;
  }

  private EntityDetailWithSetupUsageDetailProtoDTO createEntityDetailDTOWithDetails(
      String identifier, EntityTypeProtoEnum entityType, SetupUsageDetailType setupUsageDetailType) {
    return EntityDetailWithSetupUsageDetailProtoDTO.newBuilder()
        .setReferredEntity(createEntityDetailDTO(identifier, entityType))
        .setType(setupUsageDetailType.name())
        .build();
  }

  private List<EntityDetailWithSetupUsageDetailProtoDTO> createConnectorEntityWithDetailsList() {
    List<EntityDetailWithSetupUsageDetailProtoDTO> entityDetailProtoDTOS = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      entityDetailProtoDTOS.add(createEntityDetailDTOWithDetails("connectorIdentifierForDetail" + i,
          EntityTypeProtoEnum.CONNECTORS, SetupUsageDetailType.CONNECTOR_REFERRED_BY_PIPELINE));
    }
    return entityDetailProtoDTOS;
  }

  private List<EntityDetailProtoDTO> createListOfReferredEntities() {
    List<EntityDetailProtoDTO> allReferredEntities = new ArrayList<>();
    List<EntityDetailProtoDTO> connectorEntityDetailProtoDTOList = createConnectorEntityDetailsList();
    List<EntityDetailProtoDTO> secretEntityDetailProtoDTOList = createSecretEntityDetailsList();
    allReferredEntities.addAll(connectorEntityDetailProtoDTOList);
    allReferredEntities.addAll(secretEntityDetailProtoDTOList);
    return allReferredEntities;
  }

  private List<EntityDetailProtoDTO> createSecretEntityDetailsList() {
    List<EntityDetailProtoDTO> entityDetailProtoDTOS = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      entityDetailProtoDTOS.add(createEntityDetailDTO("secretIdentifier" + i, EntityTypeProtoEnum.SECRETS));
    }
    return entityDetailProtoDTOS;
  }

  private List<EntityDetailProtoDTO> createConnectorEntityDetailsList() {
    List<EntityDetailProtoDTO> entityDetailProtoDTOS = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      entityDetailProtoDTOS.add(createEntityDetailDTO("connectorIdentifier" + i, EntityTypeProtoEnum.CONNECTORS));
    }
    return entityDetailProtoDTOS;
  }

  private EntityDetailProtoDTO createEntityDetailDTO(String identifier, EntityTypeProtoEnum entityTypeProtoEnum) {
    IdentifierRefProtoDTO identifierRefProtoDTO = IdentifierRefProtoDTO.newBuilder()
                                                      .setAccountIdentifier(StringValue.of(accountIdentifier))
                                                      .setOrgIdentifier(StringValue.of(orgIdentifier))
                                                      .setProjectIdentifier(StringValue.of(projectIdentifier))
                                                      .setIdentifier(StringValue.of(identifier))
                                                      .setScope(PROJECT)
                                                      .build();
    return EntityDetailProtoDTO.newBuilder()
        .setIdentifierRef(identifierRefProtoDTO)
        .setType(entityTypeProtoEnum)
        .build();
  }

  private EntityDetailProtoDTO createAccountLevelEntityDetailDTO(
      String identifier, EntityTypeProtoEnum entityTypeProtoEnum) {
    IdentifierRefProtoDTO identifierRefProtoDTO = IdentifierRefProtoDTO.newBuilder()
                                                      .setAccountIdentifier(StringValue.of(accountIdentifier))
                                                      .setIdentifier(StringValue.of(identifier))
                                                      .setScope(ACCOUNT)
                                                      .build();
    return EntityDetailProtoDTO.newBuilder()
        .setIdentifierRef(identifierRefProtoDTO)
        .setType(entityTypeProtoEnum)
        .build();
  }
}
