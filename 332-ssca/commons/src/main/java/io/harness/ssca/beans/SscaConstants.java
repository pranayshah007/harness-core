/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

@OwnedBy(HarnessTeam.SSCA)
public interface SscaConstants {
  String SSCA_ORCHESTRATION_STEP_NODE = "SscaOrchestrationStepNode";
  String SSCA_ORCHESTRATION_STEP = "SscaOrchestration";

  String CD_SSCA_ORCHESTRATION = "CdSscaOrchestration";
  String CD_SSCA_ORCHESTRATION_STEP_NODE = "CdSscaOrchestrationStepNode";
  String SSCA_STEPS_FOLDER_NAME = "Supply Chain Assurance (Build Steps)";

  String SSCA_STEPS_FOLDER_NAME_CD = "Supply Chain Assurance (Deploy Steps)";

  String SSCA_ENFORCEMENT_STEP_NODE = "SscaEnforcementStepNode";
  String SSCA_ENFORCEMENT = "SscaEnforcement";
  String CD_SSCA_ENFORCEMENT = "CdSscaEnforcement";
  String CD_SSCA_ENFORCEMENT_STEP_NODE = "CdSscaEnforcementStepNode";

  String SLSA_PROVENANCE = "provenance";
  String SLSA_PROVENANCE_NODE = "provenanceStepNode";

  String SLSA_VERIFICATION = "SlsaVerification";
  String SLSA_VERIFICATION_STEP_NODE = "SlsaVerificationStepNode";

  StepType SSCA_ORCHESTRATION_STEP_TYPE =
      StepType.newBuilder().setType(SSCA_ORCHESTRATION_STEP).setStepCategory(StepCategory.STEP).build();

  StepType CD_SSCA_ORCHESTRATION_STEP_TYPE =
      StepType.newBuilder().setType(CD_SSCA_ORCHESTRATION).setStepCategory(StepCategory.STEP).build();

  StepType SSCA_ENFORCEMENT_STEP_TYPE =
      StepType.newBuilder().setType(SSCA_ENFORCEMENT).setStepCategory(StepCategory.STEP).build();
  StepType CD_SSCA_ENFORCEMENT_STEP_TYPE =
      StepType.newBuilder().setType(CD_SSCA_ENFORCEMENT).setStepCategory(StepCategory.STEP).build();

  StepType PROVENANCE_STEP_TYPE =
      StepType.newBuilder().setType(SLSA_PROVENANCE).setStepCategory(StepCategory.STEP).build();
  StepType SLSA_VERIFICATION_STEP_TYPE =
      StepType.newBuilder().setType(SLSA_VERIFICATION).setStepCategory(StepCategory.STEP).build();

  String PREDICATE_TYPE = "https://slsa.dev/provenance/v1";
}
