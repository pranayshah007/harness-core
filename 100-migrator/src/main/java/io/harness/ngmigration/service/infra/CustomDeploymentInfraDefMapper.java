/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.customdeployment.CustomDeploymentNGVariable;
import io.harness.cdng.customdeployment.CustomDeploymentNGVariableType;
import io.harness.cdng.customdeployment.CustomDeploymentStringNGVariable;
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.NameValuePair;
import software.wings.beans.Variable;
import software.wings.beans.template.Template;
import software.wings.infra.CustomInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class CustomDeploymentInfraDefMapper implements InfraDefMapper {
  @Override
  public ServiceDefinitionType getServiceDefinition(InfrastructureDefinition infrastructureDefinition) {
    return ServiceDefinitionType.CUSTOM_DEPLOYMENT;
  }

  @Override
  public InfrastructureType getInfrastructureType(InfrastructureDefinition infrastructureDefinition) {
    return InfrastructureType.CUSTOM_DEPLOYMENT;
  }

  @Override
  public Infrastructure getSpec(MigrationContext migrationContext, InfrastructureDefinition infrastructureDefinition,
      List<ElastigroupConfiguration> elastigroupConfiguration) {
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    CustomInfrastructure infrastructure = (CustomInfrastructure) infrastructureDefinition.getInfrastructure();
    CgEntityId cgEntityId = CgEntityId.builder()
                                .type(NGMigrationEntityType.TEMPLATE)
                                .id(infrastructureDefinition.getDeploymentTypeTemplateId())
                                .build();
    NgEntityDetail ngEntityDetail = migratedEntities.get(cgEntityId).getNgEntityDetail();
    Template template = (Template) (entities.get(cgEntityId).getEntity());
    List<CustomDeploymentNGVariable> variables = new ArrayList<>();
    List<NameValuePair> variablesFromInfra = infrastructure.getInfraVariables();
    Set<String> keysAdded = new HashSet<>();
    if (isNotEmpty(variablesFromInfra)) {
      variablesFromInfra.forEach(vp -> {
        variables.add(CustomDeploymentStringNGVariable.builder()
                          .name(vp.getName())
                          .type(CustomDeploymentNGVariableType.STRING)
                          .value(ParameterField.createValueField(StringUtils.defaultIfBlank(vp.getValue(), "")))
                          .build());
        keysAdded.add(vp.getName());
      });
    }
    List<Variable> variablesFromTemplate = template.getVariables();
    if (isNotEmpty(variablesFromTemplate)) {
      variablesFromTemplate.stream()
          .filter(v -> !keysAdded.contains(v.getName()))
          .forEach(vp
              -> variables.add(
                  CustomDeploymentStringNGVariable.builder()
                      .name(vp.getName())
                      .type(CustomDeploymentNGVariableType.STRING)
                      .value(ParameterField.createValueField(StringUtils.defaultIfBlank(vp.getValue(), "")))
                      .build()));
    }

    return CustomDeploymentInfrastructure.builder()
        .customDeploymentRef(StepTemplateRef.builder()
                                 .templateRef(MigratorUtility.getIdentifierWithScope(ngEntityDetail))
                                 .versionLabel("__STABLE__")
                                 .build())
        .variables(variables)
        .build();
  }
}
