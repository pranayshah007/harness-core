package io.harness.cvng.statemachine.beans;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants(innerTypeName = "AnalysisInputKeys")
@Builder
public class CanaryAnalysisInput extends AnalysisInput {}
