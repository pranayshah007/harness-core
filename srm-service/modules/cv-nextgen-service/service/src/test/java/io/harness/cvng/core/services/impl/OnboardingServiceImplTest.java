/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANJAN;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.splunk.SplunkSavedSearchRequest;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class OnboardingServiceImplTest extends CvNextGenTestBase {
  @Inject private OnboardingService onboardingService;
  @Mock private NextGenService nextGenService;
  @Mock private VerificationManagerService verificationManagerService;

  private String accountId;
  private String projectIdentifier;
  private String orgIdentifier;
  private String connectorIdentifier;
  @Before
  public void setup() throws IllegalAccessException {
    initMocks(this);
    accountId = generateUuid();
    projectIdentifier = generateUuid();
    orgIdentifier = generateUuid();
    connectorIdentifier = generateUuid();
    FieldUtils.writeField(onboardingService, "nextGenService", nextGenService, true);
    FieldUtils.writeField(onboardingService, "verificationManagerService", verificationManagerService, true);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetOnboardingResponse() {
    String tracingId = generateUuid();
    ConnectorConfigDTO connectorConfigDTO =
        SplunkConnectorDTO.builder()
            .splunkUrl("https://splunk.dev.harness.io:8089/")
            .username("harnessadmin")
            .passwordRef(SecretRefData.builder().decryptedValue("123".toCharArray()).build())
            .build();
    when(nextGenService.get(eq(accountId), eq(connectorIdentifier), eq(orgIdentifier), eq(projectIdentifier)))
        .thenReturn(Optional.of(ConnectorInfoDTO.builder().connectorConfig(connectorConfigDTO).build()));
    when(verificationManagerService.getDataCollectionResponse(
             eq(accountId), eq(orgIdentifier), eq(projectIdentifier), any()))
        .thenReturn("{\"a\": 1}");
    OnboardingResponseDTO onboardingResponseDTO = onboardingService.getOnboardingResponse(accountId,
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(SplunkSavedSearchRequest.builder().tracingId(tracingId).build())
            .connectorIdentifier(connectorIdentifier)
            .orgIdentifier(orgIdentifier)
            .tracingId(generateUuid())
            .projectIdentifier(projectIdentifier)
            .build());
    assertThat(onboardingResponseDTO.getAccountId()).isEqualTo(accountId);
    assertThat(onboardingResponseDTO.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(onboardingResponseDTO.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(onboardingResponseDTO.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(onboardingResponseDTO.getResult()).isEqualTo(Collections.singletonMap("a", 1));
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetOnboardingResponse_missingTracingId() {
    ConnectorConfigDTO connectorConfigDTO =
        SplunkConnectorDTO.builder()
            .splunkUrl("https://splunk.dev.harness.io:8089/")
            .username("harnessadmin")
            .passwordRef(SecretRefData.builder().decryptedValue("123".toCharArray()).build())
            .build();
    when(nextGenService.get(eq(accountId), eq(connectorIdentifier), eq(orgIdentifier), eq(projectIdentifier)))
        .thenReturn(Optional.of(ConnectorInfoDTO.builder().connectorConfig(connectorConfigDTO).build()));
    when(verificationManagerService.getDataCollectionResponse(
             eq(accountId), eq(orgIdentifier), eq(projectIdentifier), any()))
        .thenReturn("{\"a\": 1}");
    assertThatThrownBy(
        ()
            -> onboardingService.getOnboardingResponse(accountId,
                OnboardingRequestDTO.builder()
                    .dataCollectionRequest(SplunkSavedSearchRequest.builder().tracingId(generateUuid()).build())
                    .connectorIdentifier(connectorIdentifier)
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .build()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Missing tracingId/requestGuid in request");
  }
}
