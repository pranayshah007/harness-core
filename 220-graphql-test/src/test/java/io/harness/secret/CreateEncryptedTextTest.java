/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secret;

import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.GraphQLTest;
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.generator.AccountGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.testframework.graphql.QLTestObject;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.graphql.schema.type.secrets.QLAppEnvScope;
import software.wings.graphql.schema.type.secrets.QLEncryptedText;
import software.wings.graphql.schema.type.secrets.QLUsageScope;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class CreateEncryptedTextTest extends GraphQLTest {
  @Inject private OwnerManager ownerManager;
  @Inject private AccountGenerator accountGenerator;
  private String accountId;
  private String secretName = "secretName";
  @Inject ApplicationGenerator applicationGenerator;
  @Inject EnvironmentGenerator environmentGenerator;
  @Inject Application application;
  @Inject Environment environment;

  @Before
  public void setup() {
    final Randomizer.Seed seed = new Randomizer.Seed(0);
    final OwnerManager.Owners owners = ownerManager.create();
    Account account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    accountId = account.getUuid();

    application = applicationGenerator.ensureApplication(
        seed, owners, anApplication().accountId(accountId).name("Application - " + generateUuid()).build());

    final Environment.Builder builder = anEnvironment().environmentType(PROD).appId(application.getUuid());
    environment =
        environmentGenerator.ensureEnvironment(seed, owners, builder.uuid(generateUuid()).name("prod").build());
  }

  private String getCreateEncryptedTextInput() {
    String encryptedText = $GQL(/*{
         secretType: ENCRYPTED_TEXT,
         encryptedText: {
             name: "%s",
             value: "secret",
             secretManagerId: "kmpySmUISimoRrJL6NL73w"
          }
 }*/ secretName);
    return encryptedText;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testCreatingEncryptedText() {
    String query = $GQL(/*
    mutation{
       createSecret(input:%s){
           secret{
               ... on EncryptedText{
                 name
                 secretManagerId
                 id
               }
          }
       }
     }
   */ getCreateEncryptedTextInput());
    final QLTestObject qlTestObject = qlExecute(query, accountId);
    final EncryptedTextHelper.CreateEncryptedTextResult result =
        JsonUtils.convertValue(qlTestObject.getMap(), EncryptedTextHelper.CreateEncryptedTextResult.class);
    QLEncryptedText encryptedText = result.getSecret();
    assertThat(encryptedText.getId()).isNotNull();
    assertThat(encryptedText.getName()).isEqualTo(secretName);
  }

  private String getCreateEncryptedTextInputWithIdAppScope() {
    String encryptedText = $GQL(/*
   {
   secretType: ENCRYPTED_TEXT,
   encryptedText: {
       name: "%s",
       value: "secret",
       secretManagerId: "kmpySmUISimoRrJL6NL73w",
       usageScope : {
           appEnvScopes: [{
              application: {
              appId: "%s"
             },
               environment: {
                 envId: "%s"
               }
            }
         ]
       }
    }
}*/ "secretName1", application.getUuid(), environment.getUuid());
    return encryptedText;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testCreatingEncryptedTextWithIdScope() {
    String query = $GQL(/*
mutation{
 createSecret(input:%s){
     secret{
         ... on EncryptedText{
           name
           secretManagerId
           id
           usageScope {
            appEnvScopes {
                application {
                    filterType
                    appId
                }
                environment {
                    filterType
                    envId
                }
            }
           }
         }
    }
 }
}
*/ getCreateEncryptedTextInputWithIdAppScope());
    final QLTestObject qlTestObject = qlExecute(query, accountId);
    final EncryptedTextHelper.CreateEncryptedTextResult result =
        JsonUtils.convertValue(qlTestObject.getMap(), EncryptedTextHelper.CreateEncryptedTextResult.class);
    QLEncryptedText encryptedText = result.getSecret();
    assertThat(encryptedText.getId()).isNotNull();
    assertThat(encryptedText.getName()).isEqualTo("secretName1");
    assertThat(encryptedText.getUsageScope()).isNotNull();
    QLUsageScope usageScope = encryptedText.getUsageScope();
    assertThat(usageScope.getAppEnvScopes()).isNotEmpty();
    assertThat(usageScope.getAppEnvScopes().size()).isEqualTo(1);
    List<QLAppEnvScope> usageList = new ArrayList<>();
    usageList.addAll(usageScope.getAppEnvScopes());
    QLAppEnvScope firstEntry = usageList.get(0);
    assertThat(firstEntry.getApplication().getAppId()).isEqualTo(application.getUuid());
    assertThat(firstEntry.getApplication().getFilterType()).isNull();
    assertThat(firstEntry.getEnvironment().getEnvId()).isEqualTo(environment.getUuid());
    assertThat(firstEntry.getEnvironment().getFilterType()).isNull();
  }
}
