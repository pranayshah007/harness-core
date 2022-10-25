/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secret;

import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.GraphQLTest;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.generator.AccountGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.testframework.graphql.QLTestObject;

import software.wings.beans.Account;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UpdateWinRMCredentialsTest extends GraphQLTest {
  @Inject private AccountGenerator accountGenerator;
  @Inject private OwnerManager ownerManager;
  @Inject private WinRMCredentialHelper winRMCredentialHelper;
  @Inject private SecretManager secretManager;

  private String updatedUserName = "updatedUserName";
  private String updatedName = "updatedName";
  private int updatedPort = 222;
  private String accountId;
  private String secretId;

  @Before
  public void setup() {
    final OwnerManager.Owners owners = ownerManager.create();
    final Randomizer.Seed seed = new Randomizer.Seed(0);
    Account account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    accountId = account.getUuid();
    secretId = winRMCredentialHelper.createWinRMCredential("secretName");
  }

  private String getUpdateWinRMCredentialNameInput() {
    String passwordSecretId = secretManager.saveSecretText(
        accountId, SecretText.builder().name("winrmPasswordSecretId").value("abc").build(), false);
    String input = $GQL(/*
    {
    secretType: WINRM_CREDENTIAL,
    secretId: "%s",
    winRMCredential: {
        name: "%s",
        userName: "%s",
        authenticationScheme:  NTLM,
        passwordSecretId: "%s"
        useSSL: false,
        skipCertCheck: false,
        port: %d
     }
    }
    */ secretId, updatedName, updatedUserName, passwordSecretId, updatedPort);
    return input;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testUpdatingWinRMCredentialName() {
    String query = $GQL(/*
    mutation{
        updateSecret(input:%s){
                  clientMutationId
            secret{
            ... on WinRMCredential{
            name
            id
            port
            skipCertCheck
            useSSL
            userName
            authenticationScheme
            }
        }
     }
    }
    */ getUpdateWinRMCredentialNameInput());
    final QLTestObject qlTestObject = qlExecute(query, accountId);
    final WinRMCredentialHelper.UpdateWinRMResult result =
        JsonUtils.convertValue(qlTestObject.getMap(), WinRMCredentialHelper.UpdateWinRMResult.class);
    assertThat(result.getSecret()).isNotNull();
    assertThat(result.getSecret().getId()).isNotNull();
    assertThat(result.getSecret().getName()).isEqualTo(updatedName);
    assertThat(result.getSecret().getPort()).isEqualTo(updatedPort);
    assertThat(result.getSecret().getUserName()).isEqualTo(updatedUserName);
    assertThat(result.getSecret().isUseSSL()).isEqualTo(false);
    assertThat(result.getSecret().isSkipCertCheck()).isEqualTo(false);
  }
}
