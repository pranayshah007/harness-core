/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.support;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.tasklet.dto.HarnessTags;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.HarnessTagLink;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HarnessTagServiceTest extends CategoryTest {
  @InjectMocks private HarnessTagService harnessTagService;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;

  private static final String ENTITY_ID = "entityId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String TAG_KEY = "tagKey";
  private static final String TAG_VALUE = "tagValue";

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetK8sWorkloadLabel() {
    when(cloudToHarnessMappingService.getTagLinksWithEntityId(ACCOUNT_ID, ENTITY_ID))
        .thenReturn(ImmutableList.of(getHarnessTags()));
    List<HarnessTags> harnessTags = harnessTagService.getHarnessTags(ACCOUNT_ID, ENTITY_ID);
    assertThat(harnessTags).contains(HarnessTags.builder().key(TAG_KEY).value(TAG_VALUE).build());
  }

  private HarnessTagLink getHarnessTags() {
    return HarnessTagLink.builder().key(TAG_KEY).value(TAG_VALUE).build();
  }
}
