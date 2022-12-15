/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;

import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.SERVICE;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.Misc;

import software.wings.api.DeploymentType;
import software.wings.api.PcfInstanceElement.PcfInstanceElementKeys;
import software.wings.beans.EntityType;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceTemplate.ServiceTemplateKeys;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by sgurubelli on 8/7/17.
 */
@OwnedBy(CDC)
@Singleton
public class ServiceExpressionBuilder extends ExpressionBuilder {
  public static final String ALL = "All";
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private AppService appService;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public Set<String> getExpressions(String appId, String entityId) {
    SortedSet<String> expressions = new TreeSet<>();
    expressions.addAll(getStaticExpressions(false));
    expressions.addAll(getDynamicExpressions(appId, entityId));
    expressions.addAll(getServiceTemplateVariableExpressions(appId, entityId, ENVIRONMENT));
    return expressions;
  }

  @Override
  public Set<String> getExpressions(String appId, String entityId, StateType stateType) {
    if (stateType == null) {
      return getExpressions(appId, entityId);
    }
    SortedSet<String> expressions = new TreeSet<>(getExpressions(appId, entityId));
    expressions.addAll(getStateTypeExpressions(stateType));
    return expressions;
  }

  @Override
  public Set<String> getDynamicExpressions(String appId, String entityId) {
    return getServiceVariables(appId, getServiceIds(appId, entityId), SERVICE);
  }

  public List<String> getServiceIds(String appId, String entityId) {
    if (entityId.equalsIgnoreCase("All")) {
      List<Service> services = serviceResourceService.list(
          aPageRequest().withLimit(UNLIMITED).addFilter("appId", EQ, appId).addFieldsIncluded("uuid").build(), false,
          false, false, null);
      return services.stream().map(Service::getUuid).collect(toList());
    } else {
      return asList(Misc.commaCharPattern.split(entityId));
    }
  }

  public Set<String> getServiceTemplateVariableExpressions(String appId, String serviceId, EntityType entityType) {
    if (considerAllServices(serviceId)) {
      PageRequest<ServiceTemplate> serviceTemplatePageRequest =
          aPageRequest().withLimit(UNLIMITED).addFilter(ServiceTemplateKeys.appId, EQ, appId).build();
      return getServiceVariablesOfTemplates(appId, serviceTemplatePageRequest, entityType);
    }
    List<String> serviceIds = getServiceIds(appId, serviceId);
    if (!serviceIds.isEmpty()) {
      PageRequest<ServiceTemplate> serviceTemplatePageRequest =
          aPageRequest()
              .withLimit(UNLIMITED)
              .addFilter(ServiceTemplateKeys.appId, EQ, appId)
              .addFilter(ServiceTemplateKeys.serviceId, IN, serviceIds.toArray())
              .build();
      return getServiceVariablesOfTemplates(appId, serviceTemplatePageRequest, entityType);
    }
    return new TreeSet<>();
  }

  private boolean considerAllServices(String serviceId) {
    return ALL.equalsIgnoreCase(serviceId);
  }

  public List<String> getContinuousVerificationVariables(String appId, String serviceId) {
    final Service service = serviceResourceService.get(appId, serviceId);
    if (service == null) {
      return new ArrayList<>();
    }
    List<String> rv = new ArrayList<>();
    rv.add("host.hostName");
    if (service.getDeploymentType() == DeploymentType.PCF) {
      rv.add("host.pcfElement." + PcfInstanceElementKeys.applicationId);
      rv.add("host.pcfElement." + PcfInstanceElementKeys.displayName);
      rv.add("host.pcfElement." + PcfInstanceElementKeys.instanceIndex);
    }
    return rv;
  }
}
