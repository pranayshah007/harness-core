/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.elkconnector.ELKAuthType;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.delegate.beans.cvng.elk.ElkUtils;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ELKUtilsTest extends CvNextGenTestBase {
  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void checkELKConnectorHeader() {
    ELKConnectorDTO elkConnectorUserPasswordDTO =
        ELKConnectorDTO.builder()
            .url("https://xwz.com/")
            .username("harnessadmin")
            .passwordRef(SecretRefData.builder().decryptedValue("Harness@123".toCharArray()).build())
            .build();
    ELKConnectorDTO elkConnectorBearerTokenDTO =
        ELKConnectorDTO.builder()
            .url("https://xwz.com/")
            .authType(ELKAuthType.BEARER_TOKEN)
            .apiKeyRef(SecretRefData.builder().decryptedValue("Harness@246".toCharArray()).build())
            .build();
    assertThat(ElkUtils.getAuthorizationHeader(elkConnectorUserPasswordDTO))
        .isEqualTo("Basic aGFybmVzc2FkbWluOkhhcm5lc3NAMTIz");
    assertThat(ElkUtils.getAuthorizationHeader(elkConnectorBearerTokenDTO)).isEqualTo("Bearer Harness@246");
  }
}
