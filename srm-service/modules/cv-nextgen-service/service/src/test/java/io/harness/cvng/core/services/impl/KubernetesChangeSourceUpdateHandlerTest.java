/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.ANJAN;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class KubernetesChangeSourceUpdateHandlerTest extends CvNextGenTestBase {
  @Inject private KubernetesChangeSourceUpdateHandler updateHandler;
  @Mock VerificationManagerService verificationManagerService;
  String accountIdentifier;

  @Before
  public void setup() throws IllegalAccessException {
    FieldUtils.writeField(updateHandler, "verificationManagerService", verificationManagerService, true);
    accountIdentifier = "1234_accountId";
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testHandleDelete() {
    KubernetesChangeSource kubeChangeSource = KubernetesChangeSource.builder()
                                                  .dataCollectionTaskId(UUIDGenerator.generateUuid())
                                                  .accountId(accountIdentifier)
                                                  .dataCollectionRequired(true)
                                                  .build();

    doNothing()
        .when(verificationManagerService)
        .deletePerpetualTask(eq(accountIdentifier), eq(kubeChangeSource.getDataCollectionTaskId()));
    updateHandler.handleDelete(kubeChangeSource);
    verify(verificationManagerService, times(1))
        .deletePerpetualTask(accountIdentifier, kubeChangeSource.getDataCollectionTaskId());
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testHandleDelete_missingDataCollectionId() {
    KubernetesChangeSource kubeChangeSource =
        KubernetesChangeSource.builder().accountId(accountIdentifier).dataCollectionRequired(true).build();
    updateHandler.handleDelete(kubeChangeSource);
    verifyZeroInteractions(verificationManagerService);
  }
}
