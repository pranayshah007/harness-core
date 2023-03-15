/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.AnnotationDTO;
import io.harness.cvng.servicelevelobjective.beans.AnnotationResponse;
import io.harness.cvng.servicelevelobjective.entities.Annotation;

import java.util.List;

public interface AnnotationService {
  AnnotationResponse create(ProjectParams projectParams, AnnotationDTO annotationDTO);

  List<Annotation> get(ProjectParams projectParams, String sloIdentifier);

  AnnotationResponse update(String annotationId, AnnotationDTO annotationDTO);

  boolean delete(String annotationId);
}
