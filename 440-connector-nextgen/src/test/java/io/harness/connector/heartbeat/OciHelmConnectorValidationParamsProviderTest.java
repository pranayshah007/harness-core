/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.heartbeat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
import io.harness.delegate.beans.connector.helm.OciHelmValidationParams;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class OciHelmConnectorValidationParamsProviderTest extends CategoryTest {
  @Mock private EncryptionHelper encryptionHelper;
  @InjectMocks private OciHelmConnectorValidationParamsProvider provider;
  private EncryptedDataDetail encryptedDataDetail =
      EncryptedDataDetail.builder().encryptedData(EncryptedRecordData.builder().name("test").build()).build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testShouldGetConnectorValidationParams() {
    SecretRefData usernameRef = SecretRefData.builder().identifier("username").scope(Scope.ACCOUNT).build();
    SecretRefData passwordRef = SecretRefData.builder().identifier("password").scope(Scope.ACCOUNT).build();
    OciHelmUsernamePasswordDTO credentials =
        OciHelmUsernamePasswordDTO.builder().usernameRef(usernameRef).passwordRef(passwordRef).build();
    OciHelmConnectorDTO connectorDTO = OciHelmConnectorDTO.builder()
                                           .auth(OciHelmAuthenticationDTO.builder()
                                                     .authType(OciHelmAuthType.USER_PASSWORD)
                                                     .credentials(credentials)
                                                     .build())
                                           .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(connectorDTO).build();

    doReturn(ImmutableList.of(encryptedDataDetail))
        .when(encryptionHelper)
        .getEncryptionDetail(eq(credentials), eq("acc"), eq("org"), eq("prj"));

    ConnectorValidationParams validationParams =
        provider.getConnectorValidationParams(connectorInfoDTO, "test-connector", "acc", "org", "prj");
    assertThat(validationParams).isInstanceOf(OciHelmValidationParams.class);
    OciHelmValidationParams ociHelmValidationParams = (OciHelmValidationParams) validationParams;
    assertThat(ociHelmValidationParams.getOciHelmConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(ociHelmValidationParams.getConnectorName()).isEqualTo("test-connector");
    assertThat(ociHelmValidationParams.getConnectorType()).isEqualTo(ConnectorType.OCI_HELM_REPO);
    assertThat(ociHelmValidationParams.getEncryptionDataDetails()).isNotEmpty();
    assertThat(ociHelmValidationParams.getEncryptionDataDetails().get(0)).isEqualTo(encryptedDataDetail);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testShouldGetConnectorValidationParamsAnonymous() {
    OciHelmConnectorDTO connectorDTO =
        OciHelmConnectorDTO.builder()
            .auth(OciHelmAuthenticationDTO.builder().authType(OciHelmAuthType.ANONYMOUS).build())
            .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(connectorDTO).build();

    doReturn(Collections.EMPTY_LIST)
        .when(encryptionHelper)
        .getEncryptionDetail(eq(null), eq("acc"), eq("org"), eq("prj"));

    ConnectorValidationParams validationParams =
        provider.getConnectorValidationParams(connectorInfoDTO, "test-connector", "acc", "org", "prj");
    assertThat(validationParams).isInstanceOf(OciHelmValidationParams.class);
    OciHelmValidationParams ociHelmValidationParams = (OciHelmValidationParams) validationParams;
    assertThat(ociHelmValidationParams.getOciHelmConnectorDTO()).isEqualTo(connectorDTO);
    assertThat(ociHelmValidationParams.getConnectorName()).isEqualTo("test-connector");
    assertThat(ociHelmValidationParams.getConnectorType()).isEqualTo(ConnectorType.OCI_HELM_REPO);
    assertThat(ociHelmValidationParams.getEncryptionDataDetails()).isEmpty();
  }
}
