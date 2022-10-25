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
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.generator.AccountGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.testframework.graphql.QLTestObject;

import software.wings.beans.Account;
import software.wings.graphql.schema.type.secrets.QLEncryptedText;
import software.wings.graphql.schema.type.secrets.QLSecret;

import com.google.inject.Inject;
import graphql.ExecutionResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class UpdateEncryptedTextTest extends GraphQLTest {
  @Inject private OwnerManager ownerManager;
  @Inject private AccountGenerator accountGenerator;
  @Inject private EncryptedTextHelper encryptedTextHelper;
  private String accountId;
  private String updatedSecretName = "UpdatedSecretName";
  private String secretId;
  private String updatedSecret = "updatedSecret";

  @Before
  public void setup() {
    final Randomizer.Seed seed = new Randomizer.Seed(0);
    final OwnerManager.Owners owners = ownerManager.create();
    Account account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    accountId = account.getUuid();
    secretId = encryptedTextHelper.CreateEncryptedText("secretName");
  }

  private String getUpdateEncryptedTextInput() {
    String encryptedText = $GQL(/*{
     secretType: ENCRYPTED_TEXT,
     secretId : "%s",
     encryptedText: {
         name: "%s",
         value: "secret",
      }
     }*/ secretId, updatedSecretName);
    return encryptedText;
  }

  @Data
  public static class CreateSecretResult {
    String clientMutationId;
    QLSecret secret;
  }

  @Data
  public static class UpdateSecretResult {
    String clientMutationId;
    QLEncryptedText secret;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testUpdatingEncryptedText() {
    String query = $GQL(/*
    mutation{
        updateSecret(input:%s){
            secret{
                ... on EncryptedText{
                name
                id
            }
        }
        }
    }
*/ getUpdateEncryptedTextInput());
    final QLTestObject qlTestObject = qlExecute(query, accountId);
    final UpdateSecretResult result = JsonUtils.convertValue(qlTestObject.getMap(), UpdateSecretResult.class);
    QLSecret encryptedText = result.getSecret();
    assertThat(encryptedText.getId()).isNotNull();
    assertThat(encryptedText.getName()).isEqualTo(updatedSecretName);
  }

  private String getUpdateEncryptedTextInputWithInvalidId() {
    String encryptedText = $GQL(/*{
     secretType: ENCRYPTED_TEXT,
     secretId : "%s",
     encryptedText: {
         name: "%s",
         value: "%s",
      }
     }*/ "secretId", updatedSecretName, updatedSecret);
    return encryptedText;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testUpdatingEncryptedTextWithInvalidId() {
    String query = $GQL(/*
    mutation{
        updateSecret(input:%s){
            secret{
                ... on EncryptedText{
                name
                id
            }
        }
        }
    }
*/ getUpdateEncryptedTextInputWithInvalidId());
    final ExecutionResult result = qlResult(query, accountId);
    assertThat(result.getErrors().size()).isEqualTo(1);

    assertThat(result.getErrors().get(0).getMessage())
        .isEqualTo(
            "Exception while fetching data (/updateSecret) : Invalid request: No encrypted text exists with the id secretId");
  }
}
