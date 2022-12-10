/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.generator;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.delegate.utils.DelegateRingConstants.LATEST_DELEGATE_IMAGE;
import static io.harness.delegate.utils.DelegateRingConstants.LATEST_UPGRADER_IMAGE;
import static io.harness.delegate.utils.DelegateRingConstants.RING_NAME_1;
import static io.harness.delegate.utils.DelegateRingConstants.RING_NAME_2;
import static io.harness.delegate.utils.DelegateRingConstants.RING_NAME_3;
import static io.harness.delegate.utils.DelegateRingConstants.RING_NAME_4;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateRing;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;

@OwnedBy(DEL)
public class DelegateRingGenerator {
  private final HPersistence persistence;

  @Inject
  public DelegateRingGenerator(HPersistence persistence) {
    this.persistence = persistence;
  }

  public void createAllRings() {
    persistence.save(DelegateRing.builder()
                         .ringName(RING_NAME_1)
                         .delegateImageTag(LATEST_DELEGATE_IMAGE)
                         .upgraderImageTag(LATEST_UPGRADER_IMAGE)
                         .build());
    persistence.save(DelegateRing.builder()
                         .ringName(RING_NAME_2)
                         .delegateImageTag(LATEST_DELEGATE_IMAGE)
                         .upgraderImageTag(LATEST_UPGRADER_IMAGE)
                         .build());
    persistence.save(DelegateRing.builder()
                         .ringName(RING_NAME_3)
                         .delegateImageTag(LATEST_DELEGATE_IMAGE)
                         .upgraderImageTag(LATEST_UPGRADER_IMAGE)
                         .build());
    persistence.save(DelegateRing.builder()
                         .ringName(RING_NAME_4)
                         .delegateImageTag(LATEST_DELEGATE_IMAGE)
                         .upgraderImageTag(LATEST_UPGRADER_IMAGE)
                         .build());
  }
}
