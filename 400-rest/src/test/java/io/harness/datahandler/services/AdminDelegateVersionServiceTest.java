/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.datahandler.services;

import static io.harness.delegate.beans.VersionOverrideType.DELEGATE_IMAGE_TAG;
import static io.harness.rule.OwnerRule.JENNY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.cache.CachingTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.VersionOverride;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AdminDelegateVersionServiceTest extends CachingTest {
  @Inject @InjectMocks private AdminDelegateVersionService adminDelegateVersionService;
  @Inject private HPersistence persistence;
  @Mock private OutboxService outboxService;

  private static final String DELEGATE_TAG = "1.9.80000";
  public static final String ACCOUNT_ID = "accountId";

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void tesAuditForDelegateCustomImageTag() {
    final VersionOverride overrideImmutable =
        VersionOverride.builder(ACCOUNT_ID).overrideType(DELEGATE_IMAGE_TAG).version("latest:88").build();
    persistence.save(overrideImmutable);
    adminDelegateVersionService.setCustomDelegateImageTag(DELEGATE_TAG, ACCOUNT_ID, false, 3);
    verify(outboxService, times(1)).save(any());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void tesAuditForDelegateImageTag() {
    final VersionOverride overrideImmutable =
        VersionOverride.builder(ACCOUNT_ID).overrideType(DELEGATE_IMAGE_TAG).version("latest:88").build();
    persistence.save(overrideImmutable);
    adminDelegateVersionService.setDelegateImageTag(DELEGATE_TAG, ACCOUNT_ID, false, 3);
    verify(outboxService, times(1)).save(any());
  }
}
