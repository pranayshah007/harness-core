/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cd.CDLicenseType;
import io.harness.cdng.usage.dto.LicenseDateUsageDTO;
import io.harness.cdng.usage.dto.LicenseDateUsageParams;
import io.harness.cdng.usage.impl.CDLicenseUsageImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.usage.params.filter.LicenseDateUsageReportType;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.beans.Service.ServiceKeys;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.CDP)
public class CDLicenseUsageResourceTest extends CategoryTest {
  private final String ACCOUNT_IDENTIFIER = "ACCOUNT_ID";
  private final String ORG_IDENTIFIER = "ORG_ID";
  private final String PROJ_IDENTIFIER = "PROJECT_ID";
  private final String IDENTIFIER = "IDENTIFIER";

  private ServiceEntity serviceEntity;
  private ServiceResponseDTO serviceResponseDTO;

  @Mock private ServiceEntityService serviceEntityService;
  @Mock private CDLicenseUsageImpl cdLicenseUsageService;
  @InjectMocks private CDLicenseUsageResource cdLicenseUsageResource;

  @Before
  public void setup() {
    serviceEntity = ServiceEntity.builder()
                        .accountId(ACCOUNT_IDENTIFIER)
                        .identifier(IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .name("Service")
                        .tags(singletonList(NGTag.builder().key("k1").value("v1").build()))
                        .yaml("service:\n  name: \"Service\"\n  identifier: \"IDENTIFIER\"\n  "
                            + "orgIdentifier: \"ORG_ID\"\n  projectIdentifier: \"PROJECT_ID\"\n  tags:\n    "
                            + "k1: \"v1\"\n")
                        .version(0L)
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
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testListServices() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> serviceList = new PageImpl<>(Collections.singletonList(serviceEntity), pageable, 1);
    when(serviceEntityService.list(any(), any())).thenReturn(serviceList);
    List<ServiceResponse> content =
        cdLicenseUsageResource
            .getAllServices(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER, "services", 0, 10, null)
            .getData()
            .getContent();

    assertThat(content).isNotNull();
    assertThat(content.size()).isEqualTo(1);
    assertThat(content.get(0).getService()).isEqualTo(serviceResponseDTO);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testListServicesWithInvalidAccountIdentifier() {
    when(serviceEntityService.list(any(), any()))
        .thenThrow(new InvalidRequestException(format("Invalid account identifier, %s", ACCOUNT_IDENTIFIER)));

    assertThatThrownBy(()
                           -> cdLicenseUsageResource.getAllServices(
                               ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJ_IDENTIFIER, "services", 0, 10, null))
        .hasMessage(format("Invalid account identifier, %s", ACCOUNT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetServiceInstancesDateUsage() {
    Map<String, Integer> serviceInstancesUsage = getMonthlyServiceUsage();
    when(cdLicenseUsageService.getLicenseDateUsage(any(), any(), any()))
        .thenReturn(LicenseDateUsageDTO.builder()
                        .licenseUsage(serviceInstancesUsage)
                        .reportType(LicenseDateUsageReportType.MONTHLY)
                        .build());

    LicenseDateUsageDTO serviceInstancesDateUsage =
        cdLicenseUsageResource
            .getLicenseDateUsage(ACCOUNT_IDENTIFIER, CDLicenseType.SERVICE_INSTANCES,
                LicenseDateUsageParams.builder()
                    .fromDate("2022-01-01")
                    .toDate("2023-01-01")
                    .reportType(LicenseDateUsageReportType.MONTHLY)
                    .build())
            .getData();

    assertThat(serviceInstancesDateUsage).isNotNull();
    assertThat(serviceInstancesDateUsage.getLicenseUsage().size()).isEqualTo(12);
    Map<String, Integer> serviceInstances = serviceInstancesDateUsage.getLicenseUsage();
    Integer serviceInstancesJanuary = serviceInstances.get("2022-01-01");
    assertThat(serviceInstancesJanuary).isEqualTo(1);
    Integer serviceInstancesDecember = serviceInstances.get("2022-12-01");
    assertThat(serviceInstancesDecember).isEqualTo(18);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetServiceInstancesDateUsageWithInvalidAccountIdentifier() {
    when(cdLicenseUsageService.getLicenseDateUsage(any(), any(), any()))
        .thenThrow(new InvalidRequestException(format("Invalid account identifier, %s", ACCOUNT_IDENTIFIER)));

    assertThatThrownBy(()
                           -> cdLicenseUsageResource.getLicenseDateUsage(ACCOUNT_IDENTIFIER,
                               CDLicenseType.SERVICE_INSTANCES, LicenseDateUsageParams.builder().build()))
        .hasMessage(format("Invalid account identifier, %s", ACCOUNT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class);
  }

  @NotNull
  private Map<String, Integer> getMonthlyServiceUsage() {
    Map<String, Integer> serviceInstancesUsage = new HashMap<>();
    serviceInstancesUsage.put("2022-01-01", 1);
    serviceInstancesUsage.put("2022-02-01", 2);
    serviceInstancesUsage.put("2022-03-01", 3);
    serviceInstancesUsage.put("2022-04-01", 4);
    serviceInstancesUsage.put("2022-05-01", 5);
    serviceInstancesUsage.put("2022-06-01", 1);
    serviceInstancesUsage.put("2022-07-01", 10);
    serviceInstancesUsage.put("2022-08-01", 11);
    serviceInstancesUsage.put("2022-09-01", 1);
    serviceInstancesUsage.put("2022-10-01", 1);
    serviceInstancesUsage.put("2022-11-01", 1);
    serviceInstancesUsage.put("2022-12-01", 18);
    return serviceInstancesUsage;
  }
}
