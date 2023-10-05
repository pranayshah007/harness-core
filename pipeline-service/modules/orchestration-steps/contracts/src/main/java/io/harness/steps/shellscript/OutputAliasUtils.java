/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.validation.constraints.NotBlank;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class OutputAliasUtils {
  private static final String UNDERSCORE = "_";
  private static final String DOT = "\\.";
  private static final String DUPLICATE_KEY_EXCEPTION = "DuplicateKeyException";
  public static final String EXPECTED_FORMAT = "scope.aliasKey.variableName";
  public static final List<String> ALLOWED_SCOPES =
      List.of(YAMLFieldNameConstants.PIPELINE, YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.STEP_GROUP);

  /**
   * 1-way utility which returns same result for same input parameter always
   * @param userAlias user defined alias to be encoded
   * @return the encoded alias after encoding the modified alias using murmur3_32 and removing special characters
   */
  public static String generateSweepingOutputKeyUsingUserAlias(String userAlias, Ambiance ambiance) {
    if (StringUtils.isBlank(userAlias)) {
      throw new IllegalArgumentException("Blank value provided for output alias. Please provide a valid value.");
    }
    String toBeEncrypted = String.format("%s%s%s", ambiance.getExpressionFunctorToken(), UNDERSCORE, userAlias);
    String uuid = Hashing.murmur3_32_fixed().hashString(toBeEncrypted, StandardCharsets.UTF_8).toString();
    // removing special characters so uuid is considered as single key in expression engine
    uuid = uuid.replaceAll(AmbianceUtils.SPECIAL_CHARACTER_REGEX, "");
    log.debug("UUID [{}] generated from userAlias {} for OutputAlias", uuid, userAlias);
    return uuid;
  }

  public static boolean isDuplicateKeyException(Exception ex) {
    return ex instanceof GeneralException && StringUtils.isNotBlank(ex.getMessage())
        && ex.getMessage().startsWith(DUPLICATE_KEY_EXCEPTION);
  }

  private boolean validateScopeString(String scope) {
    if (StringUtils.isBlank(scope)) {
      log.warn("Empty scope string specified");
      return false;
    }
    if (!ALLOWED_SCOPES.contains(scope)) {
      log.warn(String.format("Invalid scope string specified %s, must be one of %s", scope, ALLOWED_SCOPES));
      return false;
    }
    return true;
  }

  public boolean validateExpressionFormat(@NotBlank String exportExpression) {
    String[] exportExpressionSplit = exportExpression.split(DOT);
    if (exportExpressionSplit.length < 2 || exportExpressionSplit.length > 3) {
      log.warn(
          "Invalid format of export expression specified {}, expected format: {}", exportExpression, EXPECTED_FORMAT);
      return false;
    }
    if (StringUtils.isBlank(exportExpressionSplit[1])) {
      log.warn("Empty output alias key specified");
      return false;
    }
    String scope = exportExpressionSplit[0];
    return validateScopeString(scope);
  }

  /**
   * Maps yaml constant for scope to step outcome group name
   */
  public String toStepOutcomeGroup(String scopeConstant) {
    if (StringUtils.isBlank(scopeConstant)) {
      throw new InvalidRequestException("Empty scope constant provided, can't be mapped to step outcome.");
    }
    switch (scopeConstant) {
      case YAMLFieldNameConstants.PIPELINE:
        return StepOutcomeGroup.PIPELINE.name();
      case YAMLFieldNameConstants.STAGE:
        return StepOutcomeGroup.STAGE.name();
      case YAMLFieldNameConstants.STEP_GROUP:
        return StepOutcomeGroup.STEP_GROUP.name();
      default:
        throw new InvalidRequestException(String.format("Unsupported scope constant value : %s", scopeConstant));
    }
  }
}
