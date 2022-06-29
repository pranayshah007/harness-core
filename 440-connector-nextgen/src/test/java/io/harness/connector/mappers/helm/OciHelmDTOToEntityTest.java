/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.helm;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.helm.OciHelmConnector;
import io.harness.connector.entities.embedded.helm.OciHelmUsernamePasswordAuthentication;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class OciHelmDTOToEntityTest extends CategoryTest {
  private OciHelmDTOToEntity mapper;

  @Before
  public void setUp() {
    mapper = new OciHelmDTOToEntity();
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testUsernameAndPasswordSecretRefCredentialsDtoToConnectorEntity() {
    SecretRefData usernameSecretRef =
        SecretRefData.builder().identifier("username-secret").scope(Scope.ACCOUNT).build();
    SecretRefData passwordSecretRef =
        SecretRefData.builder().identifier("password-secret").scope(Scope.ACCOUNT).build();

    OciHelmConnectorDTO dto = OciHelmConnectorDTO.builder()
                                  .helmRepoUrl("localhost")
                                  .auth(OciHelmAuthenticationDTO.builder()
                                            .authType(OciHelmAuthType.USER_PASSWORD)
                                            .credentials(OciHelmUsernamePasswordDTO.builder()
                                                             .usernameRef(usernameSecretRef)
                                                             .passwordRef(passwordSecretRef)
                                                             .build())
                                            .build())
                                  .build();

    OciHelmConnector entity = mapper.toConnectorEntity(dto);
    assertThat(entity).isNotNull();
    assertThat(entity.getUrl()).isEqualTo("localhost");
    assertThat(entity.getType()).isEqualTo(ConnectorType.OCI_HELM_REPO);
    assertThat(entity.getAuthType()).isEqualTo(OciHelmAuthType.USER_PASSWORD);
    assertThat(entity.getOciHelmAuthentication()).isNotNull();
    OciHelmUsernamePasswordAuthentication authentication =
        (OciHelmUsernamePasswordAuthentication) entity.getOciHelmAuthentication();
    assertThat(authentication.getPasswordRef()).isEqualTo(passwordSecretRef.toSecretRefStringValue());
    assertThat(authentication.getUsernameRef()).isEqualTo(usernameSecretRef.toSecretRefStringValue());
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void testUsernameAndPasswordOnlySecretRefCredentialsDtoToConnectorEntity() {
    SecretRefData passwordSecretRef =
        SecretRefData.builder().identifier("password-secret").scope(Scope.ACCOUNT).build();

    OciHelmConnectorDTO dto =
        OciHelmConnectorDTO.builder()
            .helmRepoUrl("localhost")
            .auth(OciHelmAuthenticationDTO.builder()
                      .authType(OciHelmAuthType.USER_PASSWORD)
                      .credentials(
                          OciHelmUsernamePasswordDTO.builder().username("test").passwordRef(passwordSecretRef).build())
                      .build())
            .build();

    OciHelmConnector entity = mapper.toConnectorEntity(dto);
    assertThat(entity).isNotNull();
    assertThat(entity.getUrl()).isEqualTo("localhost");
    assertThat(entity.getType()).isEqualTo(ConnectorType.OCI_HELM_REPO);
    assertThat(entity.getAuthType()).isEqualTo(OciHelmAuthType.USER_PASSWORD);
    assertThat(entity.getOciHelmAuthentication()).isNotNull();
    OciHelmUsernamePasswordAuthentication authentication =
        (OciHelmUsernamePasswordAuthentication) entity.getOciHelmAuthentication();
    assertThat(authentication.getPasswordRef()).isEqualTo(passwordSecretRef.toSecretRefStringValue());
    assertThat(authentication.getUsernameRef()).isNull();
    assertThat(authentication.getUsername()).isEqualTo("test");
  }
}
