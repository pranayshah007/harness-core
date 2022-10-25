/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.application;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.GraphQLTest;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.generator.AccountGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.SettingGenerator;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.testframework.graphql.QLTestObject;

import software.wings.beans.Application;
import software.wings.beans.SettingAttribute;
import software.wings.graphql.schema.type.connector.QLGitConnector;
import software.wings.service.intfc.HarnessTagService;

import com.google.inject.Inject;
import lombok.Data;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ApplicationMutationTest extends GraphQLTest {
  @Inject private OwnerManager ownerManager;
  @Inject private HarnessTagService harnessTagService;

  @Inject AccountGenerator accountGenerator;
  @Inject ApplicationGenerator applicationGenerator;
  @Inject private SettingGenerator settingGenerator;

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testUpdateApplicationGitConfig() {
    final Randomizer.Seed seed = new Randomizer.Seed(0);
    final OwnerManager.Owners owners = ownerManager.create();
    owners.add(EmbeddedUser.builder().uuid(generateUuid()).build());

    final Application application =
        applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST);
    final SettingAttribute gitConnector =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.TERRAFORM_CITY_GIT_REPO);
    final SettingAttribute gitConnector1 =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.TERRAFORM_MAIN_GIT_REPO);
    //      all the test are in same method, as we are testing different scenarios after an api succeeds
    {
      //        create fresh git config
      String mutation = createUpdateApplicationGitConfigQuery(
          "req123", application.getUuid(), gitConnector.getUuid(), "git-config-test", true);

      final QLTestObject qlTestObject = qlExecute(mutation, application.getAccountId());
      final QLUpdateApplicationGitConfigResult result =
          JsonUtils.convertValue(qlTestObject.getMap(), QLUpdateApplicationGitConfigResult.class);

      assertThat(result.getClientMutationId()).isEqualTo("req123");
      final QLGitConfigResult gitConfig = result.getGitSyncConfig();
      final QLGitConnector gitConnectorResult = gitConfig.getGitConnector();
      assertThat(gitConnectorResult.getId()).isEqualTo(gitConnector.getUuid());
      assertThat(gitConnectorResult.getName()).isEqualTo("TERRAFORM_CITY_GIT_REPO");
      assertThat(gitConfig.getBranch()).isEqualTo("git-config-test");
      assertThat(gitConfig.getSyncEnabled()).isEqualTo(true);
    }
    //
    // update   existing git config
    {
      String mutation = createUpdateApplicationGitConfigQuery(
          "req1234", application.getUuid(), gitConnector1.getUuid(), "git-config-test-2", false);

      final QLTestObject qlTestObject = qlExecute(mutation, application.getAccountId());
      final QLUpdateApplicationGitConfigResult result =
          JsonUtils.convertValue(qlTestObject.getMap(), QLUpdateApplicationGitConfigResult.class);

      assertThat(result.getClientMutationId()).isEqualTo("req1234");
      final QLGitConfigResult gitConfig = result.getGitSyncConfig();
      final QLGitConnector gitConnectorResult = gitConfig.getGitConnector();
      assertThat(gitConnectorResult.getId()).isEqualTo(gitConnector1.getUuid());
      assertThat(gitConnectorResult.getName()).isEqualTo("TERRAFORM_MAIN_GIT_REPO");
      assertThat(gitConfig.getBranch()).isEqualTo("git-config-test-2");
      assertThat(gitConfig.getSyncEnabled()).isEqualTo(false);
    }
    //      update status only
    {
      final String updateApplicationGitConfigStatusQuery =
          createUpdateApplicationGitConfigStatusQuery("req12345", application.getAppId(), true);
      final QLTestObject qlTestObject = qlExecute(updateApplicationGitConfigStatusQuery, application.getAccountId());
      final QLUpdateApplicationGitConfigResult result =
          JsonUtils.convertValue(qlTestObject.getMap(), QLUpdateApplicationGitConfigResult.class);
      assertThat(result.getClientMutationId()).isEqualTo("req12345");
      final QLGitConfigResult gitConfig = result.getGitSyncConfig();
      final QLGitConnector gitConnectorResult = gitConfig.getGitConnector();
      assertThat(gitConnectorResult.getId()).isEqualTo(gitConnector1.getUuid());
      assertThat(gitConnectorResult.getName()).isEqualTo("TERRAFORM_MAIN_GIT_REPO");
      assertThat(gitConfig.getBranch()).isEqualTo("git-config-test-2");
      assertThat(gitConfig.getSyncEnabled()).isEqualTo(true);
    }
    //      get application git config details
    {
      final String applicationQuery = getApplicationQuery(application.getAppId());
      final QLTestObject qlTestObject = qlExecute(applicationQuery, application.getAccountId());
      final QLUpdateApplicationGitConfigResult result =
          JsonUtils.convertValue(qlTestObject.getMap(), QLUpdateApplicationGitConfigResult.class);
      final QLGitConfigResult gitConfig = result.getGitSyncConfig();
      final QLGitConnector gitConnectorResult = gitConfig.getGitConnector();
      assertThat(gitConnectorResult.getId()).isEqualTo(gitConnector1.getUuid());
      assertThat(gitConnectorResult.getName()).isEqualTo("TERRAFORM_MAIN_GIT_REPO");
      assertThat(gitConfig.getBranch()).isEqualTo("git-config-test-2");
      assertThat(gitConfig.getSyncEnabled()).isEqualTo(true);
    }
    //     remove git config
    {
      final String mutation = removeApplicationGitConfigQuery("req1", application.getUuid());
      final QLTestObject qlTestObject = qlExecute(mutation, application.getAccountId());
      final QLTestObject applicationMap = qlTestObject.sub("application");
      assertThat(applicationMap.get("gitSyncConfig")).isNull();
      assertThat(applicationMap.get("id")).isEqualTo(application.getUuid());
      assertThat(qlTestObject.get("clientMutationId")).isEqualTo("req1");
    }
  }

  private String removeApplicationGitConfigQuery(String clientMutationId, String appId) {
    String queryVariable = $GQL(/*
 {
 clientMutationId: "%s",
 applicationId: "%s"
 }*/ clientMutationId, appId);

    return $GQL(/*
 mutation{
     removeApplicationGitSyncConfig(input:%s) {
     clientMutationId
     application{
       id
       gitSyncConfig {
         branch
         syncEnabled
         gitConnector {
           id
           name
           description
           createdAt
           createdBy {
             id
             name
           }
         }
       }
     }
   }
 }*/ queryVariable);
  }

  private String createUpdateApplicationGitConfigQuery(
      String clientMutationId, String appId, String gitConnectorId, String branchName, boolean enabled) {
    String queryVariable = $GQL(/*
{
clientMutationId: "%s",
applicationId: "%s",
gitConnectorId: "%s",
branch: "%s",
syncEnabled: %s
}*/ clientMutationId, appId, gitConnectorId, branchName, String.valueOf(enabled));

    return $GQL(/*
mutation{
   updateApplicationGitSyncConfig(input:%s) {
   clientMutationId
   gitSyncConfig {
     branch
     syncEnabled
     gitConnector {
       id
       name
       description
       createdAt
       createdBy {
         id
         name
       }
     }
   }
 }
}*/ queryVariable);
  }

  private String createUpdateApplicationGitConfigStatusQuery(String clientMutationId, String appId, boolean enabled) {
    String queryVariable = $GQL(/*
 {
 clientMutationId: "%s",
 applicationId: "%s",
 syncEnabled: %s
 }*/ clientMutationId, appId, String.valueOf(enabled));

    return $GQL(/*
 mutation{
     updateApplicationGitSyncConfigStatus(input:%s) {
     clientMutationId
     gitSyncConfig {
       branch
       syncEnabled
       gitConnector {
         id
         name
         description
         createdAt
         createdBy {
           id
           name
         }
       }
     }
   }
 }*/ queryVariable);
  }

  private String getApplicationQuery(String appId) {
    return $GQL(/*
 query{
     application(applicationId:"%s") {
     gitSyncConfig {
       branch
       syncEnabled
       gitConnector {
         id
         name
         description
         createdAt
         createdBy {
           id
           name
         }
       }
     }
   }
 }*/ appId);
  }

  @Data
  private static class QLGitConfigResult {
    QLGitConnector gitConnector;
    String branch;
    Boolean syncEnabled;
  }
  @Data
  private static class QLUpdateApplicationGitConfigResult {
    private String clientMutationId;
    private QLGitConfigResult gitSyncConfig;
  }
}
