/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.cf;

import static java.lang.String.format;
import static org.joda.time.Minutes.minutes;

import io.harness.OrchestrationStepConfig;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cf.CFApi;
import io.harness.cf.openapi.ApiException;
import io.harness.cf.openapi.model.PatchInstruction;
import io.harness.cf.openapi.model.PatchOperation;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.security.JWTTokenServiceUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.cf.AddRuleYaml.AddRuleYamlSpec;
import io.harness.steps.cf.AddSegmentToVariationTargetMapYaml.AddSegmentToVariationTargetMapYamlSpec;
import io.harness.steps.cf.AddTargetsToVariationTargetMapYaml.AddTargetsToVariationTargetMapYamlSpec;
import io.harness.steps.cf.PatchInstruction.Type;
import io.harness.steps.cf.RemoveSegmentToVariationTargetMapYaml.RemoveSegmentToVariationTargetMapYamlSpec;
import io.harness.steps.cf.RemoveTargetsToVariationTargetMapYaml.RemoveTargetsToVariationTargetMapYamlSpec;
import io.harness.steps.cf.SetDefaultVariationsYaml.SetDefaultVariationsYamlSpec;
import io.harness.steps.cf.SetFeatureFlagStateYaml.SetFeatureFlagStateYamlSpec;
import io.harness.steps.cf.SetOffVariationYaml.SetOffVariationYamlSpec;
import io.harness.steps.cf.SetOnVariationYaml.SetOnVariationYamlSpec;
import io.harness.steps.cf.UpdateRuleYaml.UpdateRuleYamlSpec;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.github.resilience4j.core.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CF)
@Slf4j
public class FlagConfigurationStep implements SyncExecutable<StepElementParameters> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.FLAG_CONFIGURATION_STEP_TYPE;
  public static final String STEP_NAME = "Flag Configuration";
  public static final String STEP_CATEGORY = "FeatureFlag";

  private static final String INFRASTRUCTURE_COMMAND_UNIT = "Execute";

  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject @Named("cfPipelineAPI") private CFApi cfApi;
  @Inject OrchestrationStepConfig config;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    // TODO need to figure out how this should work...
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    log.info("Executing feature update step..");
    long startTime = System.currentTimeMillis();
    NGLogCallback ngManagerLogCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, true);

    try {
      ngManagerLogCallback.saveExecutionLog("Starting Flag Update", LogLevel.INFO);

      // Get Org, Account and Project Data
      String accountID = ambiance.getSetupAbstractionsMap().get("accountId");
      String orgID = ambiance.getSetupAbstractionsMap().get("orgIdentifier");
      String projectID = ambiance.getSetupAbstractionsMap().get("projectIdentifier");

      FlagConfigurationStepParameters flagConfigurationStepParameters =
          (FlagConfigurationStepParameters) stepParameters.getSpec();
      String featureIdentifier = flagConfigurationStepParameters.getFeature().getValue();
      String environment = flagConfigurationStepParameters.getEnvironment().getValue();

      ngManagerLogCallback.saveExecutionLog(format("updating Feature flag %s", featureIdentifier), LogLevel.INFO);

      List<PatchInstruction> instructions = new ArrayList<>();

      // Check that the parameter field is not null.  Error if it is.
      if (ParameterField.isNull(flagConfigurationStepParameters.getInstructions())) {
        throw new InvalidRequestException("the flag instructions are null");
      }

      for (io.harness.steps.cf.PatchInstruction patchInstruction :
          flagConfigurationStepParameters.getInstructions().getValue()) {
        if (patchInstruction.getType().equals(Type.SET_FEATURE_FLAG_STATE)) {
          SetFeatureFlagStateYamlSpec spec = ((SetFeatureFlagStateYaml) patchInstruction).getSpec();
          PatchInstruction instruction =
              cfApi.getFeatureFlagOnPatchInstruction(parseStateAsBoolean(spec.getState().getValue()));
          instructions.add(instruction);
          ngManagerLogCallback.saveExecutionLog(
              format("setting flag state to %s", spec.getState().getValue()), LogLevel.INFO);
        }

        if (patchInstruction.getType().equals(Type.SET_ON_VARIATION)) {
          SetOnVariationYamlSpec spec = ((SetOnVariationYaml) patchInstruction).getSpec();
          instructions.add(cfApi.setOnVariation(spec.getVariation().getValue()));
          ngManagerLogCallback.saveExecutionLog(
              format("setting On variation for flag to %s", spec.getVariation().getValue()), LogLevel.INFO);
        }

        if (patchInstruction.getType().equals(Type.SET_OFF_VARIATION)) {
          SetOffVariationYamlSpec spec = ((SetOffVariationYaml) patchInstruction).getSpec();
          instructions.add(cfApi.setOffVariation(spec.getVariation().getValue()));
          ngManagerLogCallback.saveExecutionLog(
              format("setting Off variation for flag to %s", spec.getVariation().getValue()), LogLevel.INFO);
        }

        if (patchInstruction.getType().equals(Type.SET_DEFAULT_VARIATIONS)) {
          SetDefaultVariationsYamlSpec spec = ((SetDefaultVariationsYaml) patchInstruction).getSpec();
          String on = spec.getOn().getValue();
          if (StringUtils.isNotEmpty(on)) {
            String logStr = format("setting On variation for flag to %s", on);
            log.debug(logStr);
            instructions.add(cfApi.setOnVariation(on));
            ngManagerLogCallback.saveExecutionLog(logStr, LogLevel.INFO);
          }

          String off = spec.getOff().getValue();
          if (StringUtils.isNotEmpty(off)) {
            String logStr = format("setting Off variation for flag to %s", on);
            log.debug(logStr);
            instructions.add(cfApi.setOffVariation(off));
            ngManagerLogCallback.saveExecutionLog(logStr, LogLevel.INFO);
          }
        }

        if (patchInstruction.getType().equals(Type.ADD_RULE)) {
          AddRuleYamlSpec spec = ((AddRuleYaml) patchInstruction).getSpec();
          String identifier = ((AddRuleYaml) patchInstruction).getIdentifier();
          instructions.add(addRule(spec, accountID, orgID, projectID, featureIdentifier, environment, identifier));
          ngManagerLogCallback.saveExecutionLog(
              format("adding rule %s to flag", ((AddRuleYaml) patchInstruction).getIdentifier()), LogLevel.INFO);
        }

        if (patchInstruction.getType().equals(Type.UPDATE_RULE)) {
          UpdateRuleYamlSpec spec = ((UpdateRuleYaml) patchInstruction).getSpec();
          PatchInstruction instruction = cfApi.updatePercentageRollout(spec.getRuleID().getValue(), spec.getServe());
          instructions.add(instruction);
          ngManagerLogCallback.saveExecutionLog(format("updating rule for flag"), LogLevel.INFO);
        }

        if (patchInstruction.getType().equals(Type.ADD_TARGETS_TO_VARIATION_TARGET_MAP)) {
          if (patchInstruction instanceof AddTargetsToVariationTargetMapYaml) {
            AddTargetsToVariationTargetMapYamlSpec spec =
                ((AddTargetsToVariationTargetMapYaml) patchInstruction).getSpec();
            PatchInstruction instruction =
                cfApi.getAddTargetToVariationMapParams(spec.getVariation().getValue(), spec.getTargets().getValue());
            instructions.add(instruction);
          }
        }

        if (patchInstruction.getType().equals(Type.REMOVE_TARGETS_TO_VARIATION_TARGET_MAP)) {
          if (patchInstruction instanceof RemoveTargetsToVariationTargetMapYaml) {
            RemoveTargetsToVariationTargetMapYamlSpec spec =
                ((RemoveTargetsToVariationTargetMapYaml) patchInstruction).getSpec();
            PatchInstruction instruction =
                cfApi.getRemoveTargetToVariationMapParams(spec.getVariation().getValue(), spec.getTargets().getValue());
            instructions.add(instruction);
          }
        }

        if (patchInstruction.getType().equals(Type.ADD_SEGMENT_TO_VARIATION_TARGET_MAP)) {
          if (patchInstruction instanceof AddSegmentToVariationTargetMapYaml) {
            AddSegmentToVariationTargetMapYamlSpec spec =
                ((AddSegmentToVariationTargetMapYaml) patchInstruction).getSpec();
            PatchInstruction instruction =
                cfApi.getAddSegmentToVariationMapParams(spec.getVariation().getValue(), spec.getSegments().getValue());
            instructions.add(instruction);
          }
        }

        if (patchInstruction.getType().equals(Type.REMOVE_SEGMENT_TO_VARIATION_TARGET_MAP)) {
          if (patchInstruction instanceof RemoveSegmentToVariationTargetMapYaml) {
            RemoveSegmentToVariationTargetMapYamlSpec spec =
                ((RemoveSegmentToVariationTargetMapYaml) patchInstruction).getSpec();
            PatchInstruction instruction =
                cfApi.getAddSegmentToVariationMapParams(spec.getVariation().getValue(), spec.getSegments().getValue());
            instructions.add(instruction);
          }
        }
      }

      PatchOperation patchOperation = PatchOperation.builder().instructions(instructions).build();

      addApiKeyHeader(cfApi);

      // cfApi.

      cfApi.patchFeature(featureIdentifier, accountID, orgID, projectID,
          flagConfigurationStepParameters.getEnvironment().getValue(), patchOperation);

      ngManagerLogCallback.saveExecutionLog(format("Update of Feature flag %s completed", featureIdentifier),
          LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    } catch (ApiException e) {
      String errStr = format("API error while updating flag because %s : response [%s] code: %d", e.getMessage(),
          e.getResponseBody(), e.getCode());
      log.error(errStr);
      return StepResponse.builder()
          .status(Status.ERRORED)
          .failureInfo(FailureInfo.newBuilder().setErrorMessage(errStr).build())
          .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                          .setUnitName(INFRASTRUCTURE_COMMAND_UNIT)
                                                          .setStatus(UnitStatus.FAILURE)
                                                          .setStartTime(startTime)
                                                          .setEndTime(System.currentTimeMillis())
                                                          .build()))
          .build();

    } catch (Exception e) {
      log.error(format("error updating flag because %s", e.getMessage()));
      return StepResponse.builder()
          .status(Status.ERRORED)
          .failureInfo(FailureInfo.newBuilder().setErrorMessage(e.getMessage()).build())
          .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                          .setUnitName(INFRASTRUCTURE_COMMAND_UNIT)
                                                          .setStatus(UnitStatus.FAILURE)
                                                          .setStartTime(startTime)
                                                          .setEndTime(System.currentTimeMillis())
                                                          .build()))
          .build();
    }

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName(INFRASTRUCTURE_COMMAND_UNIT)
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }

  public StepType getType() {
    return STEP_TYPE;
  }

  private void addApiKeyHeader(CFApi cfApi) {
    String apiKey = JWTTokenServiceUtils.generateJWTToken(ImmutableMap.of("type", "APIKey", "name", "PIPELINE-SERVICE"),
        minutes(10).toStandardDuration().getMillis(), config.getFfServerApiKey());
    cfApi.getApiClient().addDefaultHeader("api-key", "Bearer " + apiKey);
  }

  /**
   * Converts the flag state off|on to a boolean value
   * i.e. off = false, on = true.
   * @param state indicates the state of the flag on | off
   * @return true if flag state is on
   */
  private static boolean parseStateAsBoolean(String state) {
    return (state != null) && state.equalsIgnoreCase("on");
  }

  private String generateRuleUUID(
      String accountID, String orgID, String projectID, String featureID, String environmentID, String ruleID) {
    String aString = String.join(accountID, orgID, projectID, featureID, environmentID, ruleID);
    return UUID.nameUUIDFromBytes(aString.getBytes()).toString();
  }

  private PatchInstruction addRule(AddRuleYamlSpec rule, String accountID, String orgID, String projectID,
      String featureID, String environmentID, String ruleID) {
    Integer priority = 1;
    if (ParameterField.isNull(rule.getPriority()) != true) {
      priority = rule.getPriority().getValue();
    }

    // Generate a UUID
    log.info(format("Creating UUID From Account:%s\nOrgID:%s\nProjectID:%s\nFeatureID:%s\nEnvironmentID:%s\nRuleID:%s",
        accountID, orgID, projectID, featureID, environmentID, ruleID));
    String uuid = generateRuleUUID(accountID, orgID, projectID, featureID, environmentID, ruleID);

    return cfApi.addPercentageRollout(uuid, priority, rule.getServe(), rule.getClauses());
  }
}
