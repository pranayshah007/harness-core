/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.delegate.beans.connector.ConnectorType.CUSTOM_HEALTH;
import static io.harness.rule.OwnerRule.ANJAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.embedded.customhealthconnector.CustomHealthConnector;
import io.harness.connector.entities.embedded.customhealthconnector.CustomHealthConnectorKeyAndValue;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthKeyAndValue;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.encryption.SecretRefHelper;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@OwnedBy(HarnessTeam.CV)
public class CustomHealthConnectorTest extends CategoryTest {
  @Mock ConnectorMapper connectorMapper;
  @Mock ConnectorRepository connectorRepository;
  @Mock ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  @Mock private Map<String, ConnectionValidator> connectionValidatorMap;

  @InjectMocks @Spy DefaultConnectorServiceImpl connectorService;
  @Mock GitSyncSdkService gitSyncSdkService;

  String baseURL = "https://randomurl.com/";
  List<CustomHealthConnectorKeyAndValue> params;
  List<CustomHealthConnectorKeyAndValue> headers;
  String validationPath = "/sfsdf?ssdf=232";
  String validatonBody = "{'foo': 'bar'}";
  CustomHealthMethod customHealthMethod = CustomHealthMethod.GET;

  String identifier = "customHealth";
  String name = "customHealth";

  ConnectorDTO connectorDTO;
  ConnectorResponseDTO connectorResponseDTO;
  CustomHealthConnector customHealthConnector;
  String accountIdentifier = "accountIdentifier";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testCreateCustomHealthConnector() {
    setupWithParamsAndHeaders();
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Optional.of(customHealthConnector));
    ConnectorResponseDTO connectorResponseDTO = connectorService.create(connectorDTO, accountIdentifier);
    ensureCustomHealthConnectorFieldsAreCorrect(connectorResponseDTO);
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCustomHealthConnector() {
    setupWithParamsAndHeaders();
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Optional.of(customHealthConnector));
    ConnectorResponseDTO connectorDTO = connectorService.get(accountIdentifier, null, null, identifier).get();
    ensureCustomHealthConnectorFieldsAreCorrect(connectorDTO);
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testCustomHealthConnector_withNullHeadersAndParams() {
    setupWithNullHeadersAndParams();
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Optional.of(customHealthConnector));

    connectorService.create(connectorDTO, accountIdentifier);
    ConnectorResponseDTO connectorDTO = connectorService.get(accountIdentifier, null, null, identifier).get();

    CustomHealthConnectorDTO healthConnector =
        (CustomHealthConnectorDTO) connectorDTO.getConnector().getConnectorConfig();
    assertThat(healthConnector.getParams()).isEmpty();
    assertThat(healthConnector.getHeaders()).isEmpty();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testCustomHealthConnector_withPostMethodAndBody() {
    setupWithPostMethodAndBody();
    when(connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(Optional.of(customHealthConnector));

    connectorService.create(connectorDTO, accountIdentifier);
    ConnectorResponseDTO connectorDTO = connectorService.get(accountIdentifier, null, null, identifier).get();

    CustomHealthConnectorDTO healthConnector =
        (CustomHealthConnectorDTO) connectorDTO.getConnector().getConnectorConfig();
    assertThat(healthConnector.getValidationBody()).isEqualTo(validatonBody);
    assertThat(healthConnector.getMethod()).isEqualTo(CustomHealthMethod.POST);
  }

  private void setupWithParamsAndHeaders() {
    params = Arrays.asList(new CustomHealthConnectorKeyAndValue[] {
        CustomHealthConnectorKeyAndValue.builder().key("identifier").value("sdfsdf").isValueEncrypted(false).build()});
    headers = Arrays.asList(new CustomHealthConnectorKeyAndValue[] {CustomHealthConnectorKeyAndValue.builder()
                                                                        .key("api_key")
                                                                        .encryptedValueRef("21312sdfs")
                                                                        .isValueEncrypted(true)
                                                                        .build(),
        CustomHealthConnectorKeyAndValue.builder()
            .key("api_key")
            .encryptedValueRef("21312sdfs")
            .isValueEncrypted(true)
            .build()});

    customHealthConnector = CustomHealthConnector.builder()
                                .baseURL(baseURL)
                                .headers(headers)
                                .params(params)
                                .validationPath(validationPath)
                                .method(customHealthMethod)
                                .build();
    customHealthConnector.setType(CUSTOM_HEALTH);
    customHealthConnector.setIdentifier(identifier);
    customHealthConnector.setName(name);

    CustomHealthConnectorDTO customHealthConnectorDTO =
        CustomHealthConnectorDTO.builder()
            .baseURL(baseURL)
            .validationPath(validationPath)
            .method(customHealthMethod)
            .headers(headers.stream()
                         .map(header
                             -> CustomHealthKeyAndValue.builder()
                                    .isValueEncrypted(header.isValueEncrypted())
                                    .value(header.getValue())
                                    .key(header.getKey())
                                    .encryptedValueRef(SecretRefHelper.createSecretRef(header.getEncryptedValueRef()))
                                    .build())
                         .collect(Collectors.toList()))
            .params(params.stream()
                        .map(param
                            -> CustomHealthKeyAndValue.builder()
                                   .isValueEncrypted(param.isValueEncrypted())
                                   .value(param.getValue())
                                   .key(param.getKey())
                                   .encryptedValueRef(SecretRefHelper.createSecretRef(param.getEncryptedValueRef()))
                                   .build())
                        .collect(Collectors.toList()))
            .build();
    mockFunctions(customHealthConnectorDTO);
  }

  private void setupWithNullHeadersAndParams() {
    customHealthConnector = CustomHealthConnector.builder()
                                .baseURL(baseURL)
                                .headers(null)
                                .params(null)
                                .validationPath(validationPath)
                                .method(customHealthMethod)
                                .build();
    customHealthConnector.setType(CUSTOM_HEALTH);
    customHealthConnector.setIdentifier(identifier);
    customHealthConnector.setName(name);
    customHealthMethod = CustomHealthMethod.POST;

    CustomHealthConnectorDTO customHealthConnectorDTO = CustomHealthConnectorDTO.builder()
                                                            .baseURL(baseURL)
                                                            .validationPath(validationPath)
                                                            .method(customHealthMethod)
                                                            .headers(null)
                                                            .params(null)
                                                            .build();
    mockFunctions(customHealthConnectorDTO);
  }

  private void setupWithPostMethodAndBody() {
    headers = Arrays.asList(new CustomHealthConnectorKeyAndValue[] {CustomHealthConnectorKeyAndValue.builder()
                                                                        .key("api_key")
                                                                        .encryptedValueRef("21312sdfs")
                                                                        .isValueEncrypted(true)
                                                                        .build(),
        CustomHealthConnectorKeyAndValue.builder()
            .key("api_key")
            .encryptedValueRef("21312sdfs")
            .isValueEncrypted(true)
            .build()});
    customHealthConnector = CustomHealthConnector.builder()
                                .baseURL(baseURL)
                                .headers(headers)
                                .params(null)
                                .validationPath(validationPath)
                                .validationBody(validatonBody)
                                .method(CustomHealthMethod.POST)
                                .build();
    customHealthConnector.setType(CUSTOM_HEALTH);
    customHealthConnector.setIdentifier(identifier);
    customHealthConnector.setName(name);

    CustomHealthConnectorDTO customHealthConnectorDTO =
        CustomHealthConnectorDTO.builder()
            .baseURL(baseURL)
            .validationPath(validationPath)
            .validationBody(validatonBody)
            .method(CustomHealthMethod.POST)
            .headers(headers.stream()
                         .map(header
                             -> CustomHealthKeyAndValue.builder()
                                    .isValueEncrypted(header.isValueEncrypted())
                                    .value(header.getValue())
                                    .key(header.getKey())
                                    .encryptedValueRef(SecretRefHelper.createSecretRef(header.getEncryptedValueRef()))
                                    .build())
                         .collect(Collectors.toList()))
            .params(null)
            .build();
    mockFunctions(customHealthConnectorDTO);
  }

  private void mockFunctions(CustomHealthConnectorDTO customHealthConnectorDTO) {
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(identifier)
                                         .connectorType(CUSTOM_HEALTH)
                                         .connectorConfig(customHealthConnectorDTO)
                                         .build();
    connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfo).build();
    connectorResponseDTO = ConnectorResponseDTO.builder().connector(connectorInfo).build();
    when(connectorRepository.save(customHealthConnector, connectorDTO, ChangeType.ADD, null))
        .thenReturn(customHealthConnector);
    when(connectorMapper.writeDTO(customHealthConnector)).thenReturn(connectorResponseDTO);
    when(connectorMapper.toConnector(connectorDTO, accountIdentifier)).thenReturn(customHealthConnector);
    when(gitSyncSdkService.isGitSyncEnabled(accountIdentifier, null, null)).thenReturn(true);
    doNothing().when(connectorService).assurePredefined(any(), any());
  }

  private void ensureCustomHealthConnectorFieldsAreCorrect(ConnectorResponseDTO connectorResponse) {
    ConnectorInfoDTO connector = connectorResponse.getConnector();
    assertThat(connector).isNotNull();
    assertThat(connector.getName()).isEqualTo(name);
    assertThat(connector.getIdentifier()).isEqualTo(identifier);
    assertThat(connector.getConnectorType()).isEqualTo(CUSTOM_HEALTH);
    CustomHealthConnectorDTO customHealthConnectorDTO = (CustomHealthConnectorDTO) connector.getConnectorConfig();
    assertThat(customHealthConnectorDTO).isNotNull();
    assertThat(customHealthConnectorDTO.getBaseURL()).isEqualTo(baseURL);
    assertThat(customHealthConnectorDTO.getValidationPath()).isEqualTo(validationPath.substring(1));
    assertThat(customHealthConnectorDTO.getMethod()).isEqualTo(customHealthMethod);
    assertThat(customHealthConnectorDTO.getHeaders())
        .isEqualTo(headers.stream()
                       .map(header
                           -> CustomHealthKeyAndValue.builder()
                                  .isValueEncrypted(header.isValueEncrypted())
                                  .value(header.getValue())
                                  .key(header.getKey())
                                  .encryptedValueRef(SecretRefHelper.createSecretRef(header.getEncryptedValueRef()))
                                  .build())
                       .collect(Collectors.toList()));
    assertThat(customHealthConnectorDTO.getParams())
        .isEqualTo(params.stream()
                       .map(params
                           -> CustomHealthKeyAndValue.builder()
                                  .isValueEncrypted(params.isValueEncrypted())
                                  .value(params.getValue())
                                  .key(params.getKey())
                                  .encryptedValueRef(SecretRefHelper.createSecretRef(params.getEncryptedValueRef()))
                                  .build())
                       .collect(Collectors.toList()));
  }
}
