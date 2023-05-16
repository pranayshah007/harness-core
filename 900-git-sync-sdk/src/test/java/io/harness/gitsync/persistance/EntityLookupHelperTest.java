/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.persistance;

import static io.harness.rule.OwnerRule.PHOENIKX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.rule.Owner;

import com.google.protobuf.StringValue;
import javax.cache.Cache;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class EntityLookupHelperTest extends CategoryTest {
  private final EntityLookupHelper entityLookupHelper;
  private final Cache<String, Boolean> gitEnabledCache;

  public EntityLookupHelperTest() {
    gitEnabledCache = mock(Cache.class);
    entityLookupHelper = new EntityLookupHelper(null, gitEnabledCache);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testFetchKey() {
    when(gitEnabledCache.get(any())).thenReturn(true);

    boolean success = entityLookupHelper.fetchKey(EntityScopeInfo.newBuilder()
                                                      .setAccountId("account")
                                                      .setOrgId(StringValue.of("org"))
                                                      .setProjectId(StringValue.of("project"))
                                                      .build());
    assertThat(success).isEqualTo(true);
    verify(gitEnabledCache, times(1)).get("/account/org/project");
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdateKey() {
    when(gitEnabledCache.remove(any())).thenReturn(true);
    entityLookupHelper.updateKey(EntityScopeInfo.newBuilder()
                                     .setAccountId("account")
                                     .setOrgId(StringValue.of("org"))
                                     .setProjectId(StringValue.of("project"))
                                     .build());
    verify(gitEnabledCache, times(1)).remove("/account/org/project");
  }
}
