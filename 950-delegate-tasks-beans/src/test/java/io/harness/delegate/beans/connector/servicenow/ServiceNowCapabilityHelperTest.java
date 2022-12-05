/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.servicenow;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.common.EncryptionType;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceNowCapabilityHelperTest extends CategoryTest {
  private static final String SERVICENOW_URL = "https://harness.service-now.com";
  @Inject ServiceNowCapabilityHelper serviceNowCapabilityHelper;

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category(UnitTests.class)
  public void generateDelegateCapabilities() {
    ServiceNowConnectorDTO serviceNowConnectorDTO =
        ServiceNowConnectorDTO.builder().serviceNowUrl(SERVICENOW_URL).build();
    ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(
        EncryptedDataDetail.builder()
            .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.LOCAL).build())
            .build());
    List<ExecutionCapability> executionCapabilities = serviceNowCapabilityHelper.generateDelegateCapabilities(
        serviceNowConnectorDTO, encryptedDataDetails, expressionEvaluator);
    assertThat(executionCapabilities).hasSize(1);

    assertThat(executionCapabilities.get(0)).isInstanceOf(HttpConnectionExecutionCapability.class);
    assertThat(((HttpConnectionExecutionCapability) executionCapabilities.get(0)).getHost())
        .isEqualTo("harness.service-now.com");
  }
}
