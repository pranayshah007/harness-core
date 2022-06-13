/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.helm;

import static io.harness.encryption.Scope.ACCOUNT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.helm.OciHelmConnector;
import io.harness.connector.entities.embedded.helm.OciHelmUsernamePasswordAuthentication;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class OciHelmEntityToDTOTest extends CategoryTest {
  private OciHelmEntityToDTO mapper;

  @Before
  public void setUp() {
    mapper = new OciHelmEntityToDTO();
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testUsernameAndPasswordAuthEntityToDto() {
    String usernameSecretRef = ACCOUNT.getYamlRepresentation() + ".usernameRef";
    String passwordSecretRef = ACCOUNT.getYamlRepresentation() + ".passwordRef";
    OciHelmConnector entity = OciHelmConnector.builder()
                                  .url("localhost")
                                  .authType(OciHelmAuthType.USER_PASSWORD)
                                  .ociHelmAuthentication(OciHelmUsernamePasswordAuthentication.builder()
                                                             .usernameRef(usernameSecretRef)
                                                             .passwordRef(passwordSecretRef)
                                                             .build())
                                  .build();

    OciHelmConnectorDTO dto = mapper.createConnectorDTO(entity);
    assertThat(dto).isNotNull();
    assertThat(dto.getHelmRepoUrl()).isEqualTo("localhost");
    assertThat(dto.getAuth().getAuthType()).isEqualTo(OciHelmAuthType.USER_PASSWORD);
    assertThat(dto.getAuth().getCredentials()).isNotNull();
    OciHelmUsernamePasswordDTO usernamePasswordDTO = (OciHelmUsernamePasswordDTO) dto.getAuth().getCredentials();
    assertThat(usernamePasswordDTO.getUsername()).isNull();
    assertThat(usernamePasswordDTO.getUsernameRef()).isEqualTo(SecretRefHelper.createSecretRef(usernameSecretRef));
    assertThat(usernamePasswordDTO.getPasswordRef()).isEqualTo(SecretRefHelper.createSecretRef(passwordSecretRef));
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testUsernameAndPasswordOnlySecretAuthEntityToDto() {
    String passwordSecretRef = ACCOUNT.getYamlRepresentation() + ".passwordRef";
    OciHelmConnector entity =
        OciHelmConnector.builder()
            .url("localhost")
            .authType(OciHelmAuthType.USER_PASSWORD)
            .ociHelmAuthentication(
                OciHelmUsernamePasswordAuthentication.builder().username("test").passwordRef(passwordSecretRef).build())
            .build();

    OciHelmConnectorDTO dto = mapper.createConnectorDTO(entity);
    assertThat(dto).isNotNull();
    assertThat(dto.getHelmRepoUrl()).isEqualTo("localhost");
    assertThat(dto.getAuth().getAuthType()).isEqualTo(OciHelmAuthType.USER_PASSWORD);
    assertThat(dto.getAuth().getCredentials()).isNotNull();
    OciHelmUsernamePasswordDTO usernamePasswordDTO = (OciHelmUsernamePasswordDTO) dto.getAuth().getCredentials();
    assertThat(usernamePasswordDTO.getUsername()).isEqualTo("test");
    assertThat(usernamePasswordDTO.getUsernameRef().getIdentifier()).isNull();
    assertThat(usernamePasswordDTO.getUsernameRef().getScope()).isNull();
    assertThat(usernamePasswordDTO.getPasswordRef()).isEqualTo(SecretRefHelper.createSecretRef(passwordSecretRef));
  }
}
