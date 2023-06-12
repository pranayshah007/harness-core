/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.custom;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.rule.Owner;

import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

public class TriggerEventHistoryReadHelperTest extends CategoryTest {
  @InjectMocks TriggerEventHistoryReadHelper triggerEventHistoryReadHelper;
  @Mock @Named("secondary-mongo") MongoTemplate secondaryMongoTemplate;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFindCount() {
    when(secondaryMongoTemplate.count((Query) any(), eq(TriggerEventHistory.class))).thenReturn(1L);
    assertThat(triggerEventHistoryReadHelper.findCount(new Query())).isEqualTo(1L);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFind() {
    TriggerEventHistory triggerEventHistory = TriggerEventHistory.builder().build();
    List<TriggerEventHistory> triggerEventHistories = Collections.singletonList(triggerEventHistory);
    when(secondaryMongoTemplate.find((Query) any(), eq(TriggerEventHistory.class))).thenReturn(triggerEventHistories);
    assertThat(triggerEventHistoryReadHelper.find(new Query())).isEqualTo(triggerEventHistories);
  }
}
