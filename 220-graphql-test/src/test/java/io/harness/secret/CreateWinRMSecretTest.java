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
import software.wings.graphql.schema.type.secrets.QLWinRMCredential;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import lombok.Data;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CreateWinRMSecretTest extends GraphQLTest {
  @Inject private AccountGenerator accountGenerator;
  @Inject private OwnerManager ownerManager;
  @Inject private SecretManager secretManager;
  private String secretName = "tests";
  private String userName = "harnessadmin";
  private String password = "H@rnessH@rness";
  private int port = 5986;
  private String accountId;
  private String secretId;

  @Before
  public void setup() {
    final OwnerManager.Owners owners = ownerManager.create();
    final Randomizer.Seed seed = new Randomizer.Seed(0);
    Account account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    accountId = account.getUuid();
    SecretText secretText = SecretText.builder().name("sshPasswordSecretId").value("abc").build();
    secretId = secretManager.saveSecretText(accountId, secretText, false);
  }

  private String getCreateWinRMCredentialInput() {
    String input = $GQL(/*
     {
        secretType: WINRM_CREDENTIAL,
        winRMCredential: {
          name: "tests",
          userName: "%s",
          authenticationScheme:  NTLM,
          passwordSecretId: "%s"
          useSSL: true,
          skipCertCheck: true,
          port: %d
       }
     }
    */ userName, secretId, port);
    return input;
  }

  @Data
  public static class CreateWinRMSecretResult {
    String clientMutationId;
    QLWinRMCredential secret;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testCreatingWinRMCredential() {
    String query = $GQL(/*
    mutation{
        createSecret(input:%s){
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
   */ getCreateWinRMCredentialInput());
    final QLTestObject qlTestObject = qlExecute(query, accountId);
    final CreateWinRMSecretResult result = JsonUtils.convertValue(qlTestObject.getMap(), CreateWinRMSecretResult.class);
    QLWinRMCredential winrm = result.getSecret();
    assertThat(winrm.getId()).isNotNull();
    assertThat(winrm.getName()).isEqualTo(secretName);
  }
}
