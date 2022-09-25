/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.common.ParameterFieldHelper.hasValueOrExpression;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.expressionEvaluator.CDEngineExpressionEvaluator;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.host.HostFilter;
import io.harness.cdng.infra.beans.host.HostFilterSpec;
import io.harness.cdng.infra.beans.host.HostNamesFilter;
import io.harness.cdng.infra.beans.host.dto.AllHostsFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostNamesFilterDTO;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
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
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class InfrastructureProvisionerMapper {
  private static final String HOSTNAME_HOST_ATTRIBUTE = "hostname";

  @Inject private CDEngineExpressionEvaluator evaluator;

  @NotNull
  public InfrastructureOutcome toOutcome(
      @Nonnull Infrastructure infrastructure, EnvironmentOutcome environmentOutcome, ServiceStepOutcome service) {
    if (InfrastructureKind.PDC.equals(infrastructure.getKind())) {
      PdcInfrastructure pdcInfrastructure = (PdcInfrastructure) infrastructure;
      validatePdcInfrastructure(pdcInfrastructure);
      List<Map<String, Object>> hostInstances =
          parseHostInstancesJSON(pdcInfrastructure.getHostObjectArray().getValue());

      PdcInfrastructureOutcome pdcInfrastructureOutcome =
          PdcInfrastructureOutcome.builder()
              .credentialsRef(ParameterFieldHelper.getParameterFieldValue(pdcInfrastructure.getCredentialsRef()))
              .hosts(getHostNames(hostInstances, fixHostAttributes(pdcInfrastructure.getHostAttributes().getValue())))
              .hostFilter(toHostFilterDTO(pdcInfrastructure.getHostFilter()))
              .environment(environmentOutcome)
              .infrastructureKey(InfrastructureKey.generate(
                  service, environmentOutcome, pdcInfrastructure.getInfrastructureKeyValues()))
              .build();

      pdcInfrastructureOutcome.setInfraName(pdcInfrastructure.getInfraName());
      pdcInfrastructureOutcome.setInfraIdentifier(pdcInfrastructure.getInfraIdentifier());
      return pdcInfrastructureOutcome;
    }
    throw new InvalidArgumentsException(format("Unknown Infrastructure Kind : [%s]", infrastructure.getKind()));
  }

  private void validatePdcInfrastructure(PdcInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getCredentialsRef(), true)) {
      throw new InvalidArgumentsException(Pair.of("credentialsRef", "cannot be empty"));
    }

    if (!hasValueOrExpression(infrastructure.getHostObjectArray(), false)) {
      throw new InvalidArgumentsException(Pair.of("hostObjectArray", "cannot be empty"));
    }

    if (!hasValueOrExpression(infrastructure.getHostAttributes(), false)) {
      throw new InvalidArgumentsException(Pair.of("hostAttributes", "cannot be empty"));
    }
  }

  private List<Map<String, Object>> parseHostInstancesJSON(final String hostInstancesJson) {
    if (isEmpty(hostInstancesJson)) {
      return Collections.emptyList();
    }

    try {
      TypeReference<List<Map<String, Object>>> typeRef = new TypeReference<>() {};
      return new ObjectMapper().readValue(IOUtils.toInputStream(hostInstancesJson), typeRef);
    } catch (IOException ex) {
      log.error("Unable to parse host instances JSON", ex);
      throw new InvalidRequestException("Unable to parse host instances JSON", ex);
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

  private List<String> getHostNames(List<Map<String, Object>> hostInstances, Map<String, String> hostAttributes) {
    List<Map<String, Object>> evaluatedHostsAttributes = evaluateHostAttributes(hostInstances, hostAttributes);
    return evaluatedHostsAttributes.stream()
        .map(evaluatedHostAttributes -> (String) evaluatedHostAttributes.get(HOSTNAME_HOST_ATTRIBUTE))
        .collect(Collectors.toList());
  }

  private List<Map<String, Object>> evaluateHostAttributes(
      List<Map<String, Object>> hostInstances, Map<String, String> hostAttributes) {
    if (hostInstances == null) {
      throw new InvalidRequestException("Host instances cannot be null");
    }
    if (EmptyPredicate.isEmpty(hostAttributes)) {
      return Collections.emptyList();
    }

    List<Map<String, Object>> evaluatedHostAttributes = new ArrayList<>();
    for (Object hostInstance : hostInstances) {
      evaluatedHostAttributes.add(evaluator.evaluateProperties(hostAttributes, (Map<String, Object>) hostInstance));
    }

    return evaluatedHostAttributes;
  }

  private HostFilterDTO toHostFilterDTO(HostFilter hostFilter) {
    if (hostFilter == null) {
      return HostFilterDTO.builder().spec(AllHostsFilterDTO.builder().build()).type(HostFilterType.ALL).build();
    }

    HostFilterType type = hostFilter.getType();
    HostFilterSpec spec = hostFilter.getSpec();
    if (type == HostFilterType.HOST_NAMES) {
      return HostFilterDTO.builder()
          .spec(HostNamesFilterDTO.builder().value(((HostNamesFilter) spec).getValue()).build())
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
