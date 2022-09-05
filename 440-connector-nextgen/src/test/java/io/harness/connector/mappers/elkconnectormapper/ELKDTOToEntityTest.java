/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.elkconnectormapper;

import static io.harness.encryption.Scope.ACCOUNT;
import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.elkconnector.ELKConnector;
import io.harness.connector.mappers.elkmapper.ELKDTOToEntity;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class ELKDTOToEntityTest extends CategoryTest {
  @InjectMocks ELKDTOToEntity elkdtoToEntity;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testToELKConnector() {
    String username = "username";
    String secretIdentifier = "secretIdentifier";
    String url = "url";
    String accountId = "accountId";

    SecretRefData secretRefData = SecretRefData.builder().identifier(secretIdentifier).scope(ACCOUNT).build();
    ELKConnectorDTO elkConnectorDTO =
        ELKConnectorDTO.builder().username(username).passwordRef(secretRefData).url(url).build();

    ELKConnector elksConnector = elkdtoToEntity.toConnectorEntity(elkConnectorDTO);
    assertThat(elksConnector).isNotNull();
    assertThat(elksConnector.getUsername()).isEqualTo(elkConnectorDTO.getUsername());
    assertThat(elksConnector.getPasswordRef()).isNotNull();
    assertThat(elksConnector.getPasswordRef())
        .isEqualTo(ACCOUNT.getYamlRepresentation() + "." + elkConnectorDTO.getPasswordRef().getIdentifier());
    assertThat(elksConnector.getUrl()).isEqualTo(elkConnectorDTO.getUrl());
  }
}
