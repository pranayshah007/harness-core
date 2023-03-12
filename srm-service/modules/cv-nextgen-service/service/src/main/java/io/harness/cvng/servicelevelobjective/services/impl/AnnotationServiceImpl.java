/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.AnnotationDTO;
import io.harness.cvng.servicelevelobjective.beans.AnnotationResponse;
import io.harness.cvng.servicelevelobjective.entities.Annotation;
import io.harness.cvng.servicelevelobjective.entities.Annotation.AnnotationBuilder;
import io.harness.cvng.servicelevelobjective.entities.Annotation.AnnotationKeys;
import io.harness.cvng.servicelevelobjective.services.api.AnnotationService;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;

public class AnnotationServiceImpl implements AnnotationService {
  @Inject private HPersistence hPersistence;

  @Override
  public AnnotationResponse create(ProjectParams projectParams, AnnotationDTO annotationDTO) {
    Annotation annotation = getAnnotationFromAnnotationDTO(projectParams, annotationDTO);
    hPersistence.save(annotation);
    return AnnotationResponse.builder()
        .annotationDTO(annotationDTO)
        .createdAt(annotation.getCreatedAt())
        .lastModifiedAt(annotation.getLastUpdatedAt())
        .build();
  }

  @Override
  public AnnotationResponse update(String annotationId, AnnotationDTO annotationDTO) {
    Annotation annotation = getEntity(annotationId);
    validateUpdate(annotation, annotationDTO);
    UpdateOperations<Annotation> updateOperations = hPersistence.createUpdateOperations(Annotation.class);
    updateOperations.set(AnnotationKeys.message, annotationDTO.getMessage());
    hPersistence.update(annotation, updateOperations);
    Annotation updatedAnnotation = getEntity(annotationId);
    return AnnotationResponse.builder()
        .annotationDTO(annotationDTO)
        .createdAt(updatedAnnotation.getCreatedAt())
        .lastModifiedAt(updatedAnnotation.getLastUpdatedAt())
        .build();
  }

  @Override
  public boolean delete(String annotationId) {
    Annotation annotation = Annotation.builder().uuid(annotationId).build();
    return hPersistence.delete(annotation);
  }

  private void validateUpdate(Annotation annotation, AnnotationDTO updatedAnnotationDTO) {
    Boolean expression = annotation.getStartTime() == updatedAnnotationDTO.getStartTime()
        && annotation.getEndTime() == updatedAnnotationDTO.getEndTime();
    Preconditions.checkArgument(expression, "Can not update the start/end time.");
  }

  private Annotation getAnnotationFromAnnotationDTO(ProjectParams projectParams, AnnotationDTO annotationDTO) {
    return Annotation.builder()
        .accountId(projectParams.getAccountIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .sloIdentifier(annotationDTO.getSloIdentifier())
        .message(annotationDTO.getMessage())
        .startTime(annotationDTO.getStartTime())
        .endTime(annotationDTO.getEndTime())
        .build();
  }

  private Annotation getEntity(String annotationId) {
    return hPersistence.get(Annotation.class, annotationId);
  }
}
