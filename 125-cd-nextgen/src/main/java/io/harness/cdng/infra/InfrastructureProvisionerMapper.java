/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.cdng.ssh.SshWinRmConstants.HOSTNAME_HOST_ATTRIBUTE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.host.HostAttributesFilter;
import io.harness.cdng.infra.beans.host.HostFilter;
import io.harness.cdng.infra.beans.host.HostFilterSpec;
import io.harness.cdng.infra.beans.host.dto.AllHostsFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostAttributesFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostFilterDTO;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.evaluators.CDExpressionEvaluator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.steps.environment.EnvironmentOutcome;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class InfrastructureProvisionerMapper {
  @Inject private CDExpressionEvaluator evaluator;

  @NotNull
  public InfrastructureOutcome toOutcome(
      @Nonnull Infrastructure infrastructure, EnvironmentOutcome environmentOutcome, ServiceStepOutcome service) {
    if (InfrastructureKind.PDC.equals(infrastructure.getKind())) {
      PdcInfrastructure pdcInfrastructure = (PdcInfrastructure) infrastructure;
      validateHostAttributes(pdcInfrastructure.getHostAttributes().getValue());
      List<Map<String, Object>> hostObjects = parseHostInstancesJSON(pdcInfrastructure.getHostObjectArray().getValue());
      validateHostObjects(hostObjects);
      Map<String, String> hostAttributes = fixHostAttributes(pdcInfrastructure.getHostAttributes().getValue());

      List<Map<String, Object>> evaluatedHostObjectsAttributes =
          evaluateHostObjectsAttributes(hostObjects, hostAttributes);
      List<String> hosts = getHostNames(evaluatedHostObjectsAttributes);

      PdcInfrastructureOutcome pdcInfrastructureOutcome =
          PdcInfrastructureOutcome.builder()
              .credentialsRef(ParameterFieldHelper.getParameterFieldValue(pdcInfrastructure.getCredentialsRef()))
              .hosts(hosts)
              .hostFilter(toHostFilterDTO(pdcInfrastructure.getHostFilter()))
              .environment(environmentOutcome)
              .infrastructureKey(InfrastructureKey.generate(
                  service, environmentOutcome, pdcInfrastructure.getInfrastructureKeyValues()))
              .hostsAttributes(evaluatedHostObjectsAttributes)
              .dynamicallyProvisioned(true)
              .build();

      pdcInfrastructureOutcome.setInfraName(pdcInfrastructure.getInfraName());
      pdcInfrastructureOutcome.setInfraIdentifier(pdcInfrastructure.getInfraIdentifier());
      return pdcInfrastructureOutcome;
    }
    throw new InvalidArgumentsException(format("Unsupported Infrastructure Kind : [%s]", infrastructure.getKind()));
  }

  private List<Map<String, Object>> parseHostInstancesJSON(final String hostInstancesJson) {
    if (isEmpty(hostInstancesJson)) {
      throw new InvalidArgumentsException("Host object array JSON cannot be null or empty");
    }

    try {
      TypeReference<List<Map<String, Object>>> typeRef = new TypeReference<>() {};
      return new ObjectMapper().readValue(IOUtils.toInputStream(hostInstancesJson), typeRef);
    } catch (IOException ex) {
      log.error("Unable to parse host object array  JSON", ex);
      throw new InvalidRequestException("Host object array JSON cannot be parsed", ex);
    }
  }

  private void validateHostObjects(List<Map<String, Object>> hostObjects) {
    if (isEmpty(hostObjects)) {
      throw new InvalidRequestException("Cannot evaluate empty host object array");
    }
  }

  private void validateHostAttributes(Map<String, String> hostAttributes) {
    if (!hostAttributes.containsKey(HOSTNAME_HOST_ATTRIBUTE)) {
      throw new InvalidRequestException(
          format("[%s] property is mandatory for all host objects", HOSTNAME_HOST_ATTRIBUTE));
    }

    if (isEmpty(hostAttributes.get(HOSTNAME_HOST_ATTRIBUTE))) {
      throw new InvalidRequestException(format("[%s] property value cannot be null or empty", HOSTNAME_HOST_ATTRIBUTE));
    }
  }

  private Map<String, String> fixHostAttributes(@NotNull Map<String, String> hostAttributes) {
    if (isEmpty(hostAttributes)) {
      return hostAttributes;
    }

    hostAttributes.remove("__uuid");
    hostAttributes.replaceAll((key, oldValue) -> EngineExpressionEvaluator.createExpression(oldValue));
    return hostAttributes;
  }

  private List<Map<String, Object>> evaluateHostObjectsAttributes(
      List<Map<String, Object>> hostObjects, Map<String, String> hostAttributes) {
    if (isEmpty(hostAttributes) || isEmpty(hostObjects)) {
      return Collections.emptyList();
    }

    List<Map<String, Object>> evaluatedHostAttributes = new ArrayList<>();
    for (Object hostObject : hostObjects) {
      evaluatedHostAttributes.add(evaluator.evaluateProperties(hostAttributes, (Map<String, Object>) hostObject));
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
}
