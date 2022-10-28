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
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialType;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO.SpotPermanentTokenConfigSpecDTOBuilder;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class SpotDTOToEntityTest extends CategoryTest {
  @InjectMocks SpotDTOToEntity spotDTOToEntity;

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
  public void testToConnectorEntity() {
    SpotConfig spotConfig = spotDTOToEntity.toConnectorEntity(getConnectorConfigDTO(true));
    validate(spotConfig, true);

    spotConfig = spotDTOToEntity.toConnectorEntity(getConnectorConfigDTO(false));
    validate(spotConfig, false);
  }

  private void validate(SpotConfig spotConfig, boolean isAccountIdRef) {
    assertThat(spotConfig).isNotNull();
    assertThat(spotConfig.getCredentialType()).isEqualTo(SpotCredentialType.PERMANENT_TOKEN);
    assertThat(spotConfig.getCredential()).isInstanceOf(SpotPermanentTokenCredential.class);
    SpotPermanentTokenCredential credential = (SpotPermanentTokenCredential) spotConfig.getCredential();
    if (isAccountIdRef) {
      assertThat(credential.getSpotAccountIdRef()).isEqualTo(accountIdRefDecrypted);
    } else {
      assertThat(credential.getSpotAccountId()).isEqualTo(accountId);
      assertThat(credential.getSpotAccountIdRef()).isNull();
    }
    assertThat(credential.getApiTokenRef()).isEqualTo(apiTokenRefDecrypted);
  }

  private SpotConnectorDTO getConnectorConfigDTO(boolean isAccountIdRef) {
    SpotPermanentTokenConfigSpecDTOBuilder manualConfigBuilder = SpotPermanentTokenConfigSpecDTO.builder();
    if (isAccountIdRef) {
      manualConfigBuilder.spotAccountIdRef(
          SecretRefData.builder().identifier(accountIdRef).scope(Scope.ACCOUNT).build());
    } else {
      manualConfigBuilder.spotAccountId(accountId);
    }
    SpotPermanentTokenConfigSpecDTO manualConfig =
        manualConfigBuilder.apiTokenRef(SecretRefData.builder().identifier(apiTokenRef).scope(Scope.ACCOUNT).build())
            .build();

    return SpotConnectorDTO.builder()
        .credential(SpotCredentialDTO.builder()
                        .spotCredentialType(SpotCredentialType.PERMANENT_TOKEN)
                        .config(manualConfig)
                        .build())
        .build();
  }
}
