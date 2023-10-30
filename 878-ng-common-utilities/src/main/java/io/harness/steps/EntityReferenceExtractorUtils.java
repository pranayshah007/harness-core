/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;
import io.harness.walktree.visitor.entityreference.SecretReferenceExtractorVisitor;
import io.harness.walktree.visitor.entityreference.beans.VisitedSecretReference;

import com.google.inject.Inject;
import java.util.LinkedList;
import java.util.Set;

/**
 * A helper util to extract out all referred entities in a given object
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class EntityReferenceExtractorUtils {
  @Inject SimpleVisitorFactory simpleVisitorFactory;

  public Set<EntityDetailProtoDTO> extractReferredEntities(Ambiance ambiance, Object object) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    EntityReferenceExtractorVisitor visitor = simpleVisitorFactory.obtainEntityReferenceExtractorVisitor(
        accountIdentifier, orgIdentifier, projectIdentifier, new LinkedList<>());
    visitor.walkElementTree(object);
    return visitor.getEntityReferenceSet();
  }

  public Set<VisitedSecretReference> extractReferredSecrets(Ambiance ambiance, Object object) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    SecretReferenceExtractorVisitor visitor = simpleVisitorFactory.obtainSecretReferenceExtractorVisitor(
        accountIdentifier, orgIdentifier, projectIdentifier, new LinkedList<>());
    visitor.walkElementTree(object);
    return visitor.getVisitedSecretReferenceSet();
  }
}
