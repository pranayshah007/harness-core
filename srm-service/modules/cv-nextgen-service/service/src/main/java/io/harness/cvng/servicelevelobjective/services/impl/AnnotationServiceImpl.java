/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.AnnotationDTO;
import io.harness.cvng.servicelevelobjective.beans.AnnotationResponse;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.Annotation;
import io.harness.cvng.servicelevelobjective.entities.Annotation.AnnotationKeys;
import io.harness.cvng.servicelevelobjective.services.api.AnnotationService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AnnotationServiceImpl implements AnnotationService {
  @Inject private HPersistence hPersistence;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveService;

  @Override
  public AnnotationResponse create(ProjectParams projectParams, AnnotationDTO annotationDTO) {
    validateCreate(projectParams, annotationDTO);
    Annotation annotation = getAnnotationFromAnnotationDTO(projectParams, annotationDTO);
    hPersistence.save(annotation);
    return AnnotationResponse.builder()
        .annotationDTO(annotationDTO)
        .createdAt(annotation.getCreatedAt())
        .lastModifiedAt(annotation.getLastUpdatedAt())
        .build();
  }

  @Override
  public List<Annotation> get(ProjectParams projectParams, String sloIdentifier) {
    Query<Annotation> query = hPersistence.createQuery(Annotation.class)
                                  .filter(AnnotationKeys.accountId, projectParams.getAccountIdentifier())
                                  .filter(AnnotationKeys.sloIdentifier, sloIdentifier);
    if (isNotEmpty(projectParams.getOrgIdentifier())) {
      query.filter(AnnotationKeys.orgIdentifier, projectParams.getOrgIdentifier());
    }
    if (isNotEmpty(projectParams.getProjectIdentifier())) {
      query.filter(AnnotationKeys.projectIdentifier, projectParams.getProjectIdentifier());
    }

    return query.asList();
  }

  @Override
  public AnnotationResponse update(String annotationId, AnnotationDTO annotationDTO) {
    Annotation annotation = checkIfAnnotationPresent(annotationId);
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
    checkIfAnnotationPresent(annotationId);
    return hPersistence.delete(Annotation.class, annotationId);
  }

  private Annotation checkIfAnnotationPresent(String annotationId) {
    Optional<Annotation> optionalAnnotation = getOptionalAnnotation(annotationId);
    if (optionalAnnotation.isEmpty()) {
      throw new InvalidRequestException(String.format("Annotation with uuid %s is not present.", annotationId));
    }
    return optionalAnnotation.get();
  }

  private void checkIfStartTimeIsUnique(ProjectParams projectParams, AnnotationDTO annotationDTO) {
    List<Annotation> annotations = get(projectParams, annotationDTO.getSloIdentifier())
                                       .stream()
                                       .filter(annotation -> annotationDTO.getStartTime() == annotation.getStartTime())
                                       .collect(Collectors.toList());
    if (isNotEmpty(annotations) && annotations.get(0).getEndTime() != annotationDTO.getEndTime()) {
      throw new IllegalStateException("Can make a single thread for the same start time");
    }
  }

  private void validateCreate(ProjectParams projectParams, AnnotationDTO annotationDTO) {
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveService.getEntity(projectParams, annotationDTO.getSloIdentifier());
    if (serviceLevelObjective == null) {
      throw new EntityNotFoundException(String.format(
          "No such SLO with identifier %s and orgIdentifier %s and projectIdentifier %s is present",
          annotationDTO.getSloIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
    }
    Preconditions.checkArgument(
        annotationDTO.getStartTime() <= annotationDTO.getEndTime(), "Start time should be greater than the end time.");
    checkIfStartTimeIsUnique(projectParams, annotationDTO);
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

  private Optional<Annotation> getOptionalAnnotation(String annotationId) {
    Annotation annotation = getEntity(annotationId);
    return Optional.ofNullable(annotation);
  }
}
