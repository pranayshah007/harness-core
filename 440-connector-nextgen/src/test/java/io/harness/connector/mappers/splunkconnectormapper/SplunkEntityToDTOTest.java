/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.splunkconnectormapper;

import static io.harness.rule.OwnerRule.ANSUMAN;
import static io.harness.rule.OwnerRule.NEMANJA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.splunkconnector.SplunkConnector;
import io.harness.delegate.beans.connector.splunkconnector.SplunkAuthType;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class SplunkEntityToDTOTest extends CategoryTest {
  @InjectMocks SplunkEntityToDTO splunkEntityToDTO;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testCreateNoAuthTypeSplunkConnectorDTO() {
    String username = "username";
    String encryptedPassword = "encryptedPassword";
    String splunkUrl = "https://splunk.dev.harness.io:8089";
    String accountId = "accountId";

    SplunkConnector splunkConnector = SplunkConnector.builder()
                                          .username(username)
                                          .passwordRef(encryptedPassword)
                                          .splunkUrl(splunkUrl)
                                          .accountId(accountId)
                                          .build();

    SplunkConnectorDTO splunkConnectorDTO = splunkEntityToDTO.createConnectorDTO(splunkConnector);
    assertThat(splunkConnectorDTO).isNotNull();
    assertThat(splunkConnectorDTO.getTokenRef().getIdentifier()).isNull();
    assertThat(splunkConnectorDTO.getTokenRef().getDecryptedValue()).isNull();
    assertThat(splunkConnectorDTO.getAuthType()).isEqualTo(SplunkAuthType.USER_PASSWORD);
    assertThat(splunkConnectorDTO.getUsername()).isEqualTo(splunkConnector.getUsername());
    assertThat(splunkConnectorDTO.getPasswordRef().getIdentifier()).isEqualTo(splunkConnector.getPasswordRef());
    assertThat(splunkConnectorDTO.getSplunkUrl()).isEqualTo("https://splunk.dev.harness.io:8089/");
    assertThat(splunkConnectorDTO.getAccountId()).isEqualTo(splunkConnector.getAccountId());
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testCreateWithAuthTypeSplunkConnectorDTO() {
    String username = "username";
    String encryptedPassword = "encryptedPassword";
    String splunkUrl = "https://splunk.dev.harness.io:8089";
    String accountId = "accountId";

    SplunkConnector splunkConnector = SplunkConnector.builder()
                                          .username(username)
                                          .passwordRef(encryptedPassword)
                                          .authType(SplunkAuthType.USER_PASSWORD)
                                          .splunkUrl(splunkUrl)
                                          .accountId(accountId)
                                          .build();

    SplunkConnectorDTO splunkConnectorDTO = splunkEntityToDTO.createConnectorDTO(splunkConnector);
    assertThat(splunkConnectorDTO).isNotNull();
    assertThat(splunkConnectorDTO.getTokenRef().getIdentifier()).isNull();
    assertThat(splunkConnectorDTO.getTokenRef().getDecryptedValue()).isNull();
    assertThat(splunkConnectorDTO.getAuthType()).isEqualTo(SplunkAuthType.USER_PASSWORD);
    assertThat(splunkConnectorDTO.getUsername()).isEqualTo(splunkConnector.getUsername());
    assertThat(splunkConnectorDTO.getPasswordRef().getIdentifier()).isEqualTo(splunkConnector.getPasswordRef());
    assertThat(splunkConnectorDTO.getSplunkUrl()).isEqualTo("https://splunk.dev.harness.io:8089/");
    assertThat(splunkConnectorDTO.getAccountId()).isEqualTo(splunkConnector.getAccountId());
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testCreateBearerTokenSplunkConnectorDTO() {
    String username = "username";
    String encryptedToken = "encryptedToken";
    String splunkUrl = "https://splunk.dev.harness.io:8089";
    String accountId = "accountId";

    SplunkConnector splunkConnector = SplunkConnector.builder()
                                          .username(username)
                                          .tokenRef(encryptedToken)
                                          .authType(SplunkAuthType.BEARER_TOKEN)
                                          .splunkUrl(splunkUrl)
                                          .accountId(accountId)
                                          .build();

    SplunkConnectorDTO splunkConnectorDTO = splunkEntityToDTO.createConnectorDTO(splunkConnector);
    assertThat(splunkConnectorDTO).isNotNull();
    assertThat(splunkConnectorDTO.getPasswordRef().getIdentifier()).isNull();
    assertThat(splunkConnectorDTO.getPasswordRef().getDecryptedValue()).isNull();
    assertThat(splunkConnectorDTO.getAuthType()).isEqualTo(SplunkAuthType.BEARER_TOKEN);
    assertThat(splunkConnectorDTO.getUsername()).isEqualTo(splunkConnector.getUsername());
    assertThat(splunkConnectorDTO.getTokenRef().getIdentifier()).isEqualTo(splunkConnector.getTokenRef());
    assertThat(splunkConnectorDTO.getSplunkUrl()).isEqualTo("https://splunk.dev.harness.io:8089/");
    assertThat(splunkConnectorDTO.getAccountId()).isEqualTo(splunkConnector.getAccountId());
  }
}
