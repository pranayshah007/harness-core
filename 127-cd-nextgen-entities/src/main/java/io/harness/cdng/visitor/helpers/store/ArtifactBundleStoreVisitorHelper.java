/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.store;

import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArtifactBundleStoreVisitorHelper implements EntityReferenceExtractor {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return null;
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    return Collections.emptySet();
  }
}
