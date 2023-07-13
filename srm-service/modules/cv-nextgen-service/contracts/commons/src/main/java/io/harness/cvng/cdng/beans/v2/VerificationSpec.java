/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans.v2;

import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.cdng.beans.MonitoredServiceSpecType;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VerificationSpec {
  String analysedServiceIdentifier;
  String analysedEnvIdentifier;
  MonitoredServiceSpecType monitoredServiceType;
  String monitoredServiceIdentifier;
  String monitoredServiceTemplateIdentifier;
  String monitoredServiceTemplateVersionLabel;
  VerificationJobType analysisType;
  Sensitivity sensitivity;
  long durationInMinutes;
  Boolean isFailOnNoAnalysis;
  BaselineType baselineType;
}