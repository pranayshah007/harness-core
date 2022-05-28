/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mappers;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceElementMapperTest extends CategoryTest {
  ServiceRequestDTO serviceRequestDTO;
  ServiceResponseDTO serviceResponseDTO;
  ServiceEntity responseServiceEntity;
  ServiceEntity requestServiceEntity;
  List<NGTag> tags;

  @Before
  public void setUp() {
    tags = Arrays.asList(NGTag.builder().key("k1").value("v1").build(), NGTag.builder().key("k2").value("v2").build());
    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier("IDENTIFIER")
                            .orgIdentifier("ORG_ID")
                            .projectIdentifier("PROJECT_ID")
                            .name("Service")
                            .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                            .yaml("service:\n  name: \"Service\"\n  identifier: \"IDENTIFIER\"\n  "
                                + "orgIdentifier: \"ORG_ID\"\n  projectIdentifier: \"PROJECT_ID\"\n  tags:\n    "
                                + "k1: \"v1\"\n    k2: \"v2\"\n")
                            .build();

    serviceResponseDTO = ServiceResponseDTO.builder()
                             .accountId("ACCOUNT_ID")
                             .identifier("IDENTIFIER")
                             .orgIdentifier("ORG_ID")
                             .projectIdentifier("PROJECT_ID")
                             .name("Service")
                             .deleted(false)
                             .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                             .build();
    responseServiceEntity = ServiceEntity.builder()
                                .id("UUID")
                                .accountId("ACCOUNT_ID")
                                .identifier("IDENTIFIER")
                                .orgIdentifier("ORG_ID")
                                .projectIdentifier("PROJECT_ID")
                                .name("Service")
                                .deleted(false)
                                .tags(tags)
                                .build();

    requestServiceEntity = ServiceEntity.builder()
                               .accountId("ACCOUNT_ID")
                               .identifier("IDENTIFIER")
                               .orgIdentifier("ORG_ID")
                               .projectIdentifier("PROJECT_ID")
                               .name("Service")
                               .deleted(false)
                               .tags(tags)
                               .yaml("service:\n  name: \"Service\"\n  identifier: \"IDENTIFIER\"\n  "
                                   + "orgIdentifier: \"ORG_ID\"\n  projectIdentifier: \"PROJECT_ID\"\n  tags:\n    "
                                   + "k1: \"v1\"\n    k2: \"v2\"\n")
                               .build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testToServiceEntity() {
    ServiceEntity mappedService = ServiceElementMapper.toServiceEntity("ACCOUNT_ID", serviceRequestDTO);
    assertThat(mappedService).isNotNull();
    assertThat(mappedService).isEqualTo(requestServiceEntity);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testWriteDTO() {
    ServiceResponseDTO serviceResponseDTO = ServiceElementMapper.writeDTO(responseServiceEntity);
    assertThat(serviceResponseDTO).isNotNull();
    assertThat(serviceResponseDTO).isEqualTo(serviceResponseDTO);
  }
}
