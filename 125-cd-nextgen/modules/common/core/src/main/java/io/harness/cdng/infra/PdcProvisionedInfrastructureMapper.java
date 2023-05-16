/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.cdng.ssh.SshWinRmConstants.HOSTNAME_HOST_ATTRIBUTE;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.host.HostAttributesFilter;
import io.harness.cdng.infra.beans.host.HostFilter;
import io.harness.cdng.infra.beans.host.HostFilterSpec;
import io.harness.cdng.infra.beans.host.dto.AllHostsFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostAttributesFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostFilterDTO;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.evaluators.ProvisionerExpressionEvaluator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.steps.environment.EnvironmentOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class PdcProvisionedInfrastructureMapper {
  public PdcInfrastructureOutcome toOutcome(PdcInfrastructure pdcInfrastructure,
      ProvisionerExpressionEvaluator expressionEvaluator, EnvironmentOutcome environmentOutcome,
      ServiceStepOutcome service) {
    List<Map<String, Object>> evaluatedHostObjectsAttrs =
        evaluateHostInstancesAttrs(expressionEvaluator, getParameterFieldValue(pdcInfrastructure.getHostArrayPath()),
            getParameterFieldValue(pdcInfrastructure.getHostAttributes()));

    List<String> hosts = getHostNames(evaluatedHostObjectsAttrs);
    PdcInfrastructureOutcome pdcInfrastructureOutcome =
        PdcInfrastructureOutcome.builder()
            .credentialsRef(getParameterFieldValue(pdcInfrastructure.getCredentialsRef()))
            .hosts(hosts)
            .hostFilter(toHostFilterDTO(pdcInfrastructure.getHostFilter()))
            .environment(environmentOutcome)
            .infrastructureKey(
                InfrastructureKey.generate(service, environmentOutcome, pdcInfrastructure.getInfrastructureKeyValues()))
            .hostsAttributes(evaluatedHostObjectsAttrs)
            .dynamicallyProvisioned(true)
            .build();

    setInfraIdentifierAndName(
        pdcInfrastructureOutcome, pdcInfrastructure.getInfraIdentifier(), pdcInfrastructure.getInfraName());
    return pdcInfrastructureOutcome;
  }

  private List<Map<String, Object>> evaluateHostInstancesAttrs(
      ProvisionerExpressionEvaluator provisionerExpressionEvaluator, String hostArrayPath,
      Map<String, String> hostAttributes) {
    List<Map<String, Object>> hostObjects =
        (List<Map<String, Object>>) provisionerExpressionEvaluator.evaluateExpression(
            hostArrayPath, ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
    if (isEmpty(hostObjects)) {
      throw new InvalidRequestException("Cannot evaluate empty host object array");
    }

    List<Map<String, Object>> evaluatedHostAttributes = new ArrayList<>();
    for (Object hostObject : hostObjects) {
      evaluatedHostAttributes.add(
          provisionerExpressionEvaluator.evaluateProperties(hostAttributes, (Map<String, Object>) hostObject));
    }

    return evaluatedHostAttributes;
  }

  private List<String> getHostNames(List<Map<String, Object>> evaluatedHostObjectsAttributes) {
    return evaluatedHostObjectsAttributes.stream()
        .map(evaluatedHostAttributes -> (String) evaluatedHostAttributes.get(HOSTNAME_HOST_ATTRIBUTE))
        .collect(Collectors.toList());
  }

  private HostFilterDTO toHostFilterDTO(HostFilter hostFilter) {
    if (hostFilter == null) {
      return HostFilterDTO.builder().spec(AllHostsFilterDTO.builder().build()).type(HostFilterType.ALL).build();
    }

    HostFilterType type = hostFilter.getType();
    HostFilterSpec spec = hostFilter.getSpec();
    if (type == HostFilterType.HOST_ATTRIBUTES) {
      return HostFilterDTO.builder()
          .spec(HostAttributesFilterDTO.builder().value(((HostAttributesFilter) spec).getValue()).build())
          .type(type)
          .build();
    } else if (type == HostFilterType.ALL) {
      return HostFilterDTO.builder().spec(AllHostsFilterDTO.builder().build()).type(type).build();
    } else {
      throw new InvalidArgumentsException(
          format("Unsupported host filter type found for dynamically provisioned infrastructure: %s", type));
    }
  }

  private void setInfraIdentifierAndName(
      PdcInfrastructureOutcome infrastructureOutcome, String infraIdentifier, String infraName) {
    infrastructureOutcome.setInfraIdentifier(infraIdentifier);
    infrastructureOutcome.setInfraName(infraName);
    infrastructureOutcome.setName(infraName);
  }
}
