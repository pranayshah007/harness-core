/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Permit;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.PermitService;

import com.google.inject.Inject;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mongodb.morphia.query.Query;

public class PermitServiceTest extends WingsBaseTest {
  @InjectMocks @Inject private PermitService permitService;
  @InjectMocks @Inject private PermitServiceImpl permitServiceImpl;
  @Mock private WingsPersistence wingsPersistence;

  @Before
  public void setup() {
    when(wingsPersistence.save((PersistentEntity) any())).thenReturn("randomUUid");
    Query<PersistentEntity> mockedQueryEntity = mock(Query.class);
    when(wingsPersistence.createQuery(any())).thenReturn(mockedQueryEntity);
    when(mockedQueryEntity.filter(anyString(), any())).thenReturn(mockedQueryEntity);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldAcquirePermit() {
    assertThat(acquirePermit()).isNotNull();
  }

  private String acquirePermit() {
    int leaseDuration = (int) (TimeUnit.MINUTES.toMillis(1) * PermitServiceImpl.getBackoffMultiplier(0));
    Date expiryDate = new Date(System.currentTimeMillis() + leaseDuration);
    return permitService.acquirePermit(Permit.builder()
                                           .appId(APP_ID)
                                           .group("ARTIFACT_STREAM_GROUP")
                                           .key(ARTIFACT_STREAM_ID)
                                           .expireAt(expiryDate)
                                           .leaseDuration(leaseDuration)
                                           .build());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldReleasePermitByKey() {
    String permitId = acquirePermit();
    assertThat(permitId).isNotNull();
    assertThat(permitService.releasePermitByKey(ARTIFACT_STREAM_ID)).isTrue();
  }
  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testShouldDeleteByAccountId() {
    when(wingsPersistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    permitServiceImpl.deleteByAccountId(ACCOUNT_ID);
    verify(wingsPersistence, times(1)).delete((Query<PersistentEntity>) any());
  }
}
