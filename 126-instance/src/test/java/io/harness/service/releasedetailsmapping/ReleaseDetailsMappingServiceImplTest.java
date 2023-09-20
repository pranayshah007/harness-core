/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.releasedetailsmapping;

import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.ReleaseDetailsMappingDTO;
import io.harness.dtos.releasedetailsinfo.ReleaseDetailsDTO;
import io.harness.entities.ReleaseDetailsMapping;
import io.harness.entities.releasedetailsinfo.ReleaseDetails;
import io.harness.entities.releasedetailsinfo.ReleaseEnvDetails;
import io.harness.entities.releasedetailsinfo.ReleaseServiceDetails;
import io.harness.repositories.releasedetailsmapping.ReleaseDetailsMappingRepository;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;

public class ReleaseDetailsMappingServiceImplTest extends InstancesTestBase {
  private static final String ACCOUNT_IDENTIFIER = "account_identifier";
  private static final String ORG_IDENTIFIER = "org_identifier";
  private static final String PROJECT_IDENTIFIER = "project_identifier";
  private static final String INFRA_KEY = "infraKey";
  private static final String RELEASE_KEY = "releaseName_namespace";

  private ReleaseDetailsMapping releaseDetailsMapping;
  private ReleaseDetailsMappingDTO releaseDetailsMappingDTO;
  @Mock ReleaseDetailsMappingRepository releaseDetailsMappingRepository;
  @InjectMocks ReleaseDetailsMappingServiceImpl releaseDetailsMappingService;

  @Before
  public void setUp() {
    ReleaseServiceDetails releaseServiceDetails = ReleaseServiceDetails.builder()
                                                      .serviceName("serv1")
                                                      .orgIdentifier("org1")
                                                      .projectIdentifier("proj1")
                                                      .serviceId("id1")
                                                      .build();

    ReleaseEnvDetails releaseEnvDetails = ReleaseEnvDetails.builder()
                                              .envName("env1")
                                              .projectIdentifier("proj1")
                                              .orgIdentifier("org1")
                                              .infrastructureKind("K8sManifest")
                                              .envId("id2")
                                              .infraName("inf1")
                                              .infraIdentifier("id3")
                                              .build();

    ReleaseDetails releaseDetails =
        ReleaseDetails.builder().serviceDetails(releaseServiceDetails).envDetails(releaseEnvDetails).build();
    ReleaseDetailsDTO releaseDetailsDTO =
        ReleaseDetailsDTO.builder().envDetails(releaseEnvDetails).serviceDetails(releaseServiceDetails).build();

    releaseDetailsMapping = ReleaseDetailsMapping.builder()
                                .orgIdentifier(ORG_IDENTIFIER)
                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                .projectIdentifier(PROJECT_IDENTIFIER)
                                .releaseDetails(releaseDetails)
                                .infraKey(INFRA_KEY)
                                .releaseKey(RELEASE_KEY)
                                .build();
    releaseDetailsMappingDTO = ReleaseDetailsMappingDTO.builder()
                                   .orgIdentifier(ORG_IDENTIFIER)
                                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                                   .projectIdentifier(PROJECT_IDENTIFIER)
                                   .releaseDetailsDTO(releaseDetailsDTO)
                                   .infraKey(INFRA_KEY)
                                   .releaseKey(RELEASE_KEY)
                                   .build();
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void createNewOrReturnExistingReleaseDetailsMappingTest() {
    when(releaseDetailsMappingRepository.save(releaseDetailsMapping)).thenReturn(releaseDetailsMapping);
    assertThat(releaseDetailsMappingService.createNewOrReturnExistingReleaseDetailsMapping(releaseDetailsMappingDTO))
        .isEqualTo(Optional.of(releaseDetailsMappingDTO));
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCreateNewOrReturnExistingReleaseDetailsMappingDuplicateKeyException() {
    when(releaseDetailsMappingRepository.save(releaseDetailsMapping)).thenThrow(new DuplicateKeyException("duplicate"));
    when(releaseDetailsMappingRepository
             .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndReleaseKeyAndInfraKey(
                 anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.of(releaseDetailsMapping));
    assertThat(releaseDetailsMappingService.createNewOrReturnExistingReleaseDetailsMapping(releaseDetailsMappingDTO))
        .isEqualTo(Optional.of(releaseDetailsMappingDTO));
  }
}
