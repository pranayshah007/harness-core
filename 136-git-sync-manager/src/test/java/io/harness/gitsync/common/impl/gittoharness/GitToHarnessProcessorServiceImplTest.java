/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl.gittoharness;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.EntityType;
import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.gitsync.ChangeSet;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.beans.GitToHarnessFilesGroupedByMsvc;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class GitToHarnessProcessorServiceImplTest extends GitSyncTestBase {
  @Inject GitToHarnessProcessorServiceImpl gitToHarnessProcessorService;

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testGroupFilesByMicroservices() {
    Map<EntityType, List<ChangeSet>> mapOfEntityTypeAndContent = new HashMap<>();
    ChangeSet templateChangeSet = ChangeSet.newBuilder()
                                      .setChangeSetId("abc")
                                      .setAccountId("accountId")
                                      .setEntityType(EntityTypeProtoEnum.TEMPLATE)
                                      .setYaml("yaml")
                                      .build();
    ChangeSet pipelineChangeSet = ChangeSet.newBuilder()
                                      .setChangeSetId("abc")
                                      .setAccountId("accountId")
                                      .setEntityType(EntityTypeProtoEnum.PIPELINES)
                                      .setYaml("yaml")
                                      .build();
    mapOfEntityTypeAndContent.put(EntityType.TEMPLATE, Arrays.asList(templateChangeSet));
    mapOfEntityTypeAndContent.put(EntityType.PIPELINES, Arrays.asList(pipelineChangeSet));

    List<GitToHarnessFilesGroupedByMsvc> gitToHarnessFilesGroupedByMsvcs =
        gitToHarnessProcessorService.groupFilesByMicroservices(mapOfEntityTypeAndContent);
    assertThat(gitToHarnessFilesGroupedByMsvcs.get(0).getMicroservice()).isEqualTo(Microservice.TEMPLATESERVICE);
    assertThat(gitToHarnessFilesGroupedByMsvcs.get(1).getMicroservice()).isEqualTo(Microservice.PMS);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testGroupFilesByMicroservicesForAllServices() {
    Map<EntityType, List<ChangeSet>> mapOfEntityTypeAndContent = new HashMap<>();

    ChangeSet pipelineChangeSet1 = ChangeSet.newBuilder()
                                       .setChangeSetId("abc")
                                       .setAccountId("accountId")
                                       .setEntityType(EntityTypeProtoEnum.PIPELINES)
                                       .setYaml("yaml")
                                       .build();
    ChangeSet pipelineChangeSet2 = ChangeSet.newBuilder()
                                       .setChangeSetId("abc")
                                       .setAccountId("accountId")
                                       .setEntityType(EntityTypeProtoEnum.PIPELINES)
                                       .setYaml("yaml")
                                       .build();
    mapOfEntityTypeAndContent.put(
        EntityType.PIPELINES, new ArrayList<>(Arrays.asList(pipelineChangeSet1, pipelineChangeSet2)));

    ChangeSet templateChangeSet1 = ChangeSet.newBuilder()
                                       .setChangeSetId("abc")
                                       .setAccountId("accountId")
                                       .setEntityType(EntityTypeProtoEnum.TEMPLATE)
                                       .setYaml("yaml")
                                       .build();
    ChangeSet templateChangeSet2 = ChangeSet.newBuilder()
                                       .setChangeSetId("abc")
                                       .setAccountId("accountId")
                                       .setEntityType(EntityTypeProtoEnum.TEMPLATE)
                                       .setYaml("yaml")
                                       .build();
    mapOfEntityTypeAndContent.put(
        EntityType.TEMPLATE, new ArrayList<>(Arrays.asList(templateChangeSet1, templateChangeSet2)));

    ChangeSet coreChangeSet1 = ChangeSet.newBuilder()
                                   .setChangeSetId("abc")
                                   .setAccountId("accountId")
                                   .setEntityType(EntityTypeProtoEnum.CONNECTORS)
                                   .setYaml("yaml")
                                   .build();
    ChangeSet coreChangeSet2 = ChangeSet.newBuilder()
                                   .setChangeSetId("abc")
                                   .setAccountId("accountId")
                                   .setEntityType(EntityTypeProtoEnum.CONNECTORS)
                                   .setYaml("yaml")
                                   .build();
    mapOfEntityTypeAndContent.put(
        EntityType.CONNECTORS, new ArrayList<>(Arrays.asList(coreChangeSet1, coreChangeSet2)));

    ChangeSet featureFlagChangeSet1 = ChangeSet.newBuilder()
                                          .setChangeSetId("abc")
                                          .setAccountId("accountId")
                                          .setEntityType(EntityTypeProtoEnum.FEATURE_FLAGS)
                                          .setYaml("yaml")
                                          .build();
    mapOfEntityTypeAndContent.put(EntityType.FEATURE_FLAGS, new ArrayList<>(Arrays.asList(featureFlagChangeSet1)));

    ChangeSet inputSetChangeSet1 = ChangeSet.newBuilder()
                                       .setChangeSetId("abc")
                                       .setAccountId("accountId")
                                       .setEntityType(EntityTypeProtoEnum.INPUT_SETS)
                                       .setYaml("yaml")
                                       .build();
    ChangeSet inputSetChangeSet2 = ChangeSet.newBuilder()
                                       .setChangeSetId("abc")
                                       .setAccountId("accountId")
                                       .setEntityType(EntityTypeProtoEnum.INPUT_SETS)
                                       .setYaml("yaml")
                                       .build();
    mapOfEntityTypeAndContent.put(
        EntityType.INPUT_SETS, new ArrayList<>(Arrays.asList(inputSetChangeSet1, inputSetChangeSet2)));

    List<GitToHarnessFilesGroupedByMsvc> gitToHarnessFilesGroupedByMsvcs =
        gitToHarnessProcessorService.groupFilesByMicroservices(mapOfEntityTypeAndContent);
    assertThat(gitToHarnessFilesGroupedByMsvcs.get(0).getMicroservice()).isEqualTo(Microservice.CORE);
    assertThat(gitToHarnessFilesGroupedByMsvcs.get(1).getMicroservice()).isEqualTo(Microservice.TEMPLATESERVICE);
    assertThat(gitToHarnessFilesGroupedByMsvcs.get(2).getMicroservice()).isEqualTo(Microservice.CF);
    assertThat(gitToHarnessFilesGroupedByMsvcs.get(3).getMicroservice()).isEqualTo(Microservice.PMS);
  }
}
