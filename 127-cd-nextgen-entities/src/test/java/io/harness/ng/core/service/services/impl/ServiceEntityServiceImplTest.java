/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services.impl;

import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.MOHIT_GARG;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.YOGESH;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGEntitiesTestBase;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.exception.YamlException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.impl.EntitySetupUsageServiceImpl;
import io.harness.ng.core.events.ServiceCreateEvent;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ArtifactSourcesResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceInputsMergedResponseDto;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.mappers.ServiceFilterHelper;
import io.harness.ng.core.service.services.validators.NoOpServiceEntityValidator;
import io.harness.ng.core.service.services.validators.ServiceEntityValidatorFactory;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.template.RefreshRequestDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.exception.NGTemplateResolveExceptionV2;
import io.harness.ng.core.template.refresh.ErrorNodeSummary;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.yaml.YamlNode;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.repositories.service.spring.ServiceRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.spec.server.ng.v1.model.ManifestsResponseDTO;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
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
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
@RunWith(JUnitParamsRunner.class)
public class ServiceEntityServiceImplTest extends CDNGEntitiesTestBase {
  @Mock private OutboxService outboxService;
  @Mock private EntitySetupUsageServiceImpl entitySetupUsageService;
  @Mock private ServiceOverrideService serviceOverrideService;
  @Mock private ServiceEntitySetupUsageHelper entitySetupUsageHelper;
  @Mock private ServiceEntityValidatorFactory serviceEntityValidatorFactory;
  @Mock private NoOpServiceEntityValidator noOpServiceEntityValidator;
  @Mock private ServiceRepository serviceRepository;

  @Mock private TemplateResourceClient templateResourceClient;
  @Mock NGSettingsClient settingsClient;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;

  @Inject @InjectMocks private ServiceEntityServiceImpl serviceEntityService;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String SERVICE_ID = "serviceId";

  @Before
  public void setup() {
    entitySetupUsageService = mock(EntitySetupUsageServiceImpl.class);
    Reflect.on(serviceEntityService).set("entitySetupUsageService", entitySetupUsageService);
    Reflect.on(serviceEntityService).set("outboxService", outboxService);
    Reflect.on(serviceEntityService).set("serviceOverrideService", serviceOverrideService);
    Reflect.on(serviceEntityService).set("entitySetupUsageHelper", entitySetupUsageHelper);
    Reflect.on(serviceEntityService).set("serviceEntityValidatorFactory", serviceEntityValidatorFactory);
    Reflect.on(serviceEntityService).set("templateResourceClient", templateResourceClient);
    Reflect.on(serviceEntityService).set("overrideV2ValidationHelper", overrideV2ValidationHelper);
    when(serviceEntityValidatorFactory.getServiceEntityValidator(any())).thenReturn(noOpServiceEntityValidator);
  }

  private Object[][] data() {
    return new Object[][] {
        {"service/serviceInputs-with-few-values-fixed.yaml", "service/service-with-primaryArtifactRef-runtime.yaml",
            "service/serviceInputs-merged.yaml", false},
        {"service/serviceInputs-with-few-values-fixed.yaml", "service/service-with-no-runtime-input.yaml",
            "infrastructure/empty-file.yaml", true},
        {"infrastructure/empty-file.yaml", "service/service-with-primaryArtifactRef-fixed.yaml",
            "service/merged-service-input-fixed-prime-artifact.yaml", false}};
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(() -> serviceEntityService.validatePresenceOfRequiredFields("", null, "2"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("One of the required fields is null.");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testServiceLayer() {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId("ACCOUNT_ID")
                                      .identifier("IDENTIFIER")
                                      .orgIdentifier("ORG_ID")
                                      .projectIdentifier("PROJECT_ID")
                                      .name("Service")
                                      .type(ServiceDefinitionType.NATIVE_HELM)
                                      .gitOpsEnabled(true)
                                      .build();

    // Create operations
    ServiceEntity createdService = serviceEntityService.create(serviceEntity);
    assertThat(createdService).isNotNull();
    assertThat(createdService.getAccountId()).isEqualTo(serviceEntity.getAccountId());
    assertThat(createdService.getOrgIdentifier()).isEqualTo(serviceEntity.getOrgIdentifier());
    assertThat(createdService.getProjectIdentifier()).isEqualTo(serviceEntity.getProjectIdentifier());
    assertThat(createdService.getIdentifier()).isEqualTo(serviceEntity.getIdentifier());
    assertThat(createdService.getName()).isEqualTo(serviceEntity.getName());
    assertThat(createdService.getType()).isEqualTo(serviceEntity.getType());
    assertThat(createdService.getGitOpsEnabled()).isEqualTo(serviceEntity.getGitOpsEnabled());
    assertThat(createdService.getVersion()).isZero();

    // Get operations
    Optional<ServiceEntity> getService =
        serviceEntityService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER", false);
    assertThat(getService).isPresent();
    assertThat(getService.get()).isEqualTo(createdService);

    // Update operations
    ServiceEntity updateServiceRequest = ServiceEntity.builder()
                                             .accountId("ACCOUNT_ID")
                                             .identifier("IDENTIFIER")
                                             .orgIdentifier("ORG_ID")
                                             .projectIdentifier("PROJECT_ID")
                                             .name("UPDATED_SERVICE")
                                             .description("NEW_DESCRIPTION")
                                             .type(ServiceDefinitionType.NATIVE_HELM)
                                             .gitOpsEnabled(true)
                                             .build();
    ServiceEntity updatedServiceResponse = serviceEntityService.update(updateServiceRequest);
    assertThat(updatedServiceResponse.getAccountId()).isEqualTo(updateServiceRequest.getAccountId());
    assertThat(updatedServiceResponse.getOrgIdentifier()).isEqualTo(updateServiceRequest.getOrgIdentifier());
    assertThat(updatedServiceResponse.getProjectIdentifier()).isEqualTo(updateServiceRequest.getProjectIdentifier());
    assertThat(updatedServiceResponse.getIdentifier()).isEqualTo(updateServiceRequest.getIdentifier());
    assertThat(updatedServiceResponse.getName()).isEqualTo(updateServiceRequest.getName());
    assertThat(updatedServiceResponse.getDescription()).isEqualTo(updateServiceRequest.getDescription());
    assertThat(updatedServiceResponse.getGitOpsEnabled()).isEqualTo(updateServiceRequest.getGitOpsEnabled());
    assertThat(updatedServiceResponse.getVersion()).isEqualTo(1L);

    updateServiceRequest.setAccountId("NEW_ACCOUNT");
    assertThatThrownBy(() -> serviceEntityService.update(updateServiceRequest))
        .isInstanceOf(InvalidRequestException.class);
    updatedServiceResponse.setAccountId("ACCOUNT_ID");

    // adding test for 'Deployment Type is not allowed to change'
    updateServiceRequest.setType(ServiceDefinitionType.KUBERNETES);
    assertThatThrownBy(() -> serviceEntityService.update(updateServiceRequest))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(updatedServiceResponse.getType()).isNotEqualTo(ServiceDefinitionType.KUBERNETES);

    // adding test for 'GitOps Enabled is not allowed to change'
    updateServiceRequest.setGitOpsEnabled(false);
    assertThatThrownBy(() -> serviceEntityService.update(updateServiceRequest))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(updatedServiceResponse.getGitOpsEnabled()).isNotEqualTo(updateServiceRequest.getGitOpsEnabled());

    // Upsert operations
    ServiceEntity upsertServiceRequest = ServiceEntity.builder()
                                             .accountId("ACCOUNT_ID")
                                             .identifier("NEW_IDENTIFIER")
                                             .orgIdentifier("ORG_ID")
                                             .projectIdentifier("NEW_PROJECT")
                                             .name("UPSERTED_SERVICE")
                                             .description("NEW_DESCRIPTION")
                                             .type(ServiceDefinitionType.NATIVE_HELM)
                                             .build();
    ServiceEntity upsertService = serviceEntityService.upsert(upsertServiceRequest, UpsertOptions.DEFAULT);
    assertThat(upsertService.getAccountId()).isEqualTo(upsertServiceRequest.getAccountId());
    assertThat(upsertService.getOrgIdentifier()).isEqualTo(upsertServiceRequest.getOrgIdentifier());
    assertThat(upsertService.getProjectIdentifier()).isEqualTo(upsertServiceRequest.getProjectIdentifier());
    assertThat(upsertService.getIdentifier()).isEqualTo(upsertServiceRequest.getIdentifier());
    assertThat(upsertService.getName()).isEqualTo(upsertServiceRequest.getName());
    assertThat(upsertService.getDescription()).isEqualTo(upsertServiceRequest.getDescription());
    assertThat(upsertService.getType()).isEqualTo(upsertServiceRequest.getType());

    // Upsert operations // update via Upsert
    upsertServiceRequest = ServiceEntity.builder()
                               .accountId("ACCOUNT_ID")
                               .identifier("NEW_IDENTIFIER")
                               .orgIdentifier("ORG_ID")
                               .projectIdentifier("NEW_PROJECT")
                               .name("UPSERTED_SERVICE")
                               .description("NEW_DESCRIPTION")
                               .type(ServiceDefinitionType.NATIVE_HELM)
                               .build();
    upsertService = serviceEntityService.upsert(upsertServiceRequest, UpsertOptions.DEFAULT);
    assertThat(upsertService.getAccountId()).isEqualTo(upsertServiceRequest.getAccountId());
    assertThat(upsertService.getOrgIdentifier()).isEqualTo(upsertServiceRequest.getOrgIdentifier());
    assertThat(upsertService.getProjectIdentifier()).isEqualTo(upsertServiceRequest.getProjectIdentifier());
    assertThat(upsertService.getIdentifier()).isEqualTo(upsertServiceRequest.getIdentifier());
    assertThat(upsertService.getName()).isEqualTo(upsertServiceRequest.getName());
    assertThat(upsertService.getDescription()).isEqualTo(upsertServiceRequest.getDescription());
    assertThat(upsertService.getType()).isEqualTo(upsertServiceRequest.getType());

    // adding test for 'Deployment Type is not allowed to change'
    upsertServiceRequest.setType(ServiceDefinitionType.KUBERNETES);
    ServiceEntity finalUpsertServiceRequest = upsertServiceRequest;
    assertThatThrownBy(() -> serviceEntityService.upsert(finalUpsertServiceRequest, UpsertOptions.DEFAULT))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(upsertService.getType()).isNotEqualTo(ServiceDefinitionType.KUBERNETES);

    // List services operations.
    Criteria criteriaFromServiceFilter =
        CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", false);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<ServiceEntity> list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);
    assertThat(ServiceElementMapper.writeDTO(list.getContent().get(0)))
        .isEqualTo(ServiceElementMapper.writeDTO(updatedServiceResponse));

    criteriaFromServiceFilter = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", null, false);

    list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isZero();

    // Upsert operations for org level
    ServiceEntity upsertServiceRequestOrgLevel = ServiceEntity.builder()
                                                     .accountId("ACCOUNT_ID")
                                                     .identifier("NEW_IDENTIFIER")
                                                     .orgIdentifier("ORG_ID")
                                                     .name("UPSERTED_SERVICE")
                                                     .description("NEW_DESCRIPTION")
                                                     .build();
    upsertService = serviceEntityService.upsert(upsertServiceRequestOrgLevel, UpsertOptions.DEFAULT);
    assertThat(upsertService.getAccountId()).isEqualTo(upsertServiceRequest.getAccountId());
    assertThat(upsertService.getOrgIdentifier()).isEqualTo(upsertServiceRequest.getOrgIdentifier());
    assertThat(upsertService.getProjectIdentifier()).isNull();
    assertThat(upsertService.getIdentifier()).isEqualTo(upsertServiceRequest.getIdentifier());
    assertThat(upsertService.getName()).isEqualTo(upsertServiceRequest.getName());
    assertThat(upsertService.getDescription()).isEqualTo(upsertServiceRequest.getDescription());

    criteriaFromServiceFilter = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", null, false);

    list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);
    List<ServiceResponseDTO> dtoList =
        list.getContent().stream().map(ServiceElementMapper::writeDTO).collect(Collectors.toList());
    assertThat(dtoList).containsOnly(ServiceElementMapper.writeDTO(upsertService));

    // Delete operations
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder().value("false").build();
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(settingValueResponseDTO);

    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(Page.empty());
    boolean delete = serviceEntityService.delete("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER", 1L, false);
    assertThat(delete).isTrue();
    verify(serviceOverrideService).deleteAllInProjectForAService("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "IDENTIFIER");

    Optional<ServiceEntity> deletedService =
        serviceEntityService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "UPDATED_SERVICE", false);
    assertThat(deletedService.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testBulkCreate() {
    List<ServiceEntity> serviceEntities = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      String serviceIdentifier = "identifier " + i;
      String serviceName = "serviceName " + i;
      ServiceEntity serviceEntity = createServiceEntity(serviceIdentifier, serviceName);
      serviceEntities.add(serviceEntity);
    }
    serviceEntityService.bulkCreate(ACCOUNT_ID, serviceEntities);
    for (int i = 0; i < 2; i++) {
      String serviceIdentifier = "identifier " + i;
      Optional<ServiceEntity> serviceEntitySaved =
          serviceEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, serviceIdentifier, false);
      assertThat(serviceEntitySaved.isPresent()).isTrue();
    }
    verify(outboxService, times(2)).save(any(ServiceCreateEvent.class));
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetAllServices() {
    List<ServiceEntity> serviceEntities = new ArrayList<>();
    int pageSize = 50;
    int numOfServices = pageSize * 2 + 10; // creating adhoc num of services, not in multiples of page size
    for (int i = 0; i < numOfServices; i++) {
      String serviceIdentifier = "identifier " + i;
      String serviceName = "serviceName " + i;
      ServiceEntity serviceEntity = createServiceEntity(serviceIdentifier, serviceName);
      serviceEntities.add(serviceEntity);
    }
    serviceEntityService.bulkCreate(ACCOUNT_ID, serviceEntities);

    List<ServiceEntity> serviceEntityList =
        serviceEntityService.getAllServices(ACCOUNT_ID, ORG_ID, PROJECT_ID, pageSize, true, new ArrayList<>());
    assertThat(serviceEntityList.size()).isEqualTo(numOfServices);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetAllNonDeletedServices() {
    List<ServiceEntity> serviceEntities = new ArrayList<>();
    int numOfServices = 4;
    for (int i = 0; i < numOfServices; i++) {
      String serviceIdentifier = "identifier " + i;
      String serviceName = "serviceName " + i;
      ServiceEntity serviceEntity = createServiceEntity(serviceIdentifier, serviceName);
      serviceEntity.setDeleted(i % 2 == 0); // Every alternate service is deleted
      serviceEntities.add(serviceEntity);
    }
    serviceEntityService.bulkCreate(ACCOUNT_ID, serviceEntities);

    List<ServiceEntity> serviceEntityList =
        serviceEntityService.getAllNonDeletedServices(ACCOUNT_ID, ORG_ID, PROJECT_ID, new ArrayList<>());
    assertThat(serviceEntityList.size()).isEqualTo(numOfServices / 2);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetAllNonDeletedServicesWithSort() {
    List<ServiceEntity> serviceEntities = new ArrayList<>();
    int numOfServices = 4;
    for (int i = 0; i < numOfServices; i++) {
      String serviceIdentifier = "identifier" + i;
      String serviceName = String.valueOf((char) ('A' + i));
      ServiceEntity serviceEntity = createServiceEntity(serviceIdentifier, serviceName);
      serviceEntity.setDeleted(i % 2 == 0); // Every alternate service is deleted
      serviceEntities.add(serviceEntity);
    }
    serviceEntityService.bulkCreate(ACCOUNT_ID, serviceEntities);

    List<ServiceEntity> serviceEntityList =
        serviceEntityService.getAllNonDeletedServices(ACCOUNT_ID, ORG_ID, PROJECT_ID, List.of("name,DESC"));
    assertThat(serviceEntityList.size()).isEqualTo(numOfServices / 2);
    assertThat(serviceEntityList.get(0).getName())
        .isGreaterThan(serviceEntityList.get(serviceEntityList.size() - 1).getName());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetActiveServiceCount() {
    List<ServiceEntity> serviceEntities = new ArrayList<>();
    for (int i = 1; i <= 20; i++) {
      String serviceIdentifier = "identifier " + i;
      String serviceName = "serviceName " + i;
      ServiceEntity serviceEntity = createServiceEntity(serviceIdentifier, serviceName);
      serviceEntity.setCreatedAt((long) i);
      if (i % 5 == 0) {
        serviceEntity.setDeleted(true);
        serviceEntity.setDeletedAt((long) (i + 5));
      }
      serviceEntities.add(serviceEntity);
    }
    serviceEntityService.bulkCreate(ACCOUNT_ID, serviceEntities);
    Integer activeServiceCount =
        serviceEntityService.findActiveServicesCountAtGivenTimestamp(ACCOUNT_ID, ORG_ID, PROJECT_ID, 16);
    assertThat(activeServiceCount).isEqualTo(14);
  }

  private ServiceEntity createServiceEntity(String identifier, String name) {
    return ServiceEntity.builder()
        .accountId(ACCOUNT_ID)
        .identifier(identifier)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .name(name)
        .deleted(false)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testGetDuplicateServiceExistsErrorMessage() {
    String errorMessage = "Bulk write operation error on server localhost:27017. "
        + "Write errors: [BulkWriteError{index=0, code=11000, "
        + "message='E11000 duplicate key error collection: ng-harness.servicesNG "
        + "index: unique_accountId_organizationIdentifier_projectIdentifier_serviceIdentifier "
        + "dup key: { accountId: \"kmpySmUISimoRrJL6NL73w\", orgIdentifier: \"default\", "
        + "projectIdentifier: \"Nofar\", identifier: \"service_5\" }'";
    String errorMessageToBeShownToUser =
        serviceEntityService.getDuplicateServiceExistsErrorMessage("kmpySmUISimoRrJL6NL73w", errorMessage);
    assertThat(errorMessageToBeShownToUser)
        .isEqualTo(
            "Service [service_5] under Project[Nofar], Organization [default] in Account [kmpySmUISimoRrJL6NL73w] already exists");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testErrorMessageWhenServiceIsReferenced() {
    List<EntitySetupUsageDTO> referencedByEntities = List.of(getEntitySetupUsageDTO());
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder().value("false").build();
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(settingValueResponseDTO);
    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(new PageImpl<>(referencedByEntities));
    assertThatThrownBy(() -> serviceEntityService.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, "SERVICE", 0L, false))
        .isInstanceOf(ReferencedEntityException.class)
        .hasMessage(
            "The service SERVICE cannot be deleted because it is being referenced in 1 entity. To delete your service, please remove the reference service from these entities.");

    referencedByEntities = List.of(getEntitySetupUsageDTO(), getEntitySetupUsageDTO());
    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(new PageImpl<>(referencedByEntities));
    assertThatThrownBy(() -> serviceEntityService.delete(ACCOUNT_ID, ORG_ID, PROJECT_ID, "SERVICE", 0L, false))
        .isInstanceOf(ReferencedEntityException.class)
        .hasMessage(
            "The service SERVICE cannot be deleted because it is being referenced in 2 entities. To delete your service, please remove the reference service from these entities.");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testDeleteAllServicesInProject() {
    ServiceEntity serviceEntity1 = ServiceEntity.builder()
                                       .accountId("ACCOUNT_ID")
                                       .identifier("IDENTIFIER_1")
                                       .orgIdentifier("ORG_ID")
                                       .projectIdentifier("PROJECT_ID")
                                       .name("Service")
                                       .build();

    ServiceEntity serviceEntity2 = ServiceEntity.builder()
                                       .accountId("ACCOUNT_ID")
                                       .identifier("IDENTIFIER_2")
                                       .orgIdentifier("ORG_ID")
                                       .projectIdentifier("PROJECT_ID")
                                       .name("Service")
                                       .build();

    // Create operations
    serviceEntityService.create(serviceEntity1);
    serviceEntityService.create(serviceEntity2);

    boolean delete = serviceEntityService.forceDeleteAllInProject("ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
    assertThat(delete).isTrue();

    // List services operations.
    Criteria criteriaFromServiceFilter =
        CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<ServiceEntity> list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isZero();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testHardDeleteService() {
    final String id = UUIDGenerator.generateUuid();
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId("ACCOUNT_ID")
                                      .identifier(id)
                                      .orgIdentifier("ORG_ID")
                                      .projectIdentifier("PROJECT_ID")
                                      .name("Service")
                                      .build();

    serviceEntityService.create(serviceEntity);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder().value("false").build();
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(settingValueResponseDTO);
    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(Page.empty());
    boolean delete = serviceEntityService.delete("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", id, 0L, false);
    assertThat(delete).isTrue();

    // list both deleted true/false services
    Criteria criteriaFromServiceFilter =
        CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<ServiceEntity> list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent().size()).isZero();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateServiceInputsForServiceWithNoRuntimeInputs() {
    String filename = "service/service-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    String templateYaml = serviceEntityService.createServiceInputsYaml(yaml, SERVICE_ID);
    assertThat(templateYaml).isNullOrEmpty();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateServiceInputsForServiceWithRuntimeInputs() {
    String filename = "service/service-with-runtime-inputs.yaml";
    String yaml = readFile(filename);
    String templateYaml = serviceEntityService.createServiceInputsYaml(yaml, SERVICE_ID);
    assertThat(templateYaml).isNotNull();

    String resFile = "service/service-with-runtime-inputs-res.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateServiceInputsForServiceWithPrimaryArtifactRefFixed() {
    String filename = "service/service-with-primaryArtifactRef-fixed.yaml";
    String yaml = readFile(filename);
    String templateYaml = serviceEntityService.createServiceInputsYaml(yaml, SERVICE_ID);
    assertThat(templateYaml).isNotNull();

    String resFile = "service/serviceInputs-with-primaryArtifactRef-fixed.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateServiceInputsForServiceWithPrimaryArtifactRefRuntime() {
    String filename = "service/service-with-primaryArtifactRef-runtime.yaml";
    String yaml = readFile(filename);
    String templateYaml = serviceEntityService.createServiceInputsYaml(yaml, SERVICE_ID);
    assertThat(templateYaml).isNotNull();

    String resFile = "service/serviceInputs-with-primaryArtifactRef-runtime.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCreateServiceInputsForServiceWithPrimaryArtifactRefExpression() {
    String filename = "service/service-with-primaryArtifactRef-expression.yaml";
    String yaml = readFile(filename);

    String templateYaml = serviceEntityService.createServiceInputsYaml(yaml, SERVICE_ID);
    assertThat(templateYaml).isNotNull();

    String resFile = "service/serviceInputs-with-primaryArtifactRef-expression.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetArtifactSourceInputsWithServiceV2() {
    String filename = "service/service-with-primaryArtifactRef-runtime.yaml";
    String yaml = readFile(filename);
    ArtifactSourcesResponseDTO responseDTO = serviceEntityService.getArtifactSourceInputs(yaml, SERVICE_ID);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getSourceIdentifiers()).isNotNull().isNotEmpty().hasSize(2);
    assertThat(responseDTO.getSourceIdentifiers()).hasSameElementsAs(Arrays.asList("i1", "i2"));
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).isNotNull().isNotEmpty().hasSize(2);
    String runForm1 = "identifier: i1\n"
        + "type: DockerRegistry\n"
        + "spec:\n"
        + "  tag: <+input>\n";
    String runForm2 = "identifier: i2\n"
        + "type: DockerRegistry\n"
        + "spec:\n"
        + "  tag: <+input>\n";
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).hasFieldOrPropertyWithValue("i1", runForm1);
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).hasFieldOrPropertyWithValue("i2", runForm2);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetArtifactSourceInputsWithServiceV1() {
    String filename = "service/serviceWith3ConnectorReferences.yaml";
    String yaml = readFile(filename);
    ArtifactSourcesResponseDTO responseDTO = serviceEntityService.getArtifactSourceInputs(yaml, SERVICE_ID);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getSourceIdentifiers()).isNull();
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).isNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetArtifactSourceInputsWithServiceV2AndSourcesHasNoRuntimeInput() {
    String filename = "service/service-with-no-runtime-input-in-sources.yaml";
    String yaml = readFile(filename);
    ArtifactSourcesResponseDTO responseDTO = serviceEntityService.getArtifactSourceInputs(yaml, SERVICE_ID);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getSourceIdentifiers()).isNotNull().isNotEmpty().hasSize(2);
    assertThat(responseDTO.getSourceIdentifiers()).hasSameElementsAs(Arrays.asList("i1", "i2"));
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).hasFieldOrPropertyWithValue("i1", null);
    assertThat(responseDTO.getSourceIdentifierToSourceInputMap()).hasFieldOrPropertyWithValue("i2", null);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpsertWithoutOutbox() {
    ServiceEntity createRequest = ServiceEntity.builder()
                                      .accountId("ACCOUNT_ID")
                                      .identifier(UUIDGenerator.generateUuid())
                                      .orgIdentifier("ORG_ID")
                                      .projectIdentifier("PROJECT_ID")
                                      .build();

    ServiceEntity created = serviceEntityService.create(createRequest);

    ServiceEntity upsertRequest = ServiceEntity.builder()
                                      .accountId("ACCOUNT_ID")
                                      .identifier(created.getIdentifier())
                                      .orgIdentifier("ORG_ID")
                                      .projectIdentifier("PROJECT_ID")
                                      .name("UPSERTED_ENV")
                                      .description("NEW_DESCRIPTION")
                                      .build();

    ServiceEntity upserted = serviceEntityService.upsert(upsertRequest, UpsertOptions.DEFAULT.withNoOutbox());

    assertThat(upserted).isNotNull();

    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetYamlNodeForFqn() {
    String yaml = readFile("ArtifactResourceUtils/serviceWithPrimaryAndSidecars.yaml");
    ServiceEntity createRequest = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("testGetYamlNodeForFqn")
                                      .identifier("testGetYamlNodeForFqn")
                                      .yaml(yaml)
                                      .build();

    serviceEntityService.create(createRequest);

    YamlNode primaryNode =
        serviceEntityService.getYamlNodeForFqn(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testGetYamlNodeForFqn",
            "pipeline.stages.s2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag");

    YamlNode sidecarNode =
        serviceEntityService.getYamlNodeForFqn(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testGetYamlNodeForFqn",
            "pipeline.stages.s2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars.sc1.spec.tag");

    assertThat(primaryNode.getCurrJsonNode().asText()).isEqualTo("<+input>");
    assertThat(primaryNode.getParentNode().toString())
        .isEqualTo(
            "{\"connectorRef\":\"account.harnessImage\",\"imagePath\":\"harness/todolist\",\"tag\":\"<+input>\"}");

    assertThat(sidecarNode.getCurrJsonNode().asText()).isEqualTo("<+input>");
    assertThat(sidecarNode.getParentNode().toString())
        .isEqualTo(
            "{\"connectorRef\":\"account.harnessImage\",\"imagePath\":\"harness/todolist-sample\",\"region\":\"us-east-1\",\"tag\":\"<+input>\"}");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetYamlNodeForFqnWithPrimarySources() {
    String yaml = readFile("ArtifactResourceUtils/serviceWithPrimarySourcesAndSidecars.yaml");
    ServiceEntity createRequest = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("testGetYamlNodeForFqn")
                                      .identifier("testGetYamlNodeForFqn")
                                      .yaml(yaml)
                                      .build();

    serviceEntityService.create(createRequest);

    YamlNode primaryNode = serviceEntityService.getYamlNodeForFqn(ACCOUNT_ID, ORG_ID, PROJECT_ID,
        "testGetYamlNodeForFqn",
        "pipeline.stages.s2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.sources.i1.spec.tag");

    YamlNode sidecarNode =
        serviceEntityService.getYamlNodeForFqn(ACCOUNT_ID, ORG_ID, PROJECT_ID, "testGetYamlNodeForFqn",
            "pipeline.stages.s2.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars.sc1.spec.tag");

    assertThat(primaryNode.getCurrJsonNode().asText()).isEqualTo("<+input>");
    assertThat(primaryNode.getParentNode().toString())
        .isEqualTo(
            "{\"connectorRef\":\"account.harnessImage1\",\"imagePath\":\"harness/todolist\",\"tag\":\"<+input>\"}");

    assertThat(sidecarNode.getCurrJsonNode().asText()).isEqualTo("<+input>");
    assertThat(sidecarNode.getParentNode().toString())
        .isEqualTo(
            "{\"connectorRef\":\"account.harnessImage\",\"imagePath\":\"harness/todolist-sample\",\"region\":\"us-east-1\",\"tag\":\"<+input>\"}");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  @Parameters(method = "data")
  public void testMergeServiceInputs(String pipelineInputYamlPath, String actualEntityYamlPath,
      String mergedInputYamlPath, boolean isMergedYamlEmpty) {
    String yaml = readFile(actualEntityYamlPath);
    ServiceEntity createRequest = ServiceEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .name("serviceWithPrimaryArtifactRefRuntime")
                                      .identifier("serviceWithPrimaryArtifactRefRuntime")
                                      .yaml(yaml)
                                      .build();

    serviceEntityService.create(createRequest);

    String oldTemplateInputYaml = readFile(pipelineInputYamlPath);
    String mergedTemplateInputsYaml = readFile(mergedInputYamlPath);
    ServiceInputsMergedResponseDto responseDto = serviceEntityService.mergeServiceInputs(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, "serviceWithPrimaryArtifactRefRuntime", oldTemplateInputYaml);
    String mergedYaml = responseDto.getMergedServiceInputsYaml();
    if (isMergedYamlEmpty) {
      assertThat(mergedYaml).isNull();
    } else {
      assertThat(mergedYaml).isNotNull().isNotEmpty();
      assertThat(mergedYaml).isEqualTo(mergedTemplateInputsYaml);
    }
    assertThat(responseDto.getServiceYaml()).isNotNull().isNotEmpty().isEqualTo(yaml);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetListCallForOrgAccountLevelService() {
    ServiceEntity serviceEntity1 =
        ServiceEntity.builder().accountId("ACCOUNT_ID").identifier("OS1").orgIdentifier("ORG_ID").name("OS1").build();

    ServiceEntity serviceEntity2 = ServiceEntity.builder()
                                       .accountId("ACCOUNT_ID")
                                       .identifier("PS1")
                                       .orgIdentifier("ORG_ID")
                                       .projectIdentifier("PROJECT_ID")
                                       .name("PS1")
                                       .build();

    ServiceEntity serviceEntity3 =
        ServiceEntity.builder().accountId("ACCOUNT_ID").identifier("AS1").name("AS1").build();

    serviceEntityService.create(serviceEntity1);
    serviceEntityService.create(serviceEntity2);
    serviceEntityService.create(serviceEntity3);

    // get by serviceRef
    Optional<ServiceEntity> optionalService =
        serviceEntityService.get("ACCOUNT_ID", "ORG_ID", "RANDOM_PIPELINE_PROJECT_ID", "org.OS1", false);
    assertThat(optionalService).isPresent();
    // get by serviceIdentifier
    Optional<ServiceEntity> optionalServiceById = serviceEntityService.get("ACCOUNT_ID", "ORG_ID", null, "OS1", false);
    assertThat(optionalServiceById).isPresent();

    // List down all services accessible from that scope
    // project level
    Criteria criteriaFromServiceFilter =
        ServiceFilterHelper.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", null, false, true);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<ServiceEntity> list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    // services from all scopes
    assertThat(list.getContent().size()).isEqualTo(3);

    // org level
    criteriaFromServiceFilter =
        ServiceFilterHelper.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", null, null, false, true);
    list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    // services from org,account scopes
    assertThat(list.getContent().size()).isEqualTo(2);

    // account level
    criteriaFromServiceFilter =
        ServiceFilterHelper.createCriteriaForGetList("ACCOUNT_ID", null, null, null, false, true);
    list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    // services from acc scope
    assertThat(list.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateCriteriaForGetListWithOptionalOrgAndProject() {
    Criteria criteriaFromServiceFilter =
        ServiceFilterHelper.createCriteriaForGetList("ACCOUNT_ID", null, null, null, false, false);

    assertThat(criteriaFromServiceFilter.getCriteriaObject()).containsKey("accountId");
    assertThat(criteriaFromServiceFilter.getCriteriaObject()).containsKey("orgIdentifier");
    assertThat(criteriaFromServiceFilter.getCriteriaObject()).containsKey("projectIdentifier");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDForAccountLevelService() {
    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(Page.empty());
    ServiceEntity serviceEntity =
        ServiceEntity.builder().accountId("ACCOUNT_ID").identifier("IDENTIFIER").name("SERVICE").build();
    // Create operations
    ServiceEntity createdService = serviceEntityService.create(serviceEntity);
    assertThat(createdService).isNotNull();

    // Update operations at org level
    ServiceEntity updateServiceRequest = ServiceEntity.builder()
                                             .accountId("ACCOUNT_ID")
                                             .identifier("IDENTIFIER")
                                             .name("UPDATED_SERVICE")
                                             .description("NEW_DESCRIPTION")
                                             .build();

    ServiceEntity updatedServiceResponse = serviceEntityService.update(updateServiceRequest);
    assertThat(updatedServiceResponse.getName()).isEqualTo(updateServiceRequest.getName());
    assertThat(updatedServiceResponse.getDescription()).isEqualTo(updateServiceRequest.getDescription());

    // Upsert operations at account level
    ServiceEntity upsertServiceRequest = ServiceEntity.builder()
                                             .accountId("ACCOUNT_ID")
                                             .identifier("NEW_IDENTIFIER")
                                             .name("UPSERTED_SERVICE")
                                             .description("NEW_DESCRIPTION")
                                             .build();

    ServiceEntity upsertService = serviceEntityService.upsert(upsertServiceRequest, UpsertOptions.DEFAULT);
    assertThat(upsertService.getAccountId()).isEqualTo(upsertServiceRequest.getAccountId());
    assertThat(upsertService.getOrgIdentifier()).isEqualTo(upsertServiceRequest.getOrgIdentifier());
    assertThat(upsertService.getProjectIdentifier()).isEqualTo(upsertServiceRequest.getProjectIdentifier());
    assertThat(upsertService.getIdentifier()).isEqualTo(upsertServiceRequest.getIdentifier());
    assertThat(upsertService.getName()).isEqualTo(upsertServiceRequest.getName());
    assertThat(upsertService.getDescription()).isEqualTo(upsertServiceRequest.getDescription());

    // List services operations.
    Criteria criteriaFromServiceFilter = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", null, null, false);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<ServiceEntity> list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(2);
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder().value("false").build();
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(settingValueResponseDTO);

    boolean delete = serviceEntityService.delete("ACCOUNT_ID", null, null, "IDENTIFIER", 1L, false);
    assertThat(delete).isTrue();
    verify(serviceOverrideService).deleteAllInProjectForAService("ACCOUNT_ID", null, null, "IDENTIFIER");

    Optional<ServiceEntity> deletedService =
        serviceEntityService.get("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", "account.NEW_IDENTIFIER", false);
    assertThat(deletedService.isPresent()).isTrue();
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testForceDeleteAllServicesInProject() {
    ServiceEntity serviceEntity1 = ServiceEntity.builder()
                                       .accountId("ACCOUNT_ID")
                                       .identifier("IDENTIFIER_1")
                                       .orgIdentifier("ORG_ID")
                                       .projectIdentifier("PROJECT_ID")
                                       .name("Service")
                                       .build();

    ServiceEntity serviceEntity2 = ServiceEntity.builder()
                                       .accountId("ACCOUNT_ID")
                                       .identifier("IDENTIFIER_2")
                                       .orgIdentifier("ORG_ID")
                                       .projectIdentifier("PROJECT_ID")
                                       .name("Service")
                                       .build();

    // Create operations
    serviceEntityService.create(serviceEntity1);
    serviceEntityService.create(serviceEntity2);

    boolean delete = serviceEntityService.forceDeleteAllInProject("ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
    assertThat(delete).isTrue();
    verify(entitySetupUsageHelper, times(1))
        .deleteSetupUsagesWithOnlyIdentifierInfo("IDENTIFIER_1", "ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
    verify(entitySetupUsageHelper, times(1))
        .deleteSetupUsagesWithOnlyIdentifierInfo("IDENTIFIER_2", "ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testForceDeleteService() {
    final String id = UUIDGenerator.generateUuid();
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId("ACCOUNT_ID")
                                      .identifier(id)
                                      .orgIdentifier("ORG_ID")
                                      .projectIdentifier("PROJECT_ID")
                                      .name("Service")
                                      .build();

    serviceEntityService.create(serviceEntity);
    when(entitySetupUsageService.listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString()))
        .thenReturn(Page.empty());
    MockedStatic<NGRestUtils> mockRestStatic = Mockito.mockStatic(NGRestUtils.class);
    SettingValueResponseDTO settingValueResponseDTO = SettingValueResponseDTO.builder().value("false").build();
    mockRestStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(settingValueResponseDTO);
    serviceEntityService.delete("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", id, 0L, true);
    verify(entitySetupUsageService, times(0))
        .listAllEntityUsage(anyInt(), anyInt(), anyString(), anyString(), any(), anyString());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testDeleteAllServicesInOrg() {
    ServiceEntity serviceEntity1 = ServiceEntity.builder()
                                       .accountId("ACCOUNT_ID")
                                       .identifier("IDENTIFIER_1")
                                       .orgIdentifier("ORG_ID")
                                       .name("Service")
                                       .build();

    ServiceEntity serviceEntity2 = ServiceEntity.builder()
                                       .accountId("ACCOUNT_ID")
                                       .identifier("IDENTIFIER_2")
                                       .orgIdentifier("ORG_ID")
                                       .name("Service")
                                       .build();

    ServiceEntity projectLevelService1 = ServiceEntity.builder()
                                             .accountId("ACCOUNT_ID")
                                             .identifier("IDENTIFIER_2")
                                             .orgIdentifier("ORG_ID")
                                             .projectIdentifier("PROJECT_ID")
                                             .name("Service")
                                             .build();

    // Create operations
    serviceEntityService.create(serviceEntity1);
    serviceEntityService.create(serviceEntity2);
    serviceEntityService.create(projectLevelService1);

    boolean delete = serviceEntityService.forceDeleteAllInOrg("ACCOUNT_ID", "ORG_ID");
    assertThat(delete).isTrue();

    // List services operations.
    Criteria criteriaFromServiceFilter = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", null);
    Pageable pageRequest = PageUtils.getPageRequest(0, 10, null);
    Page<ServiceEntity> list = serviceEntityService.list(criteriaFromServiceFilter, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isZero();

    // List services operations.
    Criteria projectServiceCriteria = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID");
    pageRequest = PageUtils.getPageRequest(0, 10, null);
    list = serviceEntityService.list(projectServiceCriteria, pageRequest);
    assertThat(list.getContent()).isNotNull();
    assertThat(list.getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsWithNormalService() {
    ServiceEntity serviceEntity1 = ServiceEntity.builder()
                                       .accountId("ACCOUNT_ID")
                                       .identifier("IDENTIFIER_1")
                                       .orgIdentifier("ORG_ID")
                                       .name("Service")
                                       .build();

    serviceEntityService.create(serviceEntity1);
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        serviceEntityService.validateTemplateInputs("ACCOUNT_ID", "ORG_ID", null, "IDENTIFIER_1", "false");

    assertThat(validateTemplateInputsResponseDTO.isValidYaml()).isTrue();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testValidateTemplateInputsWithInvalidService() throws IOException {
    // create service
    ServiceEntity serviceEntity1 = ServiceEntity.builder()
                                       .accountId("ACCOUNT_ID")
                                       .identifier("IDENTIFIER_1")
                                       .orgIdentifier("ORG_ID")
                                       .name("Service")
                                       .yaml("service:\n"
                                           + "  name: Service\n"
                                           + "  identifier: IDENTIFIER_1\n"
                                           + "  tags: {}\n"
                                           + "  serviceDefinition:\n"
                                           + "    spec:\n"
                                           + "      artifacts:\n"
                                           + "        primary:\n"
                                           + "          primaryArtifactRef: <+input>\n"
                                           + "          sources:\n"
                                           + "            - name: s1\n"
                                           + "              identifier: s1\n"
                                           + "              template:\n"
                                           + "                templateRef: nginx\n"
                                           + "                versionLabel: v1\n"
                                           + "                templateInputs:\n"
                                           + "                  type: DockerRegistry\n"
                                           + "                  spec:\n"
                                           + "                    tag: <+input>\n"
                                           + "    type: Kubernetes")
                                       .build();

    serviceEntityService.create(serviceEntity1);

    RefreshRequestDTO refreshRequest = RefreshRequestDTO.builder().yaml(serviceEntity1.fetchNonEmptyYaml()).build();

    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder()
            .validYaml(false)
            .errorNodeSummary(ErrorNodeSummary.builder().build())
            .build();
    Call<ResponseDTO<ValidateTemplateInputsResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .validateTemplateInputsForGivenYaml(
            "ACCOUNT_ID", "ORG_ID", null, null, null, null, null, null, null, null, null, "false", refreshRequest);
    when(callRequest.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(validateTemplateInputsResponseDTO)));

    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO2 =
        serviceEntityService.validateTemplateInputs("ACCOUNT_ID", "ORG_ID", null, "IDENTIFIER_1", "false");

    assertThat(validateTemplateInputsResponseDTO2.isValidYaml()).isFalse();
  }
  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testTemplateResolveExceptionWithArtifactSourceTemplateInService() throws IOException {
    String fileName = "service-with-artifact-template-ref.yaml";
    String givenYaml = readFile(fileName);
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", null, null, null, null, null, null, null,
            null, null, TemplateApplyRequestDTO.builder().originalEntityYaml(givenYaml).checkForAccess(true).build(),
            false);
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponseDTO =
        ValidateTemplateInputsResponseDTO.builder().build();
    when(callRequest.execute())
        .thenThrow(new NGTemplateResolveExceptionV2(
            "Exception in resolving template refs in given yaml.", USER, validateTemplateInputsResponseDTO, null));
    assertThatThrownBy(
        () -> serviceEntityService.resolveArtifactSourceTemplateRefs("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", givenYaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Exception in resolving template refs in given service yaml.");
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testResolveRefsWithArtifactSourceTemplateInService() throws IOException {
    String fileName = "service-with-artifact-template-ref.yaml";
    String givenYaml = readFile(fileName);
    Call<ResponseDTO<TemplateMergeResponseDTO>> callRequest = mock(Call.class);
    doReturn(callRequest)
        .when(templateResourceClient)
        .applyTemplatesOnGivenYamlV2("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", null, null, null, null, null, null, null,
            null, null, TemplateApplyRequestDTO.builder().originalEntityYaml(givenYaml).checkForAccess(true).build(),
            false);
    when(callRequest.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(TemplateMergeResponseDTO.builder().mergedPipelineYaml(givenYaml).build())));
    String resolvedTemplateRefsInService =
        serviceEntityService.resolveArtifactSourceTemplateRefs("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", givenYaml);
    assertThat(resolvedTemplateRefsInService).isEqualTo(givenYaml);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testMergeServiceInputsWithSingleArtifactSource() {
    String serviceYaml = "service:\n"
        + "  name: svc\n"
        + "  identifier: svc\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: <+input>\n"
        + "          sources:\n"
        + "            - spec:\n"
        + "                connectorRef: dockerpublic\n"
        + "                imagePath: library/nginx\n"
        + "                tag: <+input>\n"
        + "              identifier: updatedValue\n"
        + "              type: DockerRegistry\n"
        + "    type: Kubernetes\n";

    String responseYaml =
        serviceEntityService.createServiceInputsYamlGivenPrimaryArtifactRef(serviceYaml, "svc", "oldValue");

    assertThat(responseYaml).isNotEmpty();
    assertThat(responseYaml)
        .isEqualTo("serviceInputs:\n"
            + "  serviceDefinition:\n"
            + "    type: Kubernetes\n"
            + "    spec:\n"
            + "      artifacts:\n"
            + "        primary:\n"
            + "          primaryArtifactRef: <+input>\n"
            + "          sources:\n"
            + "            - identifier: updatedValue\n"
            + "              type: DockerRegistry\n"
            + "              spec:\n"
            + "                tag: <+input>\n");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testMergeServiceInputsWhenPrimaryRefExpressionInputReconciledWithExpressionValue() {
    String filename = "service/service-with-primaryArtifactRef-expression.yaml";
    String yaml = readFile(filename);
    String templateYaml = serviceEntityService.createServiceInputsYamlGivenPrimaryArtifactRef(
        yaml, "svc", "<+serviceVariables.primaryRef>");

    assertThat(templateYaml).isNotNull();

    String resFile = "service/serviceInputs-with-primaryArtifactRef-expression.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testMergeServiceInputsWhenPrimaryRefRuntimeInputReconciledWithExpressionValue() {
    String filename = "service/service-with-primaryArtifactRef-runtime.yaml";
    String yaml = readFile(filename);
    String templateYaml = serviceEntityService.createServiceInputsYamlGivenPrimaryArtifactRef(
        yaml, "svc", "<+serviceVariables.primaryRef>");

    assertThat(templateYaml).isNotNull();

    String resFile = "service/merged-service-input-runtime-primary-artifact-expression.yaml";
    String resTemplate = readFile(resFile);
    assertThat(templateYaml).isEqualTo(resTemplate);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetManifestIdentifiersListServiceV2KubernetesSpec() {
    String filename = "service/service-with-primaryManifestRef-Kubernetes.yaml";
    String yaml = readFile(filename);
    ManifestsResponseDTO responseDTO = serviceEntityService.getManifestIdentifiers(yaml, SERVICE_ID);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getIdentifiers()).isNotNull().isNotEmpty().hasSize(1);
    assertThat(responseDTO.getIdentifiers()).hasSameElementsAs(Arrays.asList("mani_i1"));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetManifestIdentifiersListServiceV2HelmSpec() {
    String filename = "service/service-with-primaryManifestRef-NativeHelm.yaml";
    String yaml = readFile(filename);
    ManifestsResponseDTO responseDTO = serviceEntityService.getManifestIdentifiers(yaml, SERVICE_ID);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getIdentifiers()).isNotNull().isNotEmpty().hasSize(2);
    assertThat(responseDTO.getIdentifiers()).hasSameElementsAs(Arrays.asList("mani_i1", "mani_i2"));
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetManifestIdentifiersListServiceV2Others() {
    String filename = "service/service-with-primaryManifestRef-Others.yaml";
    String InvalidYaml = readFile(filename);
    assertThatThrownBy(() -> serviceEntityService.getManifestIdentifiers(InvalidYaml, SERVICE_ID))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Service Spec Type ECS is not supported");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testFailGetManifestIdentifiersServiceV2YamlException() {
    assertThatThrownBy(() -> serviceEntityService.getManifestIdentifiers("service:", SERVICE_ID))
        .isInstanceOf(YamlException.class)
        .hasMessage("Yaml provided for service " + SERVICE_ID + " does not have service root field.");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetEmptyManifestIdentifiersListServiceV2() {
    String filename = "service/service-with-no-manifests.yaml";
    String yaml = readFile(filename);
    assertThat(serviceEntityService.getManifestIdentifiers(yaml, SERVICE_ID)).isEqualTo(new ManifestsResponseDTO());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testFailGetManifestIdentifiersServiceV2YamlExceptionServiceDefinition() {
    assertThatThrownBy(()
                           -> serviceEntityService.getManifestIdentifiers("service:\n"
                                   + "  serviceDefinition:",
                               SERVICE_ID))
        .isInstanceOf(YamlException.class)
        .hasMessage("Yaml provided for service " + SERVICE_ID + " does not have service definition field.");
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private EntitySetupUsageDTO getEntitySetupUsageDTO() {
    return EntitySetupUsageDTO.builder().referredByEntity(EntityDetail.builder().build()).build();
  }
}
