/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.assessment.settings.services;

import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadRequest;
import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadResponse;

public interface AssessmentUploadService {
  AssessmentUploadResponse uploadNewAssessment(AssessmentUploadRequest assessmentUploadRequest);

  AssessmentUploadResponse updateAssessment(AssessmentUploadRequest assessmentUploadRequest);

  AssessmentUploadResponse publishAssessment(String assessmentId);

  AssessmentUploadResponse getAssessment(String assessmentId);
}
