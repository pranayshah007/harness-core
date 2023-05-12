/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.signalfxconnectormapper;

import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.signalfxconnector.SignalFXConnector;
import io.harness.connector.mappers.signalfxmapper.SignalFXDTOToEntity;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class SignalFXDTOToEntityTest extends CategoryTest {
  private static final String urlWithoutSlash = "https://api.us1.signalfx.com";
  private static final String urlWithSlash = urlWithoutSlash + "/";
  private static final String apiToken = "signalfx_api_token";

  @InjectMocks SignalFXDTOToEntity signalFXDTOToEntity;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testToSignalFXConnector() {
    SignalFXConnectorDTO connectorDTO = SignalFXConnectorDTO.builder()
                                            .url(urlWithoutSlash)
                                            .delegateSelectors(Collections.emptySet())
                                            .apiTokenRef(SecretRefHelper.createSecretRef(apiToken))
                                            .build();

    SignalFXConnector signalFXConnector = signalFXDTOToEntity.toConnectorEntity(connectorDTO);
    assertThat(signalFXConnector).isNotNull();
    assertThat(signalFXConnector.getUrl()).isEqualTo(urlWithSlash);
    assertThat(signalFXConnector.getApiTokenRef()).isEqualTo(apiToken);

    connectorDTO = SignalFXConnectorDTO.builder()
                       .url(urlWithSlash)
                       .delegateSelectors(Collections.emptySet())
                       .apiTokenRef(SecretRefHelper.createSecretRef(apiToken))
                       .build();

    signalFXConnector = signalFXDTOToEntity.toConnectorEntity(connectorDTO);
    assertThat(signalFXConnector).isNotNull();
    assertThat(signalFXConnector.getUrl()).isEqualTo(urlWithSlash);
    assertThat(signalFXConnector.getApiTokenRef()).isEqualTo(apiToken);
  }
}
