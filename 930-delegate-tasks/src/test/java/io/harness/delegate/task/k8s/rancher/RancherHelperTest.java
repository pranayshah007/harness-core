/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.rancher;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.rancher.RancherConnectorBearerTokenAuthenticationDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigAuthCredentialsDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigAuthDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.delegate.task.k8s.RancherK8sInfraDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RancherHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRancherUrlExtraction() {
    assertThat(RancherHelper.getRancherUrl(RancherK8sInfraDelegateConfig.builder().build())).isEmpty();
    assertThat(RancherHelper.getRancherBearerToken(RancherK8sInfraDelegateConfig.builder().build())).isEmpty();

    String url = "URL";
    String token = "token";
    RancherK8sInfraDelegateConfig rancherK8sInfraDelegateConfig =
        RancherK8sInfraDelegateConfig.builder()
            .rancherConnectorDTO(
                RancherConnectorDTO.builder()
                    .config(
                        RancherConnectorConfigDTO.builder()
                            .config(RancherConnectorConfigAuthDTO.builder()
                                        .credentials(RancherConnectorConfigAuthCredentialsDTO.builder()
                                                         .auth(RancherConnectorBearerTokenAuthenticationDTO.builder()
                                                                   .passwordRef(SecretRefData.builder()
                                                                                    .decryptedValue(token.toCharArray())
                                                                                    .build())
                                                                   .build())
                                                         .build())
                                        .rancherUrl(url)
                                        .build())
                            .build())
                    .build())
            .build();

    assertThat(RancherHelper.getRancherUrl(rancherK8sInfraDelegateConfig)).isEqualTo(url);
    assertThat(RancherHelper.getRancherBearerToken(rancherK8sInfraDelegateConfig)).isEqualTo(token);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testRancherTokenNameExtraction() {
    assertThat(RancherHelper.getKubeConfigTokenName(null)).isEmpty();
    String emptyToken = "";
    String token = "tokenId:abc:def:ghi";

    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().serviceAccountTokenSupplier(() -> token).build();
    KubernetesConfig invalidKubernetesConfig =
        KubernetesConfig.builder().serviceAccountTokenSupplier(() -> emptyToken).build();

    assertThat(RancherHelper.getKubeConfigTokenName(kubernetesConfig)).isEqualTo("tokenId");
    assertThat(RancherHelper.getKubeConfigTokenName(invalidKubernetesConfig)).isEmpty();
  }
}
