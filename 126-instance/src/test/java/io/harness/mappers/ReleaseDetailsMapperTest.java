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
import io.harness.dtos.releasedetailsinfo.ReleaseDetailsDTO;
import io.harness.entities.releasedetailsinfo.ReleaseDetails;
import io.harness.entities.releasedetailsinfo.ReleaseEnvDetails;
import io.harness.entities.releasedetailsinfo.ReleaseServiceDetails;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class ReleaseDetailsMapperTest {
  ReleaseServiceDetails releaseServiceDetails;
  ReleaseEnvDetails releaseEnvDetails;
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
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void test_ReleaseDetailsDTO() {
    ReleaseDetails releaseDetails =
        ReleaseDetails.builder().serviceDetails(releaseServiceDetails).envDetails(releaseEnvDetails).build();
    ReleaseDetailsDTO releaseDetailsDTO = ReleaseDetailsMapper.toDTO(releaseDetails);
    assertThat(releaseDetailsDTO.getServiceDetails()).isEqualTo(releaseServiceDetails);
    assertThat(releaseDetailsDTO.getEnvDetails()).isEqualTo(releaseEnvDetails);
    assertThat(releaseDetailsDTO.getEnvDetails().getConnectorRef()).isNull();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void test_ReleaseDetailsDTO_NullCheck() {
    ReleaseDetails releaseDetails = ReleaseDetails.builder().serviceDetails(releaseServiceDetails).build();
    ReleaseDetailsDTO releaseDetailsDTO = ReleaseDetailsMapper.toDTO(releaseDetails);
    assertThat(releaseDetailsDTO.getServiceDetails()).isEqualTo(releaseServiceDetails);
    assertThat(releaseDetailsDTO.getEnvDetails()).isNull();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void test_ReleaseDetails() {
    ReleaseDetailsDTO releaseDetailsDTO =
        ReleaseDetailsDTO.builder().serviceDetails(releaseServiceDetails).envDetails(releaseEnvDetails).build();
    ReleaseDetails releaseDetails = ReleaseDetailsMapper.toEntity(releaseDetailsDTO);
    assertThat(releaseDetails.getServiceDetails()).isEqualTo(releaseServiceDetails);
    assertThat(releaseDetails.getEnvDetails()).isEqualTo(releaseEnvDetails);
    assertThat(releaseDetails.getEnvDetails().getConnectorRef()).isNull();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void test_ReleaseDetails_NullCheck() {
    ReleaseDetailsDTO releaseDetailsDTO = ReleaseDetailsDTO.builder().serviceDetails(releaseServiceDetails).build();
    ReleaseDetails releaseDetails = ReleaseDetailsMapper.toEntity(releaseDetailsDTO);
    assertThat(releaseDetails.getServiceDetails()).isEqualTo(releaseServiceDetails);
    assertThat(releaseDetails.getEnvDetails()).isNull();
  }
}
