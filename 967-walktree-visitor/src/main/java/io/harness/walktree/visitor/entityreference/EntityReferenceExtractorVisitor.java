/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.entityreference;

import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.walktree.beans.VisitElementResult;
import io.harness.walktree.visitor.DummyVisitableElement;
import io.harness.walktree.visitor.SimpleVisitor;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;

import com.google.inject.Injector;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.NotImplementedException;

public class EntityReferenceExtractorVisitor extends SimpleVisitor<DummyVisitableElement> {
  Set<EntityDetailProtoDTO> entityReferenceSet;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;

  public Set<EntityDetailProtoDTO> getEntityReferenceSet() {
    return entityReferenceSet;
  }

  public EntityReferenceExtractorVisitor(Injector injector, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> fqnList) {
    super(injector);
    entityReferenceSet = new HashSet<>();
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    if (fqnList != null) {
      fqnList.forEach(levelNode -> VisitorParentPathUtils.addToParentList(this.getContextMap(), levelNode));
    }
  }

  @Override
  public VisitElementResult visitElement(Object currentElement) {
    DummyVisitableElement helperClassInstance = getHelperClass(currentElement);
    if (helperClassInstance == null) {
      throw new NotImplementedException("Helper Class not implemented for object of type" + currentElement.getClass());
    }
    if (helperClassInstance instanceof EntityReferenceExtractor) {
      EntityReferenceExtractor entityReferenceExtractor = (EntityReferenceExtractor) helperClassInstance;
      Set<EntityDetailProtoDTO> newReferences = entityReferenceExtractor.addReference(
          currentElement, accountIdentifier, orgIdentifier, projectIdentifier, this.getContextMap());
      if (EmptyPredicate.isNotEmpty(newReferences)) {
        entityReferenceSet.addAll(newReferences);
      }
    }
    return VisitElementResult.CONTINUE;
  }
}
