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
import io.harness.connector.mappers.signalfxmapper.SignalFXEntityToDTO;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class SignalFXEntityToDTOTest extends CategoryTest {
  static final String apiToken = "signalfx_api_token";
  static final String urlWithoutSlash = "https://api.us1.signalfx.com";
  static final String urlWithSlash = urlWithoutSlash + "/";

  @InjectMocks SignalFXEntityToDTO signalFXEntityToDTO;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testToSignalFXDTO() {
    SignalFXConnector signalFXConnector =
        SignalFXConnector.builder().apiTokenRef(apiToken).url(urlWithoutSlash).build();
    SignalFXConnectorDTO signalFXConnectorDTO = signalFXEntityToDTO.createConnectorDTO(signalFXConnector);

    assertThat(signalFXConnectorDTO.getApiTokenRef().toSecretRefStringValue()).isEqualTo(apiToken);
    assertThat(signalFXConnectorDTO.getUrl()).isEqualTo(urlWithSlash);

    signalFXConnector = SignalFXConnector.builder().apiTokenRef(apiToken).url(urlWithSlash).build();
    signalFXConnectorDTO = signalFXEntityToDTO.createConnectorDTO(signalFXConnector);

    assertThat(signalFXConnectorDTO.getApiTokenRef().toSecretRefStringValue()).isEqualTo(apiToken);
    assertThat(signalFXConnectorDTO.getUrl()).isEqualTo(urlWithSlash);
  }
}
