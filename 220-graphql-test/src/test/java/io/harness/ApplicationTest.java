/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.generator.AccountGenerator.ACCOUNT_ID;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.VARDAN_BANSAL;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.EntityType.APPLICATION;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.generator.AccountGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.ApplicationGenerator.Applications;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.multiline.MultilineStringMixin;
import io.harness.rule.Owner;
import io.harness.testframework.graphql.QLTestObject;

import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.HarnessTagLink;
import software.wings.graphql.schema.type.QLApplication.QLApplicationKeys;
import software.wings.graphql.schema.type.QLApplicationConnection;
import software.wings.graphql.schema.type.QLTag.QLTagKeys;
import software.wings.graphql.schema.type.QLUser.QLUserKeys;
import software.wings.service.intfc.HarnessTagService;

import com.google.inject.Inject;
import graphql.ExecutionResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class ApplicationTest extends GraphQLTest {
  @Inject private OwnerManager ownerManager;
  @Inject private HarnessTagService harnessTagService;

  @Inject AccountGenerator accountGenerator;
  @Inject ApplicationGenerator applicationGenerator;

  @Test
  @Owner(developers = GEORGE)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testQueryApplication() {
    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();
    owners.add(EmbeddedUser.builder().uuid(generateUuid()).build());

    final Application application = applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST);

    {
      String query = $GQL(/*
{
  application(applicationId: "%s") {
    id
    name
    description
    createdAt
    createdBy {
     id
    }
  }
}*/ application.getUuid());

      final QLTestObject qlTestObject = qlExecute(query, application.getAccountId());
      assertThat(qlTestObject.get(QLApplicationKeys.id)).isEqualTo(application.getUuid());
      assertThat(qlTestObject.get(QLApplicationKeys.name)).isEqualTo(application.getName());
      assertThat(qlTestObject.get(QLApplicationKeys.description)).isEqualTo(application.getDescription());
      assertThat(qlTestObject.get(QLApplicationKeys.createdAt)).isEqualTo(application.getCreatedAt());
      assertThat(qlTestObject.sub(QLApplicationKeys.createdBy).get(QLUserKeys.id))
          .isEqualTo(application.getCreatedBy().getUuid());
    }

    {
      String query = $GQL(/*
{
  application(applicationId: "%s") {
    id
    tags {
      name
      value
    }
  }
}*/ application.getUuid());

      attachTag(application);
      QLTestObject qlApplication = qlExecute(query, application.getAccountId());
      assertThat(qlApplication.get(QLApplicationKeys.id)).isEqualTo(application.getUuid());
      Map<String, String> tagsMap = (LinkedHashMap) (((ArrayList) qlApplication.get("tags")).get(0));
      assertThat(tagsMap.get(QLTagKeys.name)).isEqualTo("color");
      assertThat(tagsMap.get(QLTagKeys.value)).isEqualTo("red");
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testQueryMissingApplication() {
    String query = $GQL(/*
{
  application(applicationId: "blah") {
    id
    name
    description
  }
}*/);

    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();
    owners.add(EmbeddedUser.builder().uuid(generateUuid()).build());
    final Application application = applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST);
    final ExecutionResult result = qlResult(query, application.getAccountId());
    assertThat(result.getErrors().size()).isEqualTo(1);

    assertThat(result.getErrors().get(0).getMessage())
        .isEqualTo("Exception while fetching data (/application) : User not authorized: ACCESS_DENIED");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testQueryApplications() {
    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();

    // final Account account = accountGenerator.getOrCreateAccount(generateUuid(), "testing", "graphql");
    /// accountGenerator.ensureAccount(account);
    // owners.add(account);

    final Application application1 = applicationGenerator.ensureApplication(
        seed, owners, anApplication().name("Application - " + generateUuid()).build());
    final Application application2 = applicationGenerator.ensureApplication(
        seed, owners, anApplication().name("Application - " + generateUuid()).build());
    final Application application3 = applicationGenerator.ensureApplication(
        seed, owners, anApplication().name("Application - " + generateUuid()).build());

    {
      String query = $GQL(/*
{
  applications(limit: 2) {
    nodes {
      id
      name
      description
    }
  }
}*/ application1.getAccountId());

      QLApplicationConnection applicationConnection =
          qlExecute(QLApplicationConnection.class, query, application1.getAccountId());
      assertThat(applicationConnection.getNodes().size()).isEqualTo(2);

      assertThat(applicationConnection.getNodes().get(0).getId()).isEqualTo(application3.getUuid());
      assertThat(applicationConnection.getNodes().get(1).getId()).isEqualTo(application2.getUuid());
    }

    {
      String query = $GQL(/*
{
  applications(limit: 2, offset: 1) {
    nodes {
      id
      name
      description
    }
  }
}*/ application1.getAccountId());

      QLApplicationConnection applicationConnection =
          qlExecute(QLApplicationConnection.class, query, application1.getAccountId());
      assertThat(applicationConnection.getNodes().size()).isEqualTo(2);

      assertThat(applicationConnection.getNodes().get(0).getId()).isEqualTo(application2.getUuid());
      assertThat(applicationConnection.getNodes().get(1).getId()).isEqualTo(application1.getUuid());
    }

    {
      String query = $GQL(/*
{
  applications(limit: 1, offset: 2) {
    nodes {
      id
      tags {
        name
        value
      }
    }
  }
}*/ application1.getAccountId());

      attachTag(application1);
      QLTestObject applicationConnection = qlExecute(query, application1.getAccountId());
      Map<String, Object> applicationMap = (LinkedHashMap) (((ArrayList) applicationConnection.get("nodes")).get(0));
      assertThat(applicationMap.get(QLApplicationKeys.id)).isEqualTo(application1.getUuid());
      Map<String, String> tagsMap = (LinkedHashMap) (((ArrayList) applicationMap.get("tags")).get(0));
      assertThat(tagsMap.get(QLTagKeys.name)).isEqualTo("color");
      assertThat(tagsMap.get(QLTagKeys.value)).isEqualTo("red");
    }
  }

  private void attachTag(Application application) {
    harnessTagService.attachTag(HarnessTagLink.builder()
                                    .accountId(application.getAccountId())
                                    .appId(application.getUuid())
                                    .entityId(application.getUuid())
                                    .entityType(APPLICATION)
                                    .key("color")
                                    .value("red")
                                    .build());
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category({GraphQLTests.class, UnitTests.class})
  public void test_applicationByName() {
    String applicationQueryPattern = MultilineStringMixin.$.GQL(/*
  {
  applicationByName(name:"%s"){
    name
    id
  }
}
*/ ApplicationTest.class);
    String query = String.format(applicationQueryPattern, "Test App");
    final Seed seed = new Seed(0);
    final Owners owners = ownerManager.create();
    owners.add(EmbeddedUser.builder().uuid(generateUuid()).build());
    final Application application = applicationGenerator.ensurePredefined(seed, owners, Applications.GENERIC_TEST);

    final QLTestObject qlApplicationObject = qlExecute(query, ACCOUNT_ID);
    assertThat(qlApplicationObject.get(ApplicationKeys.name)).isEqualTo(application.getName());
  }
}
