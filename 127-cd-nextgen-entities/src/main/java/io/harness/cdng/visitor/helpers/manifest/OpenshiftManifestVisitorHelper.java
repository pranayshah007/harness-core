/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.manifest;

import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PARAMS_PATHS;

import io.harness.cdng.manifest.yaml.kinds.OpenshiftManifest;
import io.harness.cdng.visitor.helpers.store.HarnessStoreVisitorHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class OpenshiftManifestVisitorHelper implements ConfigValidator, EntityReferenceExtractor {
  @Inject HarnessStoreVisitorHelper harnessStoreVisitorHelper;

  @Override
  public void validate(Object object, ValidationVisitor visitor) {}

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    OpenshiftManifest openshiftManifest = (OpenshiftManifest) originalElement;
    return OpenshiftManifest.builder().identifier(openshiftManifest.getIdentifier()).build();
  }

  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    OpenshiftManifest openshiftManifest = (OpenshiftManifest) object;
    if (!HARNESS_STORE_TYPE.equals(openshiftManifest.getStoreConfig().getKind())) {
      return Collections.emptySet();
    }
    return harnessStoreVisitorHelper.getEntityDetailsProtoDTO(openshiftManifest.getParamsPaths(), accountIdentifier,
        orgIdentifier, projectIdentifier, contextMap, PARAMS_PATHS);
  }
}
