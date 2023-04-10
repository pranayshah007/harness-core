/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.MigratorUtility.containsCgExpressions;
import static io.harness.ngmigration.utils.NGMigrationConstants.RUNTIME_INPUT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.infra.AzureKubernetesService;

import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
public class InfraDefMapperUtils {
  public static K8sAzureInfrastructure buildK8sAzureInfrastructure(
      MigrationContext context, AzureKubernetesService aks, NgEntityDetail connectorDetail) {
    String releaseName = (String) MigratorExpressionUtils.render(
        context, aks.getReleaseName(), context.getInputDTO().getCustomExpressions());
    return K8sAzureInfrastructure.builder()
        .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
        .subscriptionId(ParameterField.createValueField(aks.getSubscriptionId()))
        .resourceGroup(ParameterField.createValueField(aks.getResourceGroup()))
        .cluster(ParameterField.createValueField(aks.getClusterName()))
        .namespace(ParameterField.createValueField(aks.getNamespace()))
        .releaseName(ParameterField.createValueField(releaseName))
        .build();
  }

  public static ParameterField<String> getExpression(
      Map<String, String> expressions, String field, String defaultValue, String provisionerId) {
    String value = getValueFromExpression(expressions, field, defaultValue, provisionerId);

    return ParameterField.createValueField(value);
  }

  public static String getValueFromExpression(
      Map<String, String> expressions, String field, String defaultValue, String provisionerId) {
    String value = RUNTIME_INPUT;

    if (isNotEmpty(provisionerId) && isNotEmpty(field) && isNotEmpty(expressions) && expressions.containsKey(field)) {
      value = expressions.get(field);
    } else if (isNotEmpty(defaultValue)) {
      value = defaultValue;
    }

    if (containsCgExpressions(value)) {
      value = RUNTIME_INPUT;
    }
    return value;
  }
}
