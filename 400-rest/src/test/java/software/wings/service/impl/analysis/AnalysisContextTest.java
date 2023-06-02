/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.Misc.replaceDotWithUnicode;
import static io.harness.persistence.HPersistence.DEFAULT_STORE;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.MetricsDataCollectionInfo.MetricsDataCollectionInfoKeys;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfoV2;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AnalysisContextTest extends WingsBaseTest {
  @Inject private HPersistence persistence;

  private static final SecureRandom random = new SecureRandom();

  private AnalysisContext analysisContext;
  private long timeSeriesAnalysisIteration;
  private long logAnalysisIteration;
  private long feedbackIteration;

  @Before
  public void setUp() throws IllegalAccessException {
    analysisContext = AnalysisContext.builder().stateExecutionId(generateUuid()).build();
    timeSeriesAnalysisIteration = random.nextLong();
    logAnalysisIteration = random.nextLong();
    feedbackIteration = random.nextLong();

    FieldUtils.writeField(analysisContext, AnalysisContextKeys.logAnalysisIteration, logAnalysisIteration, true);
    FieldUtils.writeField(
        analysisContext, AnalysisContextKeys.timeSeriesAnalysisIteration, timeSeriesAnalysisIteration, true);
    FieldUtils.writeField(analysisContext, AnalysisContextKeys.feedbackIteration, feedbackIteration, true);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testObtainNextIteration_timeSeriesAnalysisIteration() {
    assertThat(analysisContext.obtainNextIteration(AnalysisContextKeys.timeSeriesAnalysisIteration))
        .isEqualTo(timeSeriesAnalysisIteration);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testUpdateNextIteration_timeSeriesAnalysisIteration() {
    assertThat(analysisContext.getTimeSeriesAnalysisIteration()).isEqualTo(timeSeriesAnalysisIteration);
    long updatedIteration = random.nextLong();
    analysisContext.updateNextIteration(AnalysisContextKeys.timeSeriesAnalysisIteration, updatedIteration);
    assertThat(analysisContext.getTimeSeriesAnalysisIteration()).isEqualTo(updatedIteration);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testObtainNextIteration_logAnalysisIteration() {
    assertThat(analysisContext.obtainNextIteration(AnalysisContextKeys.logAnalysisIteration))
        .isEqualTo(logAnalysisIteration);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testUpdateNextIteration_logAnalysisIteration() {
    assertThat(analysisContext.getTimeSeriesAnalysisIteration()).isEqualTo(timeSeriesAnalysisIteration);
    long updatedIteration = random.nextLong();
    analysisContext.updateNextIteration(AnalysisContextKeys.logAnalysisIteration, updatedIteration);
    assertThat(analysisContext.getLogAnalysisIteration()).isEqualTo(updatedIteration);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testObtainNextIteration_feedbackIteration() {
    assertThat(analysisContext.obtainNextIteration(AnalysisContextKeys.feedbackIteration)).isEqualTo(feedbackIteration);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateNextIteration_feedbackIteration() {
    assertThat(analysisContext.getTimeSeriesAnalysisIteration()).isEqualTo(timeSeriesAnalysisIteration);
    long updatedIteration = random.nextLong();
    analysisContext.updateNextIteration(AnalysisContextKeys.feedbackIteration, updatedIteration);
    assertThat(analysisContext.obtainNextIteration(AnalysisContextKeys.feedbackIteration)).isEqualTo(updatedIteration);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testObtainNextIteration_invalidFieldName() {
    assertThatThrownBy(() -> analysisContext.obtainNextIteration(generateUuid()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testUpdateNextIteration_invalidFieldName() {
    assertThatThrownBy(() -> analysisContext.updateNextIteration(generateUuid(), random.nextLong()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testHostsWithDots() {
    Set<String> controlNodes = Sets.newHashSet("harness.todolist.control1", "harness.todolist.control2");
    Set<String> testNodes = Sets.newHashSet("harness.todolist.test1", "harness.todolist.test2");

    final AnalysisContext analysisContext = AnalysisContext.builder()
                                                .stateExecutionId(generateUuid())
                                                .controlNodes(nodesWithGroups(controlNodes))
                                                .testNodes(nodesWithGroups(testNodes))
                                                .build();
    final String analysisContextId = persistence.save(analysisContext);
    final DBCollection collection = persistence.getCollection(DEFAULT_STORE, "verificationServiceTask");
    final BasicDBObject dbObject = (BasicDBObject) collection.findOne();
    dbObject.get(AnalysisContextKeys.controlNodes);
    verifyDotsReplacedWithUniCode((BasicDBObject) dbObject.get(AnalysisContextKeys.controlNodes), controlNodes);
    verifyDotsReplacedWithUniCode((BasicDBObject) dbObject.get(AnalysisContextKeys.testNodes), testNodes);

    final AnalysisContext savedAnalysisContext = persistence.get(AnalysisContext.class, analysisContextId);
    assertThat(savedAnalysisContext.getControlNodes().keySet()).isEqualTo(controlNodes);
    assertThat(savedAnalysisContext.getTestNodes().keySet()).isEqualTo(testNodes);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveAnalysisContext_withDotsInHostnameV2() {
    Set<String> controlNodes = Sets.newHashSet("harness.todolist.control1", "harness.todolist.control2");
    Set<String> testNodes = Sets.newHashSet("harness.todolist.test1", "harness.todolist.test2");
    Map<String, String> hostToGroupNameMap = new HashMap<>();
    hostToGroupNameMap.put("harness.todolist.control1", "default");
    hostToGroupNameMap.put("harness.todolist.control2", "default");
    hostToGroupNameMap.put("harness.todolist.test1", "default");
    hostToGroupNameMap.put("harness.todolist.test2", "default");

    final AnalysisContext analysisContext = AnalysisContext.builder()
                                                .stateExecutionId(generateUuid())
                                                .controlNodes(nodesWithGroups(controlNodes))
                                                .testNodes(nodesWithGroups(testNodes))
                                                .build();
    analysisContext.setDataCollectionInfov2(
        NewRelicDataCollectionInfoV2.builder().hostsToGroupNameMap(hostToGroupNameMap).build());

    final String analysisContextId = persistence.save(analysisContext);
    final DBCollection collection = persistence.getCollection(DEFAULT_STORE, "verificationServiceTask");
    final BasicDBObject dbObject = (BasicDBObject) collection.findOne();
    dbObject.get(AnalysisContextKeys.controlNodes);
    verifyDotsReplacedWithUniCode((BasicDBObject) dbObject.get(AnalysisContextKeys.controlNodes), controlNodes);
    verifyDotsReplacedWithUniCode((BasicDBObject) dbObject.get(AnalysisContextKeys.testNodes), testNodes);
    verifyDotsReplacedWithUniCode(
        (BasicDBObject) ((BasicDBObject) dbObject.get(AnalysisContextKeys.dataCollectionInfov2))
            .get(MetricsDataCollectionInfoKeys.hostsToGroupNameMap),
        hostToGroupNameMap.keySet());

    final AnalysisContext savedAnalysisContext = persistence.get(AnalysisContext.class, analysisContextId);
    assertThat(savedAnalysisContext.getControlNodes().keySet()).isEqualTo(controlNodes);
    assertThat(savedAnalysisContext.getTestNodes().keySet()).isEqualTo(testNodes);
    assertThat(((NewRelicDataCollectionInfoV2) savedAnalysisContext.getDataCollectionInfov2()).getHostsToGroupNameMap())
        .isEqualTo(hostToGroupNameMap);
  }

  private void verifyDotsReplacedWithUniCode(BasicDBObject nodesInDb, Set<String> nodesToVerify) {
    Set<String> unicodeNodes = new HashSet<>();
    nodesToVerify.forEach(node -> unicodeNodes.add(replaceDotWithUnicode(node)));
    assertThat(nodesInDb.keySet().size()).isEqualTo(nodesToVerify.size());
    nodesInDb.keySet().forEach(node -> {
      assertThat(nodesToVerify).doesNotContain(node);
      assertThat(unicodeNodes).contains(node);
    });
  }

  private Map<String, String> nodesWithGroups(Set<String> nodes) {
    Map<String, String> rv = new HashMap<>();
    nodes.forEach(node -> rv.put(node, DEFAULT_GROUP_NAME));
    return rv;
  }
}
