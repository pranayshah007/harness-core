/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VARDAN_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.generator.ChangeContentGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.rule.Owner;

import software.wings.beans.record.EntityYamlRecord;
import software.wings.graphql.schema.type.QLPageInfo;
import software.wings.graphql.schema.type.audit.QLChangeContent;
import software.wings.graphql.schema.type.audit.QLChangeContentConnection;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChangeContentTest extends GraphQLTest {
  @Inject private OwnerManager ownerManager;
  @Inject private ChangeContentGenerator changeContentGenerator;
  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testQueryForChangeSetFilter() {
    String changeContentQueryPattern = $.GQL(/*
{
  auditChangeContent(filters:{
    changeSetId: "%s"
  }, limit: %d){
    nodes{
      resourceId
      changeSetId
      oldYaml
      newYaml
    }
  }
}
*/ ChangeContentTest.class);
    String query = String.format(changeContentQueryPattern, "changeSetId", 1);
    verify(query);
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testQueryForChangeSetAndResourceFilter() {
    String changeContentQueryPattern = $.GQL(/*
{
  auditChangeContent(filters:{
    changeSetId: "%s"
    resourceId: "%s"
  }, limit: %d){
    nodes{
      resourceId
      changeSetId
      oldYaml
      newYaml
    }
  }
}
*/ ChangeContentTest.class);
    String query = String.format(changeContentQueryPattern, "changeSetId", "entityId", 1);
    verify(query);
  }

  private void verify(String query) {
    final Randomizer.Seed seed = new Randomizer.Seed(0);
    final OwnerManager.Owners owners = ownerManager.create();
    owners.add(EmbeddedUser.builder().uuid(generateUuid()).email("email").name("name").build());
    List<EntityYamlRecord> entityYamlRecords = changeContentGenerator.ensureYamlTest(seed, owners);
    if (entityYamlRecords != null && entityYamlRecords.size() == 2) {
      QLChangeContentConnection changeContentConnection =
          qlExecute(QLChangeContentConnection.class, query, entityYamlRecords.get(0).getAccountId());
      assertThat(changeContentConnection.getNodes().size()).isEqualTo(1);
      verifyPageInfo(changeContentConnection.getPageInfo());
      if (changeContentConnection.getNodes() != null && changeContentConnection.getNodes().size() == 1) {
        QLChangeContent changeContent = changeContentConnection.getNodes().get(0);
        verifyChangeContent(changeContent, entityYamlRecords.get(0), entityYamlRecords.get(1));
      }
    }
  }

  private void verifyChangeContent(
      QLChangeContent changeContent, EntityYamlRecord oldRecord, EntityYamlRecord newRecord) {
    if (changeContent != null && oldRecord != null && newRecord != null) {
      assertThat(changeContent.getNewYaml()).isEqualTo(newRecord.getYamlContent());
      assertThat(changeContent.getNewYamlPath()).isEqualTo(newRecord.getYamlPath());
      assertThat(changeContent.getOldYaml()).isEqualTo(oldRecord.getYamlContent());
      assertThat(changeContent.getOldYamlPath()).isEqualTo(oldRecord.getYamlPath());
    }
  }

  private void verifyPageInfo(QLPageInfo pageInfo) {
    if (pageInfo == null) {
      return;
    }
    assertThat(pageInfo.getHasMore()).isEqualTo(false);
    assertThat(pageInfo.getLimit()).isEqualTo(1);
    assertThat(pageInfo.getOffset()).isEqualTo(0);
    assertThat(pageInfo.getTotal()).isEqualTo(1);
  }
}
