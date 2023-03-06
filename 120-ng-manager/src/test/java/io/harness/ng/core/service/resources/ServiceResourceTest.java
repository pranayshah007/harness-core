/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.cdng.manifest.ManifestStoreType.BITBUCKET;
import static io.harness.cdng.manifest.ManifestStoreType.CUSTOM_REMOTE;
import static io.harness.cdng.manifest.ManifestStoreType.GCS;
import static io.harness.cdng.manifest.ManifestStoreType.GIT;
import static io.harness.cdng.manifest.ManifestStoreType.GITHUB;
import static io.harness.cdng.manifest.ManifestStoreType.GITLAB;
import static io.harness.cdng.manifest.ManifestStoreType.HTTP;
import static io.harness.cdng.manifest.ManifestStoreType.OCI;
import static io.harness.cdng.manifest.ManifestStoreType.S3;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Add;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Delete;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Fetch;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.History;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Install;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.List;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Pull;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Rollback;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Template;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Uninstall;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Update;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Upgrade;
import static io.harness.cdng.manifest.yaml.HelmCommandFlagType.Version;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static software.wings.beans.Service.ServiceKeys;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.k8s.model.HelmVersion;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
public class ServiceResourceTest extends CategoryTest {
  private ServiceResource serviceResource;
  private ServiceEntityService serviceEntityService;
  private ServiceEntityManagementService serviceEntityManagementService;

  ServiceRequestDTO serviceRequestDTO;
  ServiceResponseDTO serviceResponseDTO;
  ServiceEntity serviceEntity;
  List<NGTag> tags;

  @Before
  public void setUp() {
    serviceEntityService = mock(ServiceEntityService.class);
    serviceEntityManagementService = mock(ServiceEntityManagementService.class);
    serviceResource = new ServiceResource(serviceEntityService, serviceEntityManagementService);
    tags = Arrays.asList(NGTag.builder().key("k1").value("v1").build());
    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier("IDENTIFIER")
                            .orgIdentifier("ORG_ID")
                            .projectIdentifier("PROJECT_ID")
                            .name("Service")
                            .tags(singletonMap("k1", "v1"))
                            .yaml("service:\n  name: \"Service\"\n  identifier: \"IDENTIFIER\"\n  "
                                + "orgIdentifier: \"ORG_ID\"\n  projectIdentifier: \"PROJECT_ID\"\n  tags:\n    "
                                + "k1: \"v1\"\n")
                            .build();

    serviceResponseDTO = ServiceResponseDTO.builder()
                             .accountId("ACCOUNT_ID")
                             .identifier("IDENTIFIER")
                             .orgIdentifier("ORG_ID")
                             .projectIdentifier("PROJECT_ID")
                             .name("Service")
                             .tags(singletonMap("k1", "v1"))
                             .version(0L)
                             .yaml("service:\n  name: \"Service\"\n  identifier: \"IDENTIFIER\"\n  "
                                 + "orgIdentifier: \"ORG_ID\"\n  projectIdentifier: \"PROJECT_ID\"\n  tags:\n    "
                                 + "k1: \"v1\"\n")
                             .build();
    serviceEntity = ServiceEntity.builder()
                        .accountId("ACCOUNT_ID")
                        .identifier("IDENTIFIER")
                        .orgIdentifier("ORG_ID")
                        .projectIdentifier("PROJECT_ID")
                        .name("Service")
                        .tags(tags)
                        .yaml("service:\n  name: \"Service\"\n  identifier: \"IDENTIFIER\"\n  "
                            + "orgIdentifier: \"ORG_ID\"\n  projectIdentifier: \"PROJECT_ID\"\n  tags:\n    "
                            + "k1: \"v1\"\n")
                        .version(0L)
                        .build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGet() {
    doReturn(Optional.of(serviceEntity))
        .when(serviceEntityService)
        .get("ACCOUNT_ID", serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier(),
            serviceRequestDTO.getIdentifier(), false);

    ServiceResponseDTO serviceResponse =
        serviceResource.get("IDENTIFIER", "ACCOUNT_ID", "ORG_ID", "PROJECT_ID", false).getData();

    assertThat(serviceResponse).isNotNull();
    assertThat(serviceResponse).isEqualTo(serviceResponseDTO);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetForNotFound() {
    doReturn(Optional.empty())
        .when(serviceEntityService)
        .get("ACCOUNT_ID", serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier(),
            serviceRequestDTO.getIdentifier(), false);
    assertThatThrownBy(() -> serviceResource.get("IDENTIFIER", "ACCOUNT_ID", "ORG_ID", "PROJECT_ID", false))
        .hasMessage("Service with identifier [IDENTIFIER] in project [PROJECT_ID], org [ORG_ID] not found");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCreate() {
    doReturn(serviceEntity).when(serviceEntityService).create(any());
    ServiceResponseDTO serviceResponse =
        serviceResource.create(serviceEntity.getAccountId(), serviceRequestDTO).getData();
    assertThat(serviceResponse).isEqualTo(serviceResponseDTO);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDelete() {
    doReturn(true)
        .when(serviceEntityManagementService)
        .deleteService("ACCOUNT_ID", serviceRequestDTO.getOrgIdentifier(), serviceRequestDTO.getProjectIdentifier(),
            serviceRequestDTO.getIdentifier(), null, false);

    Boolean data = serviceResource.delete(null, "IDENTIFIER", "ACCOUNT_ID", "ORG_ID", "PROJECT_ID").getData();
    assertThat(data).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdate() {
    doReturn(serviceEntity).when(serviceEntityService).update(serviceEntity);
    ServiceResponseDTO response =
        serviceResource.update("0", serviceEntity.getAccountId(), serviceRequestDTO).getData();
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(serviceResponseDTO);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpsert() {
    doReturn(serviceEntity).when(serviceEntityService).upsert(serviceEntity, UpsertOptions.DEFAULT);
    ServiceResponseDTO response =
        serviceResource.upsert("0", serviceEntity.getAccountId(), serviceRequestDTO).getData();
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(serviceResponseDTO);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testListServicesWithDESCSort() {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList("ACCOUNT_ID", "ORG_ID", "PROJECT_ID", false);
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    final Page<ServiceEntity> serviceList = new PageImpl<>(Collections.singletonList(serviceEntity), pageable, 1);
    doReturn(serviceList).when(serviceEntityService).list(criteria, pageable);

    List<ServiceResponseDTO> content =
        serviceResource.listServicesForProject(0, 10, "ACCOUNT_ID", "ORG_ID", "PROJECT_ID", null, null)
            .getData()
            .getContent();
    assertThat(content).isNotNull();
    assertThat(content.size()).isEqualTo(1);
    assertThat(content.get(0)).isEqualTo(serviceResponseDTO);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetHelmCommandFlagsForHelmServiceSpec() {
    // V2
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V2, HTTP).getData())
        .containsExactlyInAnyOrder(Template, Delete, Upgrade, Fetch, List, Rollback, Install, History, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V2, GCS).getData())
        .containsExactlyInAnyOrder(Template, Delete, Upgrade, Fetch, List, Rollback, Install, History, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V2, S3).getData())
        .containsExactlyInAnyOrder(Template, Delete, Upgrade, Fetch, List, Rollback, Install, History, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V2, OCI).getData())
        .containsExactlyInAnyOrder(Template, Delete, Upgrade, Fetch, List, Rollback, Install, History, Version);
    assertThat(
        serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V2, CUSTOM_REMOTE).getData())
        .containsExactlyInAnyOrder(Template, Delete, Upgrade, List, Rollback, Install, History, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V2, GIT).getData())
        .containsExactlyInAnyOrder(Template, Delete, Upgrade, List, Rollback, Install, History, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V2, GITHUB).getData())
        .containsExactlyInAnyOrder(Template, Delete, Upgrade, List, Rollback, Install, History, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V2, GITLAB).getData())
        .containsExactlyInAnyOrder(Template, Delete, Upgrade, List, Rollback, Install, History, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V2, BITBUCKET).getData())
        .containsExactlyInAnyOrder(Template, Delete, Upgrade, List, Rollback, Install, History, Version);

    // V3
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V3, HTTP).getData())
        .containsExactlyInAnyOrder(
            Pull, Install, Upgrade, Add, Template, Update, Rollback, History, Uninstall, List, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V3, GCS).getData())
        .containsExactlyInAnyOrder(
            Pull, Install, Upgrade, Add, Template, Update, Rollback, History, Uninstall, List, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V3, S3).getData())
        .containsExactlyInAnyOrder(
            Pull, Install, Upgrade, Add, Template, Update, Rollback, History, Uninstall, List, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V3, OCI).getData())
        .containsExactlyInAnyOrder(
            Pull, Install, Upgrade, Add, Template, Update, Rollback, History, Uninstall, List, Version);
    assertThat(
        serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V3, CUSTOM_REMOTE).getData())
        .containsExactlyInAnyOrder(Install, Upgrade, Template, Rollback, History, Uninstall, List, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V3, GIT).getData())
        .containsExactlyInAnyOrder(History, Install, List, Template, Uninstall, Rollback, Upgrade, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V3, GITHUB).getData())
        .containsExactlyInAnyOrder(History, Install, List, Template, Uninstall, Rollback, Upgrade, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V3, GITLAB).getData())
        .containsExactlyInAnyOrder(History, Install, List, Template, Uninstall, Rollback, Upgrade, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.NATIVE_HELM, HelmVersion.V3, BITBUCKET).getData())
        .containsExactlyInAnyOrder(History, Install, List, Template, Uninstall, Rollback, Upgrade, Version);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetHelmCommandFlagsForK8sServiceSpec() {
    // V2
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V2, HTTP).getData())
        .containsExactlyInAnyOrder(Fetch, Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V2, GCS).getData())
        .containsExactlyInAnyOrder(Fetch, Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V2, S3).getData())
        .containsExactlyInAnyOrder(Fetch, Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V2, OCI).getData())
        .containsExactlyInAnyOrder(Fetch, Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V2, CUSTOM_REMOTE).getData())
        .containsExactlyInAnyOrder(Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V2, GIT).getData())
        .containsExactlyInAnyOrder(Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V2, GITHUB).getData())
        .containsExactlyInAnyOrder(Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V2, GITLAB).getData())
        .containsExactlyInAnyOrder(Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V2, BITBUCKET).getData())
        .containsExactlyInAnyOrder(Template, Version);

    // V3
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V3, HTTP).getData())
        .containsExactlyInAnyOrder(Add, Template, Pull, Update, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V3, GCS).getData())
        .containsExactlyInAnyOrder(Add, Template, Pull, Update, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V3, S3).getData())
        .containsExactlyInAnyOrder(Add, Template, Pull, Update, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V3, OCI).getData())
        .containsExactlyInAnyOrder(Add, Template, Pull, Update, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V3, CUSTOM_REMOTE).getData())
        .containsExactlyInAnyOrder(Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V3, GIT).getData())
        .containsExactlyInAnyOrder(Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V3, GITHUB).getData())
        .containsExactlyInAnyOrder(Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V3, GITLAB).getData())
        .containsExactlyInAnyOrder(Template, Version);
    assertThat(serviceResource.getHelmCommandFlags(ServiceSpecType.KUBERNETES, HelmVersion.V3, BITBUCKET).getData())
        .containsExactlyInAnyOrder(Template, Version);
  }
}
