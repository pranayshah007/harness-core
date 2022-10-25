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
import software.wings.graphql.schema.type.secrets.QLAppEnvScope;
import software.wings.graphql.schema.type.secrets.QLUsageScope;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class SecretUsageScopeTest extends GraphQLTest {
  @Inject private OwnerManager ownerManager;
  @Inject private AccountGenerator accountGenerator;
  private String accountId;
  private String secretName = "secretName";
  private Account account;
  private String secretId;
  @Inject WinRMCredentialHelper winRMCredentialHelper;

  @Before
  public void setup() {
    final Randomizer.Seed seed = new Randomizer.Seed(0);
    final OwnerManager.Owners owners = ownerManager.create();
    account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    accountId = account.getUuid();
    secretId = winRMCredentialHelper.createWinRMCredential("secretName");
  }

  private String getUpdateWinRMCredentialWithScope() {
    String input = $GQL(/*
{
secretType: WINRM_CREDENTIAL,
secretId: "%s",
winRMCredential: {
    authenticationScheme:  NTLM,
    useSSL: false,
    skipCertCheck: false,
    port: 222
    usageScope : {
         appEnvScopes: [{
            application: {
            filterType: ALL
           },
             environment: {
               filterType: PRODUCTION_ENVIRONMENTS
             }
          }
       ]
    }
  }
}
*/ secretId);
    return input;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testUpdatingWinRMCredentialWithScope() {
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
        usageScope  {
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
*/ getUpdateWinRMCredentialWithScope());
    final QLTestObject qlTestObject = qlExecute(query, accountId);
    final WinRMCredentialHelper.UpdateWinRMResult result =
        JsonUtils.convertValue(qlTestObject.getMap(), WinRMCredentialHelper.UpdateWinRMResult.class);
    assertThat(result.getSecret()).isNotNull();
    assertThat(result.getSecret().getId()).isNotNull();
    assertThat(result.getSecret().isUseSSL()).isEqualTo(false);
    assertThat(result.getSecret().isSkipCertCheck()).isEqualTo(false);

    // Test Cases for Scope
    WinRMCredentialHelper.WinRMSecret winRMCredential = result.getSecret();
    assertThat(winRMCredential.getUsageScope()).isNotNull();
    QLUsageScope usageScope = winRMCredential.getUsageScope();
    assertThat(usageScope.getAppEnvScopes()).isNotEmpty();
    assertThat(usageScope.getAppEnvScopes().size()).isEqualTo(1);
    List<QLAppEnvScope> usageList = new ArrayList<>();
    usageList.addAll(usageScope.getAppEnvScopes());
    QLAppEnvScope firstEntry = usageList.get(0);
    assertThat(firstEntry.getApplication().getAppId()).isNull();
    assertThat(firstEntry.getApplication().getFilterType().toString()).isEqualTo("ALL");
    assertThat(firstEntry.getEnvironment().getEnvId()).isNull();
    assertThat(firstEntry.getEnvironment().getFilterType().toString()).isEqualTo("PRODUCTION_ENVIRONMENTS");
  }
}
