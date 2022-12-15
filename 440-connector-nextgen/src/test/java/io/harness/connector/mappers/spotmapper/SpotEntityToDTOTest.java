/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.spotmapper;

import static io.harness.rule.OwnerRule.ARVIND;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.spotconnector.SpotConfig;
import io.harness.connector.entities.embedded.spotconnector.SpotPermanentTokenCredential;
import io.harness.connector.entities.embedded.spotconnector.SpotPermanentTokenCredential.SpotPermanentTokenCredentialBuilder;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialType;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class SpotEntityToDTOTest extends CategoryTest {
  @InjectMocks SpotEntityToDTO spotEntityToDTO;
  private static final String accountId = "accountId";
  private static final String accountIdRef = "accountIdRef";
  private static final String accountIdRefDecrypted = "account.accountIdRef";
  private static final String apiTokenRef = "apiTokenRef";
  private static final String apiTokenRefDecrypted = "account.apiTokenRef";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCreateConnectorDTO() {
    SpotConfig spotConfig = getSpotConfig(false);
    validate(spotConfig, false);

    spotConfig = getSpotConfig(true);
    validate(spotConfig, true);
  }

  private void validate(SpotConfig spotConfig, boolean isAccountIdRef) {
    SpotConnectorDTO connectorDTO = spotEntityToDTO.createConnectorDTO(spotConfig);
    assertThat(connectorDTO).isNotNull();
    SpotCredentialDTO credential = connectorDTO.getCredential();
    assertThat(credential.getSpotCredentialType()).isEqualTo(SpotCredentialType.PERMANENT_TOKEN);
    assertThat(credential.getConfig()).isInstanceOf(SpotPermanentTokenConfigSpecDTO.class);
    SpotPermanentTokenConfigSpecDTO config = (SpotPermanentTokenConfigSpecDTO) credential.getConfig();
    assertThat(config.getApiTokenRef().getIdentifier()).isEqualTo(apiTokenRef);
    if (isAccountIdRef) {
      assertThat(config.getSpotAccountIdRef().getIdentifier()).isEqualTo(accountIdRef);
    } else {
      assertThat(config.getSpotAccountId()).isEqualTo(accountId);
    }
  }

  private SpotConfig getSpotConfig(boolean isAccountIdRef) {
    SpotPermanentTokenCredentialBuilder builder = SpotPermanentTokenCredential.builder().apiTokenRef(apiTokenRef);
    if (isAccountIdRef) {
      builder.spotAccountIdRef(accountIdRef);
    } else {
      builder.spotAccountId(accountId);
    }
    SpotPermanentTokenCredential credential = builder.build();
    return SpotConfig.builder().credentialType(SpotCredentialType.PERMANENT_TOKEN).credential(credential).build();
  }
}
