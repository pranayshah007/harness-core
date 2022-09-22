/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.expressions.NGExpressionServiceEvaluator;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.host.dto.AllHostsFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostFilterDTO;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.steps.environment.EnvironmentOutcome;

import software.wings.beans.NameValuePair;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
  @Inject private NGExpressionServiceEvaluator evaluator;

  @NotNull
  public InfrastructureOutcome toOutcome(
      @Nonnull Infrastructure infrastructure, EnvironmentOutcome environmentOutcome, ServiceStepOutcome service) {
    if (InfrastructureKind.PDC.equals(infrastructure.getKind())) {
      PdcInfrastructure pdcInfrastructure = (PdcInfrastructure) infrastructure;
      List<Map<String, Object>> hostInstances =
          parseHostInstancesJSON(pdcInfrastructure.getHostObjectArrayPath().getValue());
      PdcInfrastructureOutcome pdcInfrastructureOutcome =
          PdcInfrastructureOutcome.builder()
              .credentialsRef(ParameterFieldHelper.getParameterFieldValue(pdcInfrastructure.getCredentialsRef()))
              .hosts(getHostNames(hostInstances, fixHostAttributes(pdcInfrastructure.getHostAttributes().getValue())))
              .hostFilter(
                  HostFilterDTO.builder().spec(AllHostsFilterDTO.builder().build()).type(HostFilterType.ALL).build())
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

  private List<Map<String, Object>> parseHostInstancesJSON(final String hostInstancesJson) {
    if (isEmpty(hostInstancesJson)) {
      return Collections.emptyList();
    }

    try {
      TypeReference<List<Map<String, Object>>> typeRef = new TypeReference<>() {};
      return new ObjectMapper().readValue(IOUtils.toInputStream(hostInstancesJson), typeRef);

    } catch (IOException ex) {
      log.error("Unable to parse host instances JSON", ex);
      throw new InvalidRequestException("Unable to parse host instances JSON");
    }
  }

  private Map<String, String> fixHostAttributes(Map<String, String> hostAttributes) {
    hostAttributes.remove("__uuid");
    hostAttributes.replaceAll((key, oldValue) -> format("%s%s%s", "${", oldValue, "}"));
    return hostAttributes;
  }

  private List<String> getHostNames(List<Map<String, Object>> hostInstances, Map<String, String> hostAttributes) {
    List<Map<String, Object>> evaluatedHostsAttributes = evaluateHostAttributes(hostInstances, hostAttributes);
    return evaluatedHostsAttributes.stream()
        .map(evaluatedHostAttributes -> (String) evaluatedHostAttributes.get("hostname"))
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

    List<NameValuePair> attributes = toNameValuePairs(hostAttributes);
    List<Map<String, Object>> fieldsEvaluatedList = new ArrayList<>();
    for (Object hostInstance : hostInstances) {
      fieldsEvaluatedList.add(evaluateProperties(attributes, (Map<String, Object>) hostInstance));
    }

    return fieldsEvaluatedList;
  }

  private List<NameValuePair> toNameValuePairs(Map<String, String> hostAttributes) {
    return hostAttributes.entrySet()
        .stream()
        .map(property -> new NameValuePair(property.getKey(), property.getValue(), null))
        .collect(toList());
  }

  private Map<String, Object> evaluateProperties(List<NameValuePair> properties, Map<String, Object> contextMap) {
    Map<String, Object> propertyNameEvaluatedMap = new HashMap<>();
    for (NameValuePair property : properties) {
      if (isEmpty(property.getValue())) {
        continue;
      }
      if (!property.getValue().contains("$")) {
        propertyNameEvaluatedMap.put(property.getName(), property.getValue());
        continue;
      }
      Object evaluated = null;
      try {
        evaluated = evaluator.evaluate(property.getValue(), contextMap);
      } catch (Exception ignore) {
        // ignore this exception, it is based on user input
      }
      if (evaluated == null) {
        evaluated = evaluator.substitute(property.getValue(), contextMap);
        if (evaluated == null) {
          log.info("Unresolved expression {} ", property.getValue());
          throw new InvalidRequestException(format("Unable to resolve %s", property.getValue()));
        }
      }
      propertyNameEvaluatedMap.put(property.getName(), evaluated);
    }
    return propertyNameEvaluatedMap;
  }
}
