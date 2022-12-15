/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.elkconnectormapper;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.elkconnector.ELKConnector;
import io.harness.connector.mappers.elkmapper.ELKEntityToDTO;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsAuthType;
import io.harness.delegate.beans.connector.elkconnector.ELKAuthType;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class ELKEntityToDTOTest extends CategoryTest {
  @InjectMocks ELKEntityToDTO elkEntityToDTO;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateAppDynamicsConnectorDTO() {
    String username = "username";
    String passwordRef = "passwordRef";
    String url = "url";
    String accountId = "accountId";

    ELKConnector elkConnector = ELKConnector.builder()
                                    .username(username)
                                    .passwordRef(passwordRef)
                                    .authType(ELKAuthType.USERNAME_PASSWORD)
                                    .url(url)
                                    .build();

    ELKConnectorDTO elkConnectorDTO = elkEntityToDTO.createConnectorDTO(elkConnector);
    assertThat(elkConnectorDTO).isNotNull();
    assertThat(elkConnectorDTO.getUsername()).isEqualTo(elkConnector.getUsername());
    assertThat(elkConnectorDTO.getPasswordRef()).isNotNull();
    assertThat(elkConnectorDTO.getPasswordRef().getIdentifier()).isEqualTo(elkConnector.getPasswordRef());
    assertThat(elkConnectorDTO.getUrl()).isEqualTo(elkConnector.getUrl() + "/");

    assertThat(elkConnectorDTO.getAuthType().name()).isEqualTo(AppDynamicsAuthType.USERNAME_PASSWORD.name());
    assertThat(elkConnectorDTO.getApiKeyId()).isNull();
    assertThat(elkConnectorDTO.getApiKeyRef().getIdentifier()).isNull();
  }
}
