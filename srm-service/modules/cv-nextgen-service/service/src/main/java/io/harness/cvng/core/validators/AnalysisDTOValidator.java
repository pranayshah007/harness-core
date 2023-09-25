/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.validators;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import lombok.extern.slf4j.Slf4j;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
@Slf4j
public class AnalysisDTOValidator implements ConstraintValidator<AnalysisDTOCheck, Object> {
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
            HealthSourceMetricDefinition.AnalysisDTO analysisDTO = (HealthSourceMetricDefinition.AnalysisDTO) o;
            if (!analysisDTO.getLiveMonitoring().getEnabled() && !analysisDTO.getDeploymentVerification().getEnabled()) {
                return true;
            }
            return analysisDTO.getRiskProfile().getThresholdTypes() != null && !analysisDTO.getRiskProfile().getThresholdTypes().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
