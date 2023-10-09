/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.entityreference;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.walktree.beans.VisitElementResult;
import io.harness.walktree.visitor.DummyVisitableElement;
import io.harness.walktree.visitor.SimpleVisitor;
import io.harness.walktree.visitor.entityreference.beans.VisitedSecretReference;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;

import com.google.inject.Injector;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.NotImplementedException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class SecretReferenceExtractorVisitor extends SimpleVisitor<DummyVisitableElement> {
  private final Set<VisitedSecretReference> secretReferenceSet;
  private final String accountIdentifier;
  private final String orgIdentifier;
  private final String projectIdentifier;

  public SecretReferenceExtractorVisitor(Injector injector, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> fqnList) {
    super(injector);
    this.secretReferenceSet = new HashSet<>();
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    if (fqnList != null) {
      fqnList.forEach(levelNode -> VisitorParentPathUtils.addToParentList(this.getContextMap(), levelNode));
    }
  }

  public Set<VisitedSecretReference> getVisitedSecretReferenceSet() {
    return this.secretReferenceSet;
  }

  @Override
  public VisitElementResult visitElement(Object currentElement) {
    DummyVisitableElement helperClassInstance = getHelperClass(currentElement);
    if (helperClassInstance == null) {
      throw new NotImplementedException("Helper Class not implemented for object of type" + currentElement.getClass());
    }

    if (helperClassInstance instanceof SecretReferenceExtractor) {
      SecretReferenceExtractor secretReferenceExtractor = (SecretReferenceExtractor) helperClassInstance;
      Set<VisitedSecretReference> newReferences = secretReferenceExtractor.addSecretReference(
          currentElement, accountIdentifier, orgIdentifier, projectIdentifier, this.getContextMap());
      if (EmptyPredicate.isNotEmpty(newReferences)) {
        secretReferenceSet.addAll(newReferences);
      }
    }

    return VisitElementResult.CONTINUE;
  }
}
