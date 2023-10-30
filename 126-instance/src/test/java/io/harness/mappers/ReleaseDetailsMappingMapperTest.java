/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dtos.ReleaseDetailsMappingDTO;
import io.harness.dtos.releasedetailsinfo.ReleaseDetailsDTO;
import io.harness.entities.ReleaseDetailsMapping;
import io.harness.entities.releasedetailsinfo.ReleaseDetails;
import io.harness.entities.releasedetailsinfo.ReleaseEnvDetails;
import io.harness.entities.releasedetailsinfo.ReleaseServiceDetails;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class ReleaseDetailsMappingMapperTest {
  ReleaseServiceDetails releaseServiceDetails;
  ReleaseEnvDetails releaseEnvDetails;

  ReleaseDetails releaseDetails;
  ReleaseDetailsDTO releaseDetailsDTO;
  @Before
  public void setup() {
    releaseServiceDetails = ReleaseServiceDetails.builder()
                                .serviceName("serv1")
                                .orgIdentifier("org1")
                                .projectIdentifier("proj1")
                                .serviceId("id1")
                                .build();

    releaseEnvDetails = ReleaseEnvDetails.builder()
                            .envName("env1")
                            .projectIdentifier("proj1")
                            .orgIdentifier("org1")
                            .infrastructureKind("K8sManifest")
                            .envId("id2")
                            .infraName("inf1")
                            .infraIdentifier("id3")
                            .build();

    releaseDetails =
        ReleaseDetails.builder().serviceDetails(releaseServiceDetails).envDetails(releaseEnvDetails).build();
    releaseDetailsDTO =
        ReleaseDetailsDTO.builder().envDetails(releaseEnvDetails).serviceDetails(releaseServiceDetails).build();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void test_ReleaseDetailsMappingDTO() {
    ReleaseDetailsMapping releaseDetailsMapping = ReleaseDetailsMapping.builder()
                                                      .infraKey("infraKey")
                                                      .releaseDetails(releaseDetails)
                                                      .releaseKey("release_namespace")
                                                      .accountIdentifier("account")
                                                      .projectIdentifier("project")
                                                      .orgIdentifier("org")
                                                      .build();
    ReleaseDetailsMappingDTO releaseDetailsMappingDTO = ReleaseDetailsMappingMapper.toDTO(releaseDetailsMapping);
    assertThat(releaseDetailsMappingDTO.getReleaseDetailsDTO().getEnvDetails())
        .isEqualTo(releaseDetailsMapping.getReleaseDetails().getEnvDetails());
    assertThat(releaseDetailsMappingDTO.getReleaseDetailsDTO().getServiceDetails())
        .isEqualTo(releaseDetailsMapping.getReleaseDetails().getServiceDetails());
    assertThat(releaseDetailsMappingDTO.getReleaseKey()).isEqualTo(releaseDetailsMapping.getReleaseKey());
    assertThat(releaseDetailsMappingDTO.getInfraKey()).isEqualTo(releaseDetailsMapping.getInfraKey());
    assertThat(releaseDetailsMappingDTO.getOrgIdentifier()).isEqualTo(releaseDetailsMapping.getOrgIdentifier());
    assertThat(releaseDetailsMappingDTO.getProjectIdentifier()).isEqualTo(releaseDetailsMapping.getProjectIdentifier());
    assertThat(releaseDetailsMappingDTO.getAccountIdentifier()).isEqualTo(releaseDetailsMapping.getAccountIdentifier());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void test_ReleaseDetailsMapping() {
    ReleaseDetailsMappingDTO releaseDetailsMappingDTO = ReleaseDetailsMappingDTO.builder()
                                                            .infraKey("infraKey")
                                                            .releaseDetailsDTO(releaseDetailsDTO)
                                                            .releaseKey("release_namespace")
                                                            .accountIdentifier("account")
                                                            .projectIdentifier("project")
                                                            .orgIdentifier("org")
                                                            .build();
    ReleaseDetailsMapping releaseDetailsMapping = ReleaseDetailsMappingMapper.toEntity(releaseDetailsMappingDTO);
    assertThat(releaseDetailsMapping.getReleaseDetails().getEnvDetails())
        .isEqualTo(releaseDetailsMappingDTO.getReleaseDetailsDTO().getEnvDetails());
    assertThat(releaseDetailsMapping.getReleaseDetails().getServiceDetails())
        .isEqualTo(releaseDetailsMappingDTO.getReleaseDetailsDTO().getServiceDetails());
    assertThat(releaseDetailsMapping.getReleaseKey()).isEqualTo(releaseDetailsMappingDTO.getReleaseKey());
    assertThat(releaseDetailsMapping.getInfraKey()).isEqualTo(releaseDetailsMappingDTO.getInfraKey());
    assertThat(releaseDetailsMapping.getOrgIdentifier()).isEqualTo(releaseDetailsMappingDTO.getOrgIdentifier());
    assertThat(releaseDetailsMapping.getProjectIdentifier()).isEqualTo(releaseDetailsMappingDTO.getProjectIdentifier());
    assertThat(releaseDetailsMapping.getAccountIdentifier()).isEqualTo(releaseDetailsMappingDTO.getAccountIdentifier());
  }
}
