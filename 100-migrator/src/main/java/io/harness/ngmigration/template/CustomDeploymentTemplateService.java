/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.template;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.executions.steps.StepSpecTypeConstants.DEPLOYMENT_TYPE_CUSTOM_DEPLOYMENT;
import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.creator.plan.customDeployment.CustomDeploymentInstanceAttributes;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.plancreator.customDeployment.CustomDeploymentExecutionConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.template.Template;
import software.wings.beans.template.deploymenttype.CustomDeploymentTypeTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class CustomDeploymentTemplateService implements NgTemplateService {
  @Override
  public Set<String> getExpressions(Template template) {
    CustomDeploymentTypeTemplate customDeploymentTypeTemplate =
        (CustomDeploymentTypeTemplate) template.getTemplateObject();
    if (StringUtils.isBlank(customDeploymentTypeTemplate.getFetchInstanceScript())) {
      return Collections.emptySet();
    }
    return MigratorExpressionUtils.extractAll(customDeploymentTypeTemplate.getFetchInstanceScript());
  }

  @Override
  public TemplateEntityType getTemplateEntityType() {
    return TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE;
  }

  @Override
  public boolean isMigrationSupported() {
    return true;
  }

  @Override
  public JsonNode getNgTemplateConfigSpec(
      MigrationContext context, Template template, String orgIdentifier, String projectIdentifier) {
    CustomDeploymentTypeTemplate customDeploymentTypeTemplate =
        (CustomDeploymentTypeTemplate) template.getTemplateObject();

    List<Map<String, String>> variables = new ArrayList<>();
    if (isNotEmpty(template.getVariables())) {
      template.getVariables().forEach(variable -> {
        variables.add(ImmutableMap.of("name", valueOrDefaultEmpty(variable.getName()), "type", "String", "value",
            valueOrDefaultRuntime(variable.getValue()), "description", valueOrDefaultEmpty(variable.getDescription())));
      });
    }

    StoreConfigWrapper storeConfigWrapper = null;
    if (isNotEmpty(customDeploymentTypeTemplate.getFetchInstanceScript())) {
      storeConfigWrapper =
          StoreConfigWrapper.builder()
              .type(StoreConfigType.INLINE)
              .spec(InlineStoreConfig.builder()
                        .content(ParameterField.createValueField(customDeploymentTypeTemplate.getFetchInstanceScript()))
                        .build())
              .build();
    }

    String instanceName = PLEASE_FIX_ME;
    List<CustomDeploymentInstanceAttributes> attributes = new ArrayList<>();
    if (isNotEmpty(customDeploymentTypeTemplate.getHostAttributes())) {
      customDeploymentTypeTemplate.getHostAttributes()
          .entrySet()
          .stream()
          .filter(e -> StringUtils.isNoneBlank(e.getKey(), e.getValue()))
          .forEach(e
              -> attributes.add(
                  CustomDeploymentInstanceAttributes.builder().name(e.getKey()).jsonPath(e.getValue()).build()));
      instanceName = customDeploymentTypeTemplate.getHostAttributes()
                         .entrySet()
                         .stream()
                         .filter(e -> StringUtils.isNoneBlank(e.getKey(), e.getValue()))
                         .filter(e -> e.getKey().equals("hostname"))
                         .map(Entry::getValue)
                         .findFirst()
                         .orElse(PLEASE_FIX_ME);
    }
    attributes.add(CustomDeploymentInstanceAttributes.builder().name("instancename").jsonPath(instanceName).build());

    Builder<String, Object> infrastructureSpec =
        ImmutableMap.<String, Object>builder()
            .put("variables", variables)
            .put("instanceAttributes", attributes)
            .put("instancesListPath", customDeploymentTypeTemplate.getHostObjectArrayPath());

    if (storeConfigWrapper != null) {
      infrastructureSpec.put(
          "fetchInstancesScript", ImmutableMap.<String, Object>builder().put("store", storeConfigWrapper).build());
    }

    return JsonUtils.asTree(
        ImmutableMap.<String, Object>builder()
            .put("infrastructure", JsonPipelineUtils.asTree(infrastructureSpec.build()))
            .put("execution", CustomDeploymentExecutionConfig.builder().stepTemplateRefs(new ArrayList<>()).build())
            .build());
  }

  @Override
  public String getNgTemplateStepName(Template template) {
    return DEPLOYMENT_TYPE_CUSTOM_DEPLOYMENT;
  }

  @Override
  public String getTimeoutString(Template template) {
    return "60s";
  }

  static String valueOrDefaultEmpty(String val) {
    return StringUtils.isNotBlank(val) ? val : "";
  }

  static String valueOrDefaultRuntime(String val) {
    return StringUtils.isNotBlank(val) ? val : "<+input>";
  }
}
