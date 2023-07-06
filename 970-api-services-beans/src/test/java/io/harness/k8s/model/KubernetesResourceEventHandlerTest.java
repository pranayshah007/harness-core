/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.model.KubernetesResourceEventHandler.getHandler;
import static io.harness.rule.OwnerRule.MLUKIC;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@OwnedBy(CDP)
public class KubernetesResourceEventHandlerTest extends CategoryTest {
  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetHandler() {
    getHandler("Deployment");
    getHandler("HorizontalPodAutoscaler");
    getHandler("PodDisruptionBudget");
    getHandler("CustomCRD");
    getHandler("ASDAPSIJDOAP");
    getHandler("");
    getHandler(null);
  }
}
