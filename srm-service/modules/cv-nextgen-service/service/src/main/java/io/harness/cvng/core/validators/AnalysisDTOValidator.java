/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.validators;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalysisDTOValidator implements ConstraintValidator<AnalysisDTOCheck, Object> {
  private static final Logger logger = LoggerFactory.getLogger(AnalysisDTOValidator.class);
  @Override
  public void initialize(AnalysisDTOCheck constraintAnnotation) {
    ConstraintValidator.super.initialize(constraintAnnotation);
  }

  @Override
  public boolean isValid(Object o, ConstraintValidatorContext constraintValidatorContext) {
    if (o == null) {
      return false;
    }
    try {
      if (!(o instanceof HealthSourceMetricDefinition.AnalysisDTO)) {
        return false;
      }
      HealthSourceMetricDefinition.AnalysisDTO analysisDTO = (HealthSourceMetricDefinition.AnalysisDTO) o;
      if (analysisDTO.getLiveMonitoring() == null) {
        logger.error("Live Monitoring config object is NULL, analysisDTO: {}", analysisDTO);
        return false;
      }
      if (analysisDTO.getDeploymentVerification() == null) {
        logger.error("Continuous Verification config object is NULL, analysisDTO: {}", analysisDTO);
        return false;
      }
      if (analysisDTO.getRiskProfile() == null) {
        logger.error("Risk Profile config object is NULL, analysisDTO: {}", analysisDTO);
        return false;
      }
      if (!analysisDTO.getLiveMonitoring().getEnabled() && !analysisDTO.getDeploymentVerification().getEnabled()) {
        return true;
      }
      return analysisDTO.getRiskProfile().getThresholdTypes() != null
          && !analysisDTO.getRiskProfile().getThresholdTypes().isEmpty();
    } catch (Exception e) {
      logger.error("Unknown Exception caught while validating analysisDTO object for Monitored-Service", e);
      return false;
    }
  }
}
