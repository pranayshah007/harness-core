/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cd.CDLicenseType;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.entities.modules.CDModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class CDLicenseObjectMapperTest extends CategoryTest {
  @InjectMocks CDLicenseObjectMapper objectMapper;
  private CDModuleLicense servicesModuleLicense;
  private CDModuleLicense serviceInstancesModuleLicense;
  private CDModuleLicenseDTO servicesModuleLicenseDTO;
  private CDModuleLicenseDTO serviceInstancesModuleLicenseDTO;
  private static final int DEFAULT_MAX_WORK_LOAD = 5;
  private static final int DEFAULT_SERVICE_INSTANCE_LOAD = 8;

  @Before
  public void setUp() {
    initMocks(this);
    servicesModuleLicense =
        CDModuleLicense.builder().cdLicenseType(CDLicenseType.SERVICES).workloads(DEFAULT_MAX_WORK_LOAD).build();
    servicesModuleLicenseDTO =
        CDModuleLicenseDTO.builder().workloads(DEFAULT_MAX_WORK_LOAD).cdLicenseType(CDLicenseType.SERVICES).build();

    serviceInstancesModuleLicense = CDModuleLicense.builder()
                                        .cdLicenseType(CDLicenseType.SERVICE_INSTANCES)
                                        .serviceInstances(DEFAULT_SERVICE_INSTANCE_LOAD)
                                        .build();
    serviceInstancesModuleLicenseDTO = CDModuleLicenseDTO.builder()
                                           .serviceInstances(DEFAULT_SERVICE_INSTANCE_LOAD)
                                           .cdLicenseType(CDLicenseType.SERVICE_INSTANCES)
                                           .build();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testToServicesDTO() {
    ModuleLicenseDTO result = objectMapper.toDTO(servicesModuleLicense);
    assertThat(result).isEqualTo(servicesModuleLicenseDTO);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testToServicesEntity() {
    ModuleLicense result = objectMapper.toEntity(servicesModuleLicenseDTO);
    assertThat(result).isEqualTo(servicesModuleLicense);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testToServiceInstancesDTO() {
    ModuleLicenseDTO result = objectMapper.toDTO(serviceInstancesModuleLicense);
    assertThat(result).isEqualTo(serviceInstancesModuleLicenseDTO);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testToServiceInstancesEntity() {
    ModuleLicense result = objectMapper.toEntity(serviceInstancesModuleLicenseDTO);
    assertThat(result).isEqualTo(serviceInstancesModuleLicense);
  }
}
