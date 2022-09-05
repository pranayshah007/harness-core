/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.MITISHA;
import static io.harness.rule.OwnerRule.VOJIN;

import static com.google.common.collect.Sets.newHashSet;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.SecretManagerConfig;
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.generator.AccountGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.TemplateGenerator;
import io.harness.multiline.MultilineStringMixin;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.JsonUtils;
import io.harness.testframework.graphql.QLTestObject;

import software.wings.beans.Account;
import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.beans.KmsConfig;
import software.wings.beans.VaultConfig;
import software.wings.beans.template.Template;
import software.wings.graphql.schema.type.QLEnvFilterType;
import software.wings.graphql.schema.type.QLGenericFilterType;
import software.wings.graphql.schema.type.secretManagers.QLSecretManager.QLSecretManagerKeys;
import software.wings.graphql.schema.type.secretManagers.QLSecretManagerType;
import software.wings.graphql.schema.type.secrets.QLAppEnvScope;
import software.wings.graphql.schema.type.secrets.QLUsageScope;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.UsageRestrictions;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SecretManagerTest extends GraphQLTest {
  @Inject private AccountGenerator accountGenerator;
  @Inject SecretManagerConfigService secretManagerConfigService;
  @Inject private TemplateGenerator templateGenerator;
  @Inject private OwnerManager ownerManager;
  Account account;
  SecretManagerConfig secretManagerConfig;
  CustomSecretsManagerConfig customSecretsManagerConfig;
  String SECRET_MANAGER_NAME = "test secret manager";
  String CUSTOM_SECRET_MANAGER_NAME = "custom test secret manager";
  Randomizer.Seed seed;
  OwnerManager.Owners owners;

  @Before
  public void setUp() {
    seed = new Randomizer.Seed(0);
    owners = ownerManager.create();
    account = accountGenerator.ensurePredefined(seed, owners, AccountGenerator.Accounts.GENERIC_TEST);
    secretManagerConfig = AwsSecretsManagerConfig.builder().name(SECRET_MANAGER_NAME).build();
    secretManagerConfig.setAccountId(account.getUuid());
    secretManagerConfig.setUsageRestrictions(createUsageRestrictions());
    secretManagerConfigService.save(secretManagerConfig);
    customSetup();
  }

  public void customSetup() {
    Template shellScriptTemplate =
        templateGenerator.ensurePredefined(seed, owners, TemplateGenerator.Templates.SHELL_SCRIPT);
    Set<String> delegateSelectors = new HashSet<>();
    delegateSelectors.add("delegate1");
    Set<EncryptedDataParams> testVariables = new HashSet<>();
    testVariables.add(EncryptedDataParams.builder().name("testVariable1").value("testValue1").build());
    customSecretsManagerConfig = CustomSecretsManagerConfig.builder()
                                     .name(CUSTOM_SECRET_MANAGER_NAME)
                                     .templateId(shellScriptTemplate.getUuid())
                                     .executeOnDelegate(true)
                                     .isConnectorTemplatized(true)
                                     .host("host")
                                     .commandPath("/tmp")
                                     .connectorId("ConnectorId")
                                     .isDefault(false)
                                     .delegateSelectors(delegateSelectors)
                                     .testVariables(testVariables)
                                     .build();
    customSecretsManagerConfig.setAccountId(account.getUuid());
    customSecretsManagerConfig.setUsageRestrictions(createUsageRestrictions());
    secretManagerConfigService.save(customSecretsManagerConfig);
  }

  @Test
  @Owner(developers = MITISHA)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testCustomSecretManagerQuery() {
    String secretManagerQueryPattern = MultilineStringMixin.$.GQL(/*
{
secretManager(secretManagerId: "%s"){
id
name
config{
... on CustomSecretManagerConfig {
templateId
executeOnDelegate
isConnectorTemplatized
host
commandPath
connectorId
isDefault
delegateSelectors
testVariables {
  name
  value
  }
}
}
}
}
*/ SecretManagerTest.class);
    String query = String.format(secretManagerQueryPattern, customSecretsManagerConfig.getUuid());
    final QLTestObject qlSecretManagerObject = qlExecute(query, account.getUuid());
    assertThat(qlSecretManagerObject.get(QLSecretManagerKeys.id)).isEqualTo(customSecretsManagerConfig.getUuid());
    assertThat(qlSecretManagerObject.get(QLSecretManagerKeys.name)).isEqualTo(customSecretsManagerConfig.getName());
    LinkedHashMap testConfig = (LinkedHashMap) qlSecretManagerObject.get("config");
    assertThat(testConfig.get("host")).isEqualTo(customSecretsManagerConfig.getHost());
    assertThat(testConfig.get("templateId")).isEqualTo(customSecretsManagerConfig.getTemplateId());
    assertThat(testConfig.get("isDefault")).isEqualTo(customSecretsManagerConfig.isDefault());
    ArrayList<String> delegateSelectors = (ArrayList<String>) testConfig.get("delegateSelectors");
    assertThat(delegateSelectors.contains("delegate1"));
    assertThat(testConfig.get("connectorId")).isEqualTo(customSecretsManagerConfig.getConnectorId());
    assertThat(testConfig.get("commandPath")).isEqualTo(customSecretsManagerConfig.getCommandPath());
    assertThat(testConfig.get("isConnectorTemplatized")).isEqualTo(customSecretsManagerConfig.isConnectorTemplatized());
    assertThat(testConfig.get("executeOnDelegate")).isEqualTo(customSecretsManagerConfig.isExecuteOnDelegate());
    ArrayList<LinkedHashMap> testVariables = (ArrayList<LinkedHashMap>) testConfig.get("testVariables");
    LinkedHashMap newObj = testVariables.get(0);
    assertThat(newObj.get("name")).isEqualTo("testVariable1");
    assertThat(newObj.get("value")).isEqualTo("testValue1");
  }

  @Test
  @Owner(developers = MITISHA)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testCustomSecretManagerListQuery() {
    String query = MultilineStringMixin.$.GQL(/*
{
secretManagers(limit:10){
nodes{
  id
  name
  config{
... on CustomSecretManagerConfig {
templateId
executeOnDelegate
isConnectorTemplatized
host
commandPath
connectorId
isDefault
delegateSelectors
testVariables {
  name
  value
  }
}
}
}
}
}
*/ SecretManagerTest.class);
    addAzureAndAwsKmsSecretManagers();
    saveAwsSecretManagerWithDifferentAccount();
    final QLTestObject qlSecretManagerObject = qlExecute(query, account.getUuid());
    List<LinkedHashMap> secretManagerNodes = (List<LinkedHashMap>) qlSecretManagerObject.get("nodes");
    assertThat(secretManagerNodes.size()).isEqualTo(4);
    LinkedHashMap testConfig = new LinkedHashMap();
    for (LinkedHashMap obj : secretManagerNodes) {
      if (obj.get("config") != null) {
        testConfig = (LinkedHashMap) obj.get("config");
      }
    }
    assertThat(testConfig.get("host")).isEqualTo(customSecretsManagerConfig.getHost());
    assertThat(testConfig.get("templateId")).isEqualTo(customSecretsManagerConfig.getTemplateId());
    assertThat(testConfig.get("isDefault")).isEqualTo(customSecretsManagerConfig.isDefault());
    ArrayList<String> delegateSelectors = (ArrayList<String>) testConfig.get("delegateSelectors");
    assertThat(delegateSelectors.contains("delegate1"));
    assertThat(testConfig.get("connectorId")).isEqualTo(customSecretsManagerConfig.getConnectorId());
    assertThat(testConfig.get("commandPath")).isEqualTo(customSecretsManagerConfig.getCommandPath());
    assertThat(testConfig.get("isConnectorTemplatized")).isEqualTo(customSecretsManagerConfig.isConnectorTemplatized());
    assertThat(testConfig.get("executeOnDelegate")).isEqualTo(customSecretsManagerConfig.isExecuteOnDelegate());
    ArrayList<LinkedHashMap> testVariables = (ArrayList<LinkedHashMap>) testConfig.get("testVariables");
    LinkedHashMap newObj = testVariables.get(0);
    assertThat(newObj.get("name")).isEqualTo("testVariable1");
    assertThat(newObj.get("value")).isEqualTo("testValue1");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testSecretManagerQuery() {
    String secretManagerQueryPattern = MultilineStringMixin.$.GQL(/*
{
secretManager(secretManagerId: "%s"){
id
name
}
}
*/ SecretManagerTest.class);
    String query = String.format(secretManagerQueryPattern, secretManagerConfig.getUuid());
    final QLTestObject qlSecretManagerObject = qlExecute(query, account.getUuid());
    assertThat(qlSecretManagerObject.get(QLSecretManagerKeys.id)).isEqualTo(secretManagerConfig.getUuid());
    assertThat(qlSecretManagerObject.get(QLSecretManagerKeys.name)).isEqualTo(secretManagerConfig.getName());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testSecretManagerGetByNameQuery() {
    String secretManagerQueryPattern = MultilineStringMixin.$.GQL(/*
{
secretManagerByName(name: "%s"){
id
name
}
}
*/ SecretManagerTest.class);
    String query = String.format(secretManagerQueryPattern, SECRET_MANAGER_NAME);
    final QLTestObject qlSecretManagerObject = qlExecute(query, account.getUuid());
    assertThat(qlSecretManagerObject.get(QLSecretManagerKeys.id)).isEqualTo(secretManagerConfig.getUuid());
    assertThat(qlSecretManagerObject.get(QLSecretManagerKeys.name)).isEqualTo(secretManagerConfig.getName());
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testSecretManagerListQuery() {
    String query = MultilineStringMixin.$.GQL(/*
{
secretManagers(limit:10){
nodes{
  id
  name
}
}
}
*/ SecretManagerTest.class);
    addAzureAndAwsKmsSecretManagers();
    saveAwsSecretManagerWithDifferentAccount();
    final QLTestObject qlSecretManagerObject = qlExecute(query, account.getUuid());
    List secretManagerNodes = (List) qlSecretManagerObject.get("nodes");
    assertThat(secretManagerNodes.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category({GraphQLTests.class, UnitTests.class})
  public void securityManagerTypesMustBeEqualInAPIandDB() {
    // LOCAL will differ in both the secret managers.
    assertThat(EncryptionType.values().length).isEqualTo(QLSecretManagerType.values().length + 2);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testSecretManagerQuery_usageScope() {
    String secretManagerQueryPattern = MultilineStringMixin.$.GQL(/*
{
secretManager(secretManagerId: "%s"){
usageScope{
  appEnvScopes{
    application {
      appId
      filterType
    }
    environment {
      envId
      filterType
    }
  }
}
}
}
*/ SecretManagerTest.class);
    String query = String.format(secretManagerQueryPattern, secretManagerConfig.getUuid());
    final QLTestObject qlSecretManagerObject = qlExecute(query, account.getUuid());

    QLUsageScope usageScope =
        JsonUtils.convertValue(qlSecretManagerObject.get(QLSecretManagerKeys.usageScope), QLUsageScope.class);
    assertThat(usageScope).isNotNull();

    Set<QLAppEnvScope> appEnvScopes = usageScope.getAppEnvScopes();
    assertThat(appEnvScopes.size()).isEqualTo(1);

    QLAppEnvScope appEnvScope = Iterables.getOnlyElement(appEnvScopes);
    assertThat(appEnvScope.getApplication().getFilterType()).isEqualTo(QLGenericFilterType.ALL);
    assertThat(appEnvScope.getEnvironment().getFilterType()).isEqualTo(QLEnvFilterType.PRODUCTION_ENVIRONMENTS);
  }

  private void addAzureAndAwsKmsSecretManagers() {
    SecretManagerConfig awsKms = KmsConfig.builder().name("test aws kms").build();
    awsKms.setAccountId(account.getUuid());
    secretManagerConfigService.save(awsKms);

    SecretManagerConfig azureSecret = VaultConfig.builder().name("test secret vault").build();
    azureSecret.setAccountId(account.getUuid());
    secretManagerConfigService.save(azureSecret);
  }

  private void saveAwsSecretManagerWithDifferentAccount() {
    SecretManagerConfig awsKmsDifferentAccount =
        AwsSecretsManagerConfig.builder().name("test aws kms diff account").build();
    awsKmsDifferentAccount.setAccountId("random");
    secretManagerConfigService.save(awsKmsDifferentAccount);
  }

  private UsageRestrictions createUsageRestrictions() {
    GenericEntityFilter appFilter =
        GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build();
    EnvFilter envFilter = EnvFilter.builder().filterTypes(newHashSet(EnvFilter.FilterType.PROD)).build();
    UsageRestrictions.AppEnvRestriction appEnvRestriction =
        UsageRestrictions.AppEnvRestriction.builder().appFilter(appFilter).envFilter(envFilter).build();
    return UsageRestrictions.builder().appEnvRestrictions(newHashSet(appEnvRestriction)).build();
  }
}
