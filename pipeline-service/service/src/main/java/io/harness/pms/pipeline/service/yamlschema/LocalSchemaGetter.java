/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.common.EntityTypeConstants;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.pipeline.service.yamlschema.approval.ApprovalYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.customstage.CustomStageYamlSchemaService;
import io.harness.pms.pipeline.service.yamlschema.featureflag.FeatureFlagYamlService;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public class LocalSchemaGetter implements SchemaGetter {
  private final String accountIdentifier;
  private final ModuleType moduleType;
  private YamlSchemaProvider yamlSchemaProvider;
  private final ApprovalYamlSchemaService approvalYamlSchemaService;
  private final CustomStageYamlSchemaService customStageYamlSchemaService;
  private final FeatureFlagYamlService featureFlagYamlService;
  private final PmsYamlSchemaHelper pmsYamlSchemaHelper;

  public LocalSchemaGetter(String accountIdentifier, ModuleType moduleType, YamlSchemaProvider yamlSchemaProvider,
      ApprovalYamlSchemaService approvalYamlSchemaService, CustomStageYamlSchemaService customStageYamlSchemaService,
      FeatureFlagYamlService featureFlagYamlService, PmsYamlSchemaHelper pmsYamlSchemaHelper) {
    this.accountIdentifier = accountIdentifier;
    this.moduleType = moduleType;
    this.yamlSchemaProvider = yamlSchemaProvider;
    this.approvalYamlSchemaService = approvalYamlSchemaService;
    this.customStageYamlSchemaService = customStageYamlSchemaService;
    this.featureFlagYamlService = featureFlagYamlService;
    this.pmsYamlSchemaHelper = pmsYamlSchemaHelper;
  }

  @Override
  public List<PartialSchemaDTO> getSchema(List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    List<PartialSchemaDTO> partialSchemaDTOList = new ArrayList<>();
    partialSchemaDTOList.add(approvalYamlSchemaService.getApprovalYamlSchema(
        accountIdentifier, null, null, null, yamlSchemaWithDetailsList));
    partialSchemaDTOList.add(featureFlagYamlService.getFeatureFlagYamlSchema(
        accountIdentifier, null, null, null, yamlSchemaWithDetailsList));
    partialSchemaDTOList.add(customStageYamlSchemaService.getCustomStageYamlSchema(
        accountIdentifier, null, null, null, yamlSchemaWithDetailsList));
    return partialSchemaDTOList;
  }

  @Override
  public YamlSchemaDetailsWrapper getSchemaDetails() {
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = yamlSchemaProvider.getCrossFunctionalStepsSchemaDetails(
        null, null, null, pmsYamlSchemaHelper.getNodeEntityTypesByYamlGroup(StepCategory.STEP.name()), ModuleType.PMS);
    yamlSchemaWithDetailsList.addAll(yamlSchemaProvider.getCrossFunctionalStepsSchemaDetails(null, null, null,
        pmsYamlSchemaHelper.getNodeEntityTypesByYamlGroup(StepCategory.STAGE.name()), ModuleType.PMS));
    return YamlSchemaDetailsWrapper.builder().yamlSchemaWithDetailsList(yamlSchemaWithDetailsList).build();
  }

  @Override
  public JsonNode fetchStepYamlSchema(String orgIdentifier, String projectIdentifier, Scope scope,
      EntityType entityType, String yamlGroup, List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    if (yamlGroup.equals(StepCategory.STAGE.toString())) {
      if (entityType.getYamlName().equals(EntityTypeConstants.APPROVAL_STAGE)) {
        return approvalYamlSchemaService
            .getApprovalYamlSchema(
                accountIdentifier, projectIdentifier, orgIdentifier, scope, yamlSchemaWithDetailsList)
            .getSchema();
      } else if (entityType.getYamlName().equals(EntityTypeConstants.FEATURE_FLAG_STAGE)) {
        return featureFlagYamlService
            .getFeatureFlagYamlSchema(
                accountIdentifier, projectIdentifier, orgIdentifier, scope, yamlSchemaWithDetailsList)
            .getSchema();
      } else if (entityType.getYamlName().equals(EntityTypeConstants.CUSTOM_STAGE)) {
        return customStageYamlSchemaService
            .getCustomStageYamlSchema(
                accountIdentifier, projectIdentifier, orgIdentifier, scope, yamlSchemaWithDetailsList)
            .getSchema();
      }
      throw new InvalidRequestException(format("stage %s does not exist in module pms", entityType));
    }
    return yamlSchemaProvider.getYamlSchema(entityType, orgIdentifier, projectIdentifier, scope);
  }
}
