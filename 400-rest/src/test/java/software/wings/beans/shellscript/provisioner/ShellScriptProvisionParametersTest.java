/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.shellscript.provisioner;

import static io.harness.rule.OwnerRule.ANSHUL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.common.EncryptionType;

import software.wings.WingsBaseTest;
import software.wings.beans.KmsConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ShellScriptProvisionParametersTest extends WingsBaseTest {
  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilities() {
    Map<String, EncryptedDataDetail> encryptedVariables = new HashMap<>();

    encryptedVariables.put("abc",
        EncryptedDataDetail.builder()
            .encryptedData(EncryptedRecordData.builder().encryptionType(EncryptionType.KMS).build())
            .encryptionConfig(KmsConfig.builder()
                                  .accessKey("accessKey")
                                  .region("us-east-1")
                                  .secretKey("secretKey")
                                  .kmsArn("kmsArn")
                                  .build())
            .build());

    ShellScriptProvisionParameters shellScriptProvisionParameters =
        ShellScriptProvisionParameters.builder()
            .encryptedVariables(encryptedVariables)
            .delegateSelectors(Collections.singletonList("primary"))
            .build();

    List<ExecutionCapability> executionCapabilities =
        shellScriptProvisionParameters.fetchRequiredExecutionCapabilities(null);
    assertThat(executionCapabilities).isNotEmpty();
    assertThat(((HttpConnectionExecutionCapability) executionCapabilities.get(0)).getHost())
        .isEqualTo("kms.us-east-1.amazonaws.com");
    assertThat(((SelectorCapability) executionCapabilities.get(1)).getSelectors())
        .isEqualTo(Collections.singleton("primary"));
  }
}
