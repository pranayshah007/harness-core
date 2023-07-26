/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.splunkconnector.SplunkAuthType;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.delegate.beans.cvng.splunk.SplunkUtils;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SplunkUtilsTest extends CvNextGenTestBase {
  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void checkSplunkConnectorHeader() {
    SplunkConnectorDTO splunkConnectorUserPasswordDTO =
        SplunkConnectorDTO.builder()
            .splunkUrl("https://splunk.dev.harness.io:8089/")
            .accountId(generateUuid())
            .username("harnessadmin")
            .passwordRef(SecretRefData.builder().decryptedValue("Harness@123".toCharArray()).build())
            .build();
    SplunkConnectorDTO splunkConnectorBearerTokenDTO =
        SplunkConnectorDTO.builder()
            .splunkUrl("https://splunk.dev.harness.io:8089/")
            .accountId(generateUuid())
            .authType(SplunkAuthType.BEARER_TOKEN)
            .tokenRef(SecretRefData.builder().decryptedValue("Harness@246".toCharArray()).build())
            .build();
    assertThat(SplunkUtils.getAuthorizationHeader(splunkConnectorUserPasswordDTO))
        .isEqualTo("Basic aGFybmVzc2FkbWluOkhhcm5lc3NAMTIz");
    assertThat(SplunkUtils.getAuthorizationHeader(splunkConnectorBearerTokenDTO)).isEqualTo("Bearer Harness@246");
  }
}
