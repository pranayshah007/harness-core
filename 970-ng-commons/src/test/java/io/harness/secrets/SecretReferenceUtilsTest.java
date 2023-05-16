/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.rule.OwnerRule.NISHANT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.BaseNGAccess;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SecretReferenceUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetAllSecretFQNs() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build();
    SecretRefData accountSecret = new SecretRefData("account.accountSecret");
    SecretRefData orgSecret = new SecretRefData("org.orgSecret");
    SecretRefData projectSecret = new SecretRefData("projectSecret");
    List<String> fqns = SecretReferenceUtils.getAllSecretFQNs(
        Map.ofEntries(Map.entry("accountSecret", accountSecret), Map.entry("orgSecret", orgSecret),
            Map.entry("projectSecret", projectSecret)),
        baseNGAccess);
    String projectSecretFqn = accountIdentifier + "/" + orgIdentifier + "/" + projectIdentifier + "/projectSecret";
    String orgSecretFqn = accountIdentifier + "/" + orgIdentifier + "/orgSecret";
    String accountSecretFqn = accountIdentifier + "/accountSecret";
    assertThat(fqns).isNotEmpty().hasSize(3).containsExactlyInAnyOrder(
        accountSecretFqn, orgSecretFqn, projectSecretFqn);
  }
}
