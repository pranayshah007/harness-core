/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.mappers;

import static io.harness.idp.onboarding.utils.Constants.ENTITY_UNKNOWN_LIFECYCLE;
import static io.harness.idp.onboarding.utils.Constants.ENTITY_UNKNOWN_OWNER;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.onboarding.beans.BackstageCatalogComponentEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogDomainEntity;
import io.harness.idp.onboarding.beans.BackstageCatalogEntityTypes;
import io.harness.idp.onboarding.beans.BackstageCatalogSystemEntity;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class HarnessEntityToBackstageEntityMapperTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";
  static final String TEST_ORG_IDENTIFIER = "orgId";
  static final String TEST_ORG_IDENTIFIER_BIG =
      "orgIdorgIdorgIdorgIdorgIdorgIdorgIdorgIdorgIdorgIdorgIdorgIdorgIdorgId";
  static final String TEST_ORG_IDENTIFIER_BIG_TRUNCATED =
      "orgIdorgIdorgIdorgIdorgIdorgIdorgIdorgIdorgIdorgIdorgIdorgId---";
  static final String TEST_ORG_IDENTIFIER1 = "orgId1";
  static final String TEST_ORG_NAME = "orgName";
  static final String TEST_ORG_DESC = "orgDesc";
  static final String TEST_PROJECT_IDENTIFIER = "projectId";
  static final String TEST_PROJECT_IDENTIFIER_BIG =
      "projectIdprojectIdprojectIdprojectIdprojectIdprojectIdprojectIdprojectId";
  static final String TEST_PROJECT_IDENTIFIER_BIG_TRUNCATED =
      "projectIdprojectIdprojectIdprojectIdprojectIdprojectIdprojec---";
  static final String TEST_PROJECT_IDENTIFIER1 = "projectId2";
  static final String TEST_PROJECT_NAME = "projectName";
  static final String TEST_PROJECT_DESC = "projectDesc";
  static final String TEST_SERVICE_IDENTIFIER = "serviceId";
  static final String TEST_SERVICE_IDENTIFIER_BIG =
      "serviceIdserviceIdserviceIdserviceIdserviceIdserviceIdserviceIdserviceId";
  static final String TEST_SERVICE_IDENTIFIER_BIG_TRUNCATED =
      "serviceIdserviceIdserviceIdserviceIdserviceIdserviceIdservic---";
  static final String TEST_SERVICE_NAME = "serviceName";
  static final String TEST_SERVICE_DESC = "serviceDesc";
  static final String TEST_BACKSTAGE_API_VERSION = "backstage.io/v1alpha1";
  AutoCloseable openMocks;
  @InjectMocks HarnessOrgToBackstageDomain harnessOrgToBackstageDomain;
  @InjectMocks HarnessProjectToBackstageSystem harnessProjectToBackstageSystem;
  HarnessServiceToBackstageComponent harnessServiceToBackstageComponent;
  OrganizationDTO organizationDTO;
  ProjectDTO projectDTO;
  ServiceResponseDTO serviceResponseDTO;
  final OnboardingModuleConfig onboardingModuleConfig =
      OnboardingModuleConfig.builder()
          .harnessCiCdAnnotations(Map.of("projectUrl",
              "https://localhost:8181/ng/account/accountIdentifier/home/orgs/orgIdentifier/projects/projectIdentifier/details"))
          .build();

  @Before
  public void setup() {
    openMocks = MockitoAnnotations.openMocks(this);
    harnessServiceToBackstageComponent = new HarnessServiceToBackstageComponent(onboardingModuleConfig, "local");

    organizationDTO = OrganizationDTO.builder()
                          .identifier(TEST_ORG_IDENTIFIER)
                          .name(TEST_ORG_NAME)
                          .description(TEST_ORG_DESC)
                          .tags(null)
                          .build();

    projectDTO = ProjectDTO.builder()
                     .orgIdentifier(TEST_ORG_IDENTIFIER)
                     .identifier(TEST_PROJECT_IDENTIFIER)
                     .name(TEST_PROJECT_NAME)
                     .description(TEST_PROJECT_DESC)
                     .tags(null)
                     .build();

    serviceResponseDTO = ServiceResponseDTO.builder()
                             .accountId(TEST_ACCOUNT_IDENTIFIER)
                             .orgIdentifier(TEST_ORG_IDENTIFIER)
                             .projectIdentifier(TEST_PROJECT_IDENTIFIER)
                             .identifier(TEST_SERVICE_IDENTIFIER)
                             .name(TEST_SERVICE_NAME)
                             .description(TEST_SERVICE_DESC)
                             .tags(null)
                             .build();
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessOrgToBackstageDomainMapper() {
    BackstageCatalogDomainEntity backstageCatalogDomainEntity = harnessOrgToBackstageDomain.map(organizationDTO);
    assertEquals(organizationDTO.getIdentifier(), backstageCatalogDomainEntity.getMetadata().getIdentifier());
    assertEquals(organizationDTO.getIdentifier(), backstageCatalogDomainEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(organizationDTO.getIdentifier(), backstageCatalogDomainEntity.getMetadata().getName());
    assertEquals(organizationDTO.getDescription(), backstageCatalogDomainEntity.getMetadata().getDescription());
    assertNull(backstageCatalogDomainEntity.getMetadata().getAnnotations());
    assertEquals(Collections.EMPTY_LIST, backstageCatalogDomainEntity.getMetadata().getTags());
    assertEquals(ENTITY_UNKNOWN_OWNER, backstageCatalogDomainEntity.getSpec().getOwner());
    assertEquals(BackstageCatalogEntityTypes.DOMAIN.kind, backstageCatalogDomainEntity.getKind());
    assertEquals(TEST_BACKSTAGE_API_VERSION, backstageCatalogDomainEntity.getApiVersion());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessOrgToBackstageDomainMapperWithEmptyTags() {
    organizationDTO.setTags(new HashMap<>());

    BackstageCatalogDomainEntity backstageCatalogDomainEntity = harnessOrgToBackstageDomain.map(organizationDTO);
    assertEquals(organizationDTO.getIdentifier(), backstageCatalogDomainEntity.getMetadata().getIdentifier());
    assertEquals(organizationDTO.getIdentifier(), backstageCatalogDomainEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(organizationDTO.getIdentifier(), backstageCatalogDomainEntity.getMetadata().getName());
    assertEquals(organizationDTO.getDescription(), backstageCatalogDomainEntity.getMetadata().getDescription());
    assertNull(backstageCatalogDomainEntity.getMetadata().getAnnotations());
    assertEquals(Collections.EMPTY_LIST, backstageCatalogDomainEntity.getMetadata().getTags());
    assertEquals(ENTITY_UNKNOWN_OWNER, backstageCatalogDomainEntity.getSpec().getOwner());
    assertEquals(BackstageCatalogEntityTypes.DOMAIN.kind, backstageCatalogDomainEntity.getKind());
    assertEquals(TEST_BACKSTAGE_API_VERSION, backstageCatalogDomainEntity.getApiVersion());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessOrgToBackstageDomainMapperWithTags() {
    organizationDTO.setTags(Map.of("foo", "bar", "foo1", "bar1"));

    BackstageCatalogDomainEntity backstageCatalogDomainEntity = harnessOrgToBackstageDomain.map(organizationDTO);
    assertEquals(organizationDTO.getIdentifier(), backstageCatalogDomainEntity.getMetadata().getIdentifier());
    assertEquals(organizationDTO.getIdentifier(), backstageCatalogDomainEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(organizationDTO.getIdentifier(), backstageCatalogDomainEntity.getMetadata().getName());
    assertEquals(organizationDTO.getDescription(), backstageCatalogDomainEntity.getMetadata().getDescription());
    assertNull(backstageCatalogDomainEntity.getMetadata().getAnnotations());
    assertEquals(2, backstageCatalogDomainEntity.getMetadata().getTags().size());
    assertEquals(ENTITY_UNKNOWN_OWNER, backstageCatalogDomainEntity.getSpec().getOwner());
    assertEquals(BackstageCatalogEntityTypes.DOMAIN.kind, backstageCatalogDomainEntity.getKind());
    assertEquals(TEST_BACKSTAGE_API_VERSION, backstageCatalogDomainEntity.getApiVersion());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessOrgToBackstageDomainMapperForEntityNameTruncate() {
    organizationDTO.setIdentifier(TEST_ORG_IDENTIFIER_BIG);

    BackstageCatalogDomainEntity backstageCatalogDomainEntity = harnessOrgToBackstageDomain.map(organizationDTO);
    assertEquals(organizationDTO.getIdentifier(), backstageCatalogDomainEntity.getMetadata().getIdentifier());
    assertEquals(organizationDTO.getIdentifier(), backstageCatalogDomainEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(TEST_ORG_IDENTIFIER_BIG_TRUNCATED, backstageCatalogDomainEntity.getMetadata().getName());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessProjectToBackstageSystemMapper() {
    BackstageCatalogSystemEntity backstageCatalogSystemEntity = harnessProjectToBackstageSystem.map(projectDTO);
    assertEquals(projectDTO.getIdentifier(), backstageCatalogSystemEntity.getMetadata().getIdentifier());
    assertEquals(projectDTO.getOrgIdentifier() + "-" + projectDTO.getIdentifier(),
        backstageCatalogSystemEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(projectDTO.getIdentifier(), backstageCatalogSystemEntity.getMetadata().getName());
    assertEquals(projectDTO.getDescription(), backstageCatalogSystemEntity.getMetadata().getDescription());
    assertNull(backstageCatalogSystemEntity.getMetadata().getAnnotations());
    assertEquals(Collections.EMPTY_LIST, backstageCatalogSystemEntity.getMetadata().getTags());
    assertEquals(ENTITY_UNKNOWN_OWNER, backstageCatalogSystemEntity.getSpec().getOwner());
    assertEquals(projectDTO.getOrgIdentifier(), backstageCatalogSystemEntity.getSpec().getDomain());
    assertEquals(BackstageCatalogEntityTypes.SYSTEM.kind, backstageCatalogSystemEntity.getKind());
    assertEquals(TEST_BACKSTAGE_API_VERSION, backstageCatalogSystemEntity.getApiVersion());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessProjectToBackstageSystemMapperWithEmptyTags() {
    projectDTO.setTags(new HashMap<>());

    BackstageCatalogSystemEntity backstageCatalogSystemEntity = harnessProjectToBackstageSystem.map(projectDTO);
    assertEquals(projectDTO.getIdentifier(), backstageCatalogSystemEntity.getMetadata().getIdentifier());
    assertEquals(projectDTO.getOrgIdentifier() + "-" + projectDTO.getIdentifier(),
        backstageCatalogSystemEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(projectDTO.getIdentifier(), backstageCatalogSystemEntity.getMetadata().getName());
    assertEquals(projectDTO.getDescription(), backstageCatalogSystemEntity.getMetadata().getDescription());
    assertNull(backstageCatalogSystemEntity.getMetadata().getAnnotations());
    assertEquals(Collections.EMPTY_LIST, backstageCatalogSystemEntity.getMetadata().getTags());
    assertEquals(ENTITY_UNKNOWN_OWNER, backstageCatalogSystemEntity.getSpec().getOwner());
    assertEquals(projectDTO.getOrgIdentifier(), backstageCatalogSystemEntity.getSpec().getDomain());
    assertEquals(BackstageCatalogEntityTypes.SYSTEM.kind, backstageCatalogSystemEntity.getKind());
    assertEquals(TEST_BACKSTAGE_API_VERSION, backstageCatalogSystemEntity.getApiVersion());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessProjectToBackstageSystemMapperWithTags() {
    projectDTO.setTags(Map.of("foo", "bar", "foo1", "bar1"));

    BackstageCatalogSystemEntity backstageCatalogSystemEntity = harnessProjectToBackstageSystem.map(projectDTO);
    assertEquals(projectDTO.getIdentifier(), backstageCatalogSystemEntity.getMetadata().getIdentifier());
    assertEquals(projectDTO.getOrgIdentifier() + "-" + projectDTO.getIdentifier(),
        backstageCatalogSystemEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(projectDTO.getIdentifier(), backstageCatalogSystemEntity.getMetadata().getName());
    assertEquals(projectDTO.getDescription(), backstageCatalogSystemEntity.getMetadata().getDescription());
    assertNull(backstageCatalogSystemEntity.getMetadata().getAnnotations());
    assertEquals(2, backstageCatalogSystemEntity.getMetadata().getTags().size());
    assertEquals(ENTITY_UNKNOWN_OWNER, backstageCatalogSystemEntity.getSpec().getOwner());
    assertEquals(projectDTO.getOrgIdentifier(), backstageCatalogSystemEntity.getSpec().getDomain());
    assertEquals(BackstageCatalogEntityTypes.SYSTEM.kind, backstageCatalogSystemEntity.getKind());
    assertEquals(TEST_BACKSTAGE_API_VERSION, backstageCatalogSystemEntity.getApiVersion());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessProjectToBackstageSystemMapperWithEntitiesCollision() {
    ProjectDTO projectDTO1 = projectDTO;
    ProjectDTO projectDTO2 = projectDTO;
    projectDTO2.setOrgIdentifier(TEST_ORG_IDENTIFIER1);

    BackstageCatalogSystemEntity backstageCatalogSystemEntity = harnessProjectToBackstageSystem.map(projectDTO1);
    assertEquals(projectDTO1.getIdentifier(), backstageCatalogSystemEntity.getMetadata().getIdentifier());
    assertEquals(projectDTO1.getOrgIdentifier() + "-" + projectDTO1.getIdentifier(),
        backstageCatalogSystemEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(projectDTO1.getIdentifier(), backstageCatalogSystemEntity.getMetadata().getName());

    backstageCatalogSystemEntity = harnessProjectToBackstageSystem.map(projectDTO2);
    assertEquals(projectDTO2.getIdentifier(), backstageCatalogSystemEntity.getMetadata().getIdentifier());
    assertEquals(projectDTO2.getOrgIdentifier() + "-" + projectDTO2.getIdentifier(),
        backstageCatalogSystemEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(projectDTO2.getOrgIdentifier() + "-" + projectDTO2.getIdentifier(),
        backstageCatalogSystemEntity.getMetadata().getName());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessProjectToBackstageSystemMapperForEntityNameTruncate() {
    projectDTO.setIdentifier(TEST_PROJECT_IDENTIFIER_BIG);

    BackstageCatalogSystemEntity backstageCatalogSystemEntity = harnessProjectToBackstageSystem.map(projectDTO);
    assertEquals(projectDTO.getIdentifier(), backstageCatalogSystemEntity.getMetadata().getIdentifier());
    assertEquals(projectDTO.getOrgIdentifier() + "-" + projectDTO.getIdentifier(),
        backstageCatalogSystemEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(TEST_PROJECT_IDENTIFIER_BIG_TRUNCATED, backstageCatalogSystemEntity.getMetadata().getName());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessServiceToBackstageComponentMapper() {
    BackstageCatalogComponentEntity backstageCatalogComponentEntity =
        harnessServiceToBackstageComponent.map(serviceResponseDTO);
    assertEquals(serviceResponseDTO.getIdentifier(), backstageCatalogComponentEntity.getMetadata().getIdentifier());
    assertEquals(serviceResponseDTO.getOrgIdentifier() + "-" + serviceResponseDTO.getProjectIdentifier() + "-"
            + serviceResponseDTO.getIdentifier(),
        backstageCatalogComponentEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(serviceResponseDTO.getIdentifier(), backstageCatalogComponentEntity.getMetadata().getName());
    assertEquals(serviceResponseDTO.getDescription(), backstageCatalogComponentEntity.getMetadata().getDescription());
    assertEquals(Collections.EMPTY_LIST, backstageCatalogComponentEntity.getMetadata().getTags());
    assertEquals(2, backstageCatalogComponentEntity.getMetadata().getAnnotations().size());
    assertEquals("Service", backstageCatalogComponentEntity.getSpec().getType());
    assertEquals(ENTITY_UNKNOWN_LIFECYCLE, backstageCatalogComponentEntity.getSpec().getLifecycle());
    assertEquals(ENTITY_UNKNOWN_OWNER, backstageCatalogComponentEntity.getSpec().getOwner());
    assertEquals(serviceResponseDTO.getOrgIdentifier(), backstageCatalogComponentEntity.getSpec().getDomain());
    assertEquals(serviceResponseDTO.getProjectIdentifier(), backstageCatalogComponentEntity.getSpec().getSystem());
    assertEquals(
        serviceResponseDTO.getProjectIdentifier(), backstageCatalogComponentEntity.getSpec().getHarnessSystem());
    assertEquals(BackstageCatalogEntityTypes.COMPONENT.kind, backstageCatalogComponentEntity.getKind());
    assertEquals(TEST_BACKSTAGE_API_VERSION, backstageCatalogComponentEntity.getApiVersion());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessServiceToBackstageComponentMapperWithEmptyTags() {
    serviceResponseDTO.setTags(new HashMap<>());

    BackstageCatalogComponentEntity backstageCatalogComponentEntity =
        harnessServiceToBackstageComponent.map(serviceResponseDTO);
    assertEquals(serviceResponseDTO.getIdentifier(), backstageCatalogComponentEntity.getMetadata().getIdentifier());
    assertEquals(serviceResponseDTO.getOrgIdentifier() + "-" + serviceResponseDTO.getProjectIdentifier() + "-"
            + serviceResponseDTO.getIdentifier(),
        backstageCatalogComponentEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(serviceResponseDTO.getIdentifier(), backstageCatalogComponentEntity.getMetadata().getName());
    assertEquals(serviceResponseDTO.getDescription(), backstageCatalogComponentEntity.getMetadata().getDescription());
    assertEquals(Collections.EMPTY_LIST, backstageCatalogComponentEntity.getMetadata().getTags());
    assertEquals(2, backstageCatalogComponentEntity.getMetadata().getAnnotations().size());
    assertEquals("Service", backstageCatalogComponentEntity.getSpec().getType());
    assertEquals(ENTITY_UNKNOWN_LIFECYCLE, backstageCatalogComponentEntity.getSpec().getLifecycle());
    assertEquals(ENTITY_UNKNOWN_OWNER, backstageCatalogComponentEntity.getSpec().getOwner());
    assertEquals(serviceResponseDTO.getOrgIdentifier(), backstageCatalogComponentEntity.getSpec().getDomain());
    assertEquals(serviceResponseDTO.getProjectIdentifier(), backstageCatalogComponentEntity.getSpec().getSystem());
    assertEquals(
        serviceResponseDTO.getProjectIdentifier(), backstageCatalogComponentEntity.getSpec().getHarnessSystem());
    assertEquals(BackstageCatalogEntityTypes.COMPONENT.kind, backstageCatalogComponentEntity.getKind());
    assertEquals(TEST_BACKSTAGE_API_VERSION, backstageCatalogComponentEntity.getApiVersion());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessServiceToBackstageComponentMapperWithTags() {
    serviceResponseDTO.setTags(Map.of("foo", "bar", "foo1", "bar1"));

    BackstageCatalogComponentEntity backstageCatalogComponentEntity =
        harnessServiceToBackstageComponent.map(serviceResponseDTO);
    assertEquals(serviceResponseDTO.getIdentifier(), backstageCatalogComponentEntity.getMetadata().getIdentifier());
    assertEquals(serviceResponseDTO.getOrgIdentifier() + "-" + serviceResponseDTO.getProjectIdentifier() + "-"
            + serviceResponseDTO.getIdentifier(),
        backstageCatalogComponentEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(serviceResponseDTO.getIdentifier(), backstageCatalogComponentEntity.getMetadata().getName());
    assertEquals(serviceResponseDTO.getDescription(), backstageCatalogComponentEntity.getMetadata().getDescription());
    assertEquals(2, backstageCatalogComponentEntity.getMetadata().getTags().size());
    assertEquals(2, backstageCatalogComponentEntity.getMetadata().getAnnotations().size());
    assertEquals("https://localhost:8181/ng/account/" + serviceResponseDTO.getAccountId() + "/home/orgs/"
            + serviceResponseDTO.getOrgIdentifier() + "/projects/" + serviceResponseDTO.getProjectIdentifier()
            + "/details",
        backstageCatalogComponentEntity.getMetadata().getAnnotations().get("harness.io/project-url"));
    assertEquals(serviceResponseDTO.getIdentifier(),
        backstageCatalogComponentEntity.getMetadata().getAnnotations().get("harness.io/cd-serviceId"));
    assertEquals("Service", backstageCatalogComponentEntity.getSpec().getType());
    assertEquals(ENTITY_UNKNOWN_LIFECYCLE, backstageCatalogComponentEntity.getSpec().getLifecycle());
    assertEquals(ENTITY_UNKNOWN_OWNER, backstageCatalogComponentEntity.getSpec().getOwner());
    assertEquals(serviceResponseDTO.getOrgIdentifier(), backstageCatalogComponentEntity.getSpec().getDomain());
    assertEquals(serviceResponseDTO.getProjectIdentifier(), backstageCatalogComponentEntity.getSpec().getSystem());
    assertEquals(
        serviceResponseDTO.getProjectIdentifier(), backstageCatalogComponentEntity.getSpec().getHarnessSystem());
    assertEquals(BackstageCatalogEntityTypes.COMPONENT.kind, backstageCatalogComponentEntity.getKind());
    assertEquals(TEST_BACKSTAGE_API_VERSION, backstageCatalogComponentEntity.getApiVersion());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessServiceToBackstageComponentMapperWithEntitiesCollision() {
    ServiceResponseDTO serviceResponseDTO1 = serviceResponseDTO;
    ServiceResponseDTO serviceResponseDTO2 = serviceResponseDTO;
    serviceResponseDTO2.setOrgIdentifier(TEST_ORG_IDENTIFIER1);
    serviceResponseDTO2.setProjectIdentifier(TEST_PROJECT_IDENTIFIER1);

    BackstageCatalogComponentEntity backstageCatalogComponentEntity =
        harnessServiceToBackstageComponent.map(serviceResponseDTO1);
    assertEquals(serviceResponseDTO1.getIdentifier(), backstageCatalogComponentEntity.getMetadata().getIdentifier());
    assertEquals(serviceResponseDTO1.getOrgIdentifier() + "-" + serviceResponseDTO1.getProjectIdentifier() + "-"
            + serviceResponseDTO1.getIdentifier(),
        backstageCatalogComponentEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(serviceResponseDTO1.getIdentifier(), backstageCatalogComponentEntity.getMetadata().getName());

    backstageCatalogComponentEntity = harnessServiceToBackstageComponent.map(serviceResponseDTO2);
    assertEquals(serviceResponseDTO2.getIdentifier(), backstageCatalogComponentEntity.getMetadata().getIdentifier());
    assertEquals(serviceResponseDTO2.getOrgIdentifier() + "-" + serviceResponseDTO2.getProjectIdentifier() + "-"
            + serviceResponseDTO2.getIdentifier(),
        backstageCatalogComponentEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(serviceResponseDTO2.getOrgIdentifier() + "-" + serviceResponseDTO2.getProjectIdentifier() + "-"
            + serviceResponseDTO2.getIdentifier(),
        backstageCatalogComponentEntity.getMetadata().getName());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHarnessServiceToBackstageComponentMapperForEntityNameTruncate() {
    serviceResponseDTO.setIdentifier(TEST_SERVICE_IDENTIFIER_BIG);

    BackstageCatalogComponentEntity backstageCatalogComponentEntity =
        harnessServiceToBackstageComponent.map(serviceResponseDTO);
    assertEquals(serviceResponseDTO.getIdentifier(), backstageCatalogComponentEntity.getMetadata().getIdentifier());
    assertEquals(serviceResponseDTO.getOrgIdentifier() + "-" + serviceResponseDTO.getProjectIdentifier() + "-"
            + serviceResponseDTO.getIdentifier(),
        backstageCatalogComponentEntity.getMetadata().getAbsoluteIdentifier());
    assertEquals(TEST_SERVICE_IDENTIFIER_BIG_TRUNCATED, backstageCatalogComponentEntity.getMetadata().getName());
  }

  @After
  public void teardown() throws Exception {
    openMocks.close();
  }
}
