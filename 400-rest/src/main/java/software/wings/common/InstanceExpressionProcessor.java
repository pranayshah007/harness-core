/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.common;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.api.InstanceElementListParam.INSTANCE_LIST_PARAMS;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.OBTAIN_VALUE;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections.CollectionUtils.intersection;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder.OrderType;
import io.harness.context.ContextElementType;
import io.harness.ff.FeatureFlagService;
import io.harness.serializer.MapperUtils;

import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PartitionElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ServiceInstanceIdsParam;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.infrastructure.Host;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExpressionProcessor;
import software.wings.sm.rollback.RollbackStateMachineGenerator;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

/**
 * The Class InstanceExpressionProcessor.
 */
@NoArgsConstructor
public class InstanceExpressionProcessor implements ExpressionProcessor {
  public static final String DEFAULT_EXPRESSION = "${instances}";

  private static final String EXPRESSION_START_PATTERN = "instances()";
  private static final String EXPRESSION_EQUAL_PATTERN = "instances";

  private static final String INSTANCE_EXPR_PROCESSOR = "instanceExpressionProcessor";

  @Inject private ServiceInstanceService serviceInstanceService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private HostService hostService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private FeatureFlagService featureFlagService;

  private ExecutionContext context;

  private String serviceName;
  private String[] serviceTemplateNames;
  private String[] instanceIds;

  /**
   * Instantiates a new instance expression processor.
   *
   * @param context the context
   */
  public InstanceExpressionProcessor(ExecutionContext context) {
    this.context = context;
  }

  /**
   * Convert to instance element instance element.
   *
   * @param instance        the instance
   * @param host            the host
   * @param service
   *@param serviceTemplate the service template  @return the instance element
   */
  static InstanceElement convertToInstanceElement(
      ServiceInstance instance, Host host, Service service, ServiceTemplate serviceTemplate) {
    InstanceElement element = new InstanceElement();
    MapperUtils.mapObject(instance, element);
    element.setHost(HostExpressionProcessor.convertToHostElement(host));
    element.setServiceTemplateElement(
        ServiceTemplateExpressionProcessor.convertToServiceTemplateElement(serviceTemplate, service));
    element.setDisplayName(host.getPublicDns());
    return element;
  }

  @Override
  public String getPrefixObjectName() {
    return INSTANCE_EXPR_PROCESSOR;
  }

  @Override
  public List<String> getExpressionStartPatterns() {
    return Collections.singletonList(EXPRESSION_START_PATTERN);
  }

  @Override
  public List<String> getExpressionEqualPatterns() {
    return Collections.singletonList(EXPRESSION_EQUAL_PATTERN);
  }

  @Override
  public ContextElementType getContextElementType() {
    return ContextElementType.INSTANCE;
  }

  /**
   * Instances.
   *
   * @param serviceInstanceIds the service instance ids
   * @return the instance expression processor
   */
  public InstanceExpressionProcessor instances(String... serviceInstanceIds) {
    this.instanceIds = serviceInstanceIds;
    return this;
  }

  /**
   * Instances.
   *
   * @return the instance expression processor
   */
  public InstanceExpressionProcessor getInstances() {
    return this;
  }

  /**
   * With service.
   *
   * @param serviceName the service name
   * @return the instance expression processor
   */
  public InstanceExpressionProcessor withService(String serviceName) {
    this.serviceName = serviceName;
    return this;
  }

  /**
   * With service templates.
   *
   * @param serviceTemplateNames the service template names
   * @return the instance expression processor
   */
  public InstanceExpressionProcessor withServiceTemplates(String... serviceTemplateNames) {
    this.serviceTemplateNames = serviceTemplateNames;
    return this;
  }

  /**
   * With instance ids.
   *
   * @param instanceIds the instance ids
   * @return the instance expression processor
   */
  public InstanceExpressionProcessor withInstanceIds(String... instanceIds) {
    this.instanceIds = instanceIds;
    return this;
  }

  /**
   * Lists.
   *
   * @return the list
   */
  public List<InstanceElement> list() {
    PartitionElement instancePartition = getInstancesPartition();
    if (instancePartition != null) {
      // TODO -- apply additional filters based on host name and instanceIds
      return instancePartition.getPartitionElements()
          .stream()
          .map(contextElement -> (InstanceElement) contextElement)
          .collect(toList());
    }

    InstanceElementListParam instanceListParam = getInstanceListParam();
    if (instanceListParam != null) {
      return instanceListParam.getInstanceElements();
    }

    PageRequest<ServiceInstance> pageRequest = buildPageRequest();
    if (pageRequest == null) {
      return emptyList();
    }
    PageResponse<ServiceInstance> instances = serviceInstanceService.list(pageRequest);
    return convertToInstanceElements(instances.getResponse());
  }

  private PartitionElement getInstancesPartition() {
    List<ContextElement> partitions = context.getContextElementList(ContextElementType.PARTITION);
    if (isEmpty(partitions)) {
      return null;
    }

    for (ContextElement element : partitions) {
      PartitionElement partition = (PartitionElement) element;
      if (partition.getPartitionElementType() == ContextElementType.INSTANCE) {
        return partition;
      }
    }
    return null;
  }

  /**
   * Build page request page request.
   *
   * @return the page request
   */
  PageRequest<ServiceInstance> buildPageRequest() {
    Application app = requireNonNull(context.getApp());
    PageRequestBuilder pageRequest = aPageRequest().withLimit(UNLIMITED);

    //    applyServiceTemplatesFilter(app.getUuid(), env.getUuid(), pageRequest);
    //    applyHostNamesFilter(app.getUuid(), pageRequest);
    applyServiceInstanceIdsFilter(pageRequest);

    PageRequest<ServiceInstance> req = pageRequest.build();
    // Just for safety
    if (isEmpty(req.getFilters())) {
      return null;
    }
    req.addFilter("appId", Operator.EQ, app.getUuid());
    return req;
  }

  public List<InstanceElement> convertToInstanceElements(List<ServiceInstance> instances) {
    if (instances == null) {
      return null;
    }

    List<InstanceElement> elements = new ArrayList<>();
    for (ServiceInstance instance : instances) {
      // TODO:: optimize this block.
      ServiceTemplate serviceTemplate = serviceTemplateService.get(
          instance.getAppId(), instance.getEnvId(), instance.getServiceTemplateId(), false, OBTAIN_VALUE);
      Service service = serviceResourceService.getWithDetails(instance.getAppId(), serviceTemplate.getServiceId());
      Host host = hostService.getHostByEnv(instance.getAppId(), instance.getEnvId(), instance.getHostId());
      elements.add(convertToInstanceElement(instance, host, service, serviceTemplate));
    }

    if (isNotEmpty(instanceIds)) {
      Map<String, InstanceElement> map =
          elements.stream().collect(Collectors.toMap(InstanceElement::getUuid, Function.identity()));
      elements = new ArrayList<>();
      for (String instanceId : instanceIds) {
        if (map.containsKey(instanceId)) {
          elements.add(map.get(instanceId));
        }
      }
    }

    return elements;
  }

  private void applyServiceInstanceIdsFilter(PageRequestBuilder pageRequest) {
    ServiceInstanceIdsParam serviceInstanceIdsParam = getServiceInstanceIdsParam();
    if (serviceInstanceIdsParam != null) {
      if (isNotEmpty(instanceIds)) {
        Collection<String> commonInstanceIds =
            intersection(asList(instanceIds), serviceInstanceIdsParam.getInstanceIds());
        instanceIds = commonInstanceIds.toArray(new String[0]);
      } else {
        List<String> instanceIds = serviceInstanceIdsParam.getInstanceIds();
        if (isNotEmpty(instanceIds)) {
          this.instanceIds = instanceIds.toArray(new String[0]);
        } else {
          pageRequest.addFilter(ID_KEY, Operator.IN, emptyList());
          return;
        }
      }
    }

    if (isNotEmpty(instanceIds)) {
      pageRequest.addFilter(ID_KEY, Operator.IN, Arrays.copyOf(instanceIds, instanceIds.length, Object[].class));
    } else {
      InstanceElement element = context.getContextElement(ContextElementType.INSTANCE);
      if (element != null) {
        pageRequest.addFilter(ID_KEY, Operator.IN, element.getUuid());
      }
    }
  }

  private void applyHostNamesFilter(String appId, PageRequestBuilder pageRequest) {
    // TODO
  }

  /**
   * Checks if is wild char present.
   *
   * @param names the names
   * @return true, if is wild char present
   */
  public static boolean isWildCharPresent(String... names) {
    if (isEmpty(names)) {
      return false;
    }
    for (String name : names) {
      if (name.indexOf(WILD_CHAR) >= 0) {
        return true;
      }
    }
    return false;
  }

  private void applyServiceTemplatesFilter(String appId, String envId, PageRequestBuilder pageRequest) {
    List<Service> services = null;
    List<ServiceTemplate> serviceTemplates = null;
    if (isEmpty(serviceTemplateNames)) {
      services = getServices(appId);
      serviceTemplates = getServiceTemplates(appId, envId, services, serviceTemplateNames);
    } else {
      if (isWildCharPresent(serviceTemplateNames)) {
        serviceTemplates = getServiceTemplates(appId, envId, services);
        serviceTemplates = matchingServiceTemplates(serviceTemplates, serviceTemplateNames);
      } else {
        serviceTemplates = getServiceTemplates(appId, envId, services, serviceTemplateNames);
      }
    }

    if (isNotEmpty(serviceTemplates)) {
      pageRequest.withLimit(UNLIMITED).addFilter(
          "serviceTemplate", Operator.IN, serviceTemplates.stream().map(ServiceTemplate::getUuid).toArray());
    }
  }

  /**
   * Matching ServiceTemplates.
   *
   * @param serviceTemplates the serviceTemplates
   * @param names            the names
   * @return the list
   */
  List<ServiceTemplate> matchingServiceTemplates(List<ServiceTemplate> serviceTemplates, String... names) {
    if (serviceTemplates == null) {
      return null;
    }

    List<Pattern> patterns = new ArrayList<>();
    for (String name : names) {
      patterns.add(Pattern.compile(name.replaceAll("\\" + WILD_CHAR, "." + WILD_CHAR)));
    }

    List<ServiceTemplate> matchingServiceTemplates = new ArrayList<>();
    for (ServiceTemplate serviceTemplate : serviceTemplates) {
      for (Pattern pattern : patterns) {
        Matcher matcher = pattern.matcher(serviceTemplate.getName());
        if (matcher.matches()) {
          matchingServiceTemplates.add(serviceTemplate);
          break;
        }
      }
    }
    return matchingServiceTemplates;
  }

  private List<ServiceTemplate> getServiceTemplates(
      String appId, String envId, List<Service> services, String... names) {
    PageRequestBuilder pageRequestBuilder = aPageRequest()
                                                .withLimit(UNLIMITED)
                                                .addFilter("appId", Operator.EQ, appId)
                                                .addFilter("envId", Operator.EQ, envId)
                                                .addOrder(ServiceTemplate.CREATED_AT_KEY, OrderType.ASC);
    if (isNotEmpty(services)) {
      pageRequestBuilder.addFilter(
          "serviceId", Operator.IN, services.stream().map(Service::getUuid).collect(toList()).toArray());
    }
    if (isNotEmpty(names)) {
      pageRequestBuilder.addFilter(
          "name", Operator.IN, Arrays.copyOf(serviceTemplateNames, serviceTemplateNames.length, Object[].class));
    }
    return serviceTemplateService.list(pageRequestBuilder.build(), false, OBTAIN_VALUE).getResponse();
  }

  private InstanceElementListParam getInstanceListParam() {
    List<ContextElement> params = context.getContextElementList(ContextElementType.PARAM);
    if (params == null) {
      return null;
    }
    for (ContextElement param : params) {
      if (INSTANCE_LIST_PARAMS.equals(param.getName())) {
        return (InstanceElementListParam) param;
      }
    }
    return null;
  }

  @Nullable
  private ServiceInstanceIdsParam getServiceInstanceIdsParam() {
    return getServiceInstanceIdsParamFromSweepingOutput();
  }

  @Nullable
  private ServiceInstanceIdsParam getServiceInstanceIdsParamFromContextElement() {
    List<ContextElement> params = context.getContextElementList(ContextElementType.PARAM);
    if (params == null) {
      return null;
    }
    for (ContextElement param : params) {
      if (ServiceInstanceIdsParam.SERVICE_INSTANCE_IDS_PARAMS.equals(param.getName())) {
        return (ServiceInstanceIdsParam) param;
      }
    }
    return null;
  }

  @Nullable
  private ServiceInstanceIdsParam getServiceInstanceIdsParamFromSweepingOutput() {
    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    if (phaseElement == null) {
      return null;
    }
    String suffix = getSweepingOutputNameSuffix(phaseElement);
    ServiceInstanceIdsParam serviceInstanceIdsParam =
        sweepingOutputService.findSweepingOutput(context.prepareSweepingOutputInquiryBuilder()
                                                     .name(ServiceInstanceIdsParam.SERVICE_INSTANCE_IDS_PARAMS + suffix)
                                                     .build());
    if (serviceInstanceIdsParam == null) {
      return getServiceInstanceIdsParamFromContextElement();
    } else {
      return serviceInstanceIdsParam;
    }
  }

  private String getSweepingOutputNameSuffix(@NotNull PhaseElement phaseElement) {
    String phaseName = phaseElement.isRollback() ? phaseElement.getPhaseNameForRollback() : phaseElement.getPhaseName();
    if (phaseElement.isOnDemandRollback()) {
      return phaseName.replace(
          RollbackStateMachineGenerator.STAGING_PHASE_NAME + RollbackStateMachineGenerator.WHITE_SPACE, "");
    }
    return phaseName;
  }

  private List<Service> getServices(String appId) {
    if (isNotBlank(serviceName)) {
      PageRequest<Service> svcPageRequest = aPageRequest()
                                                .withLimit(UNLIMITED)
                                                .addFilter("appId", Operator.EQ, appId)
                                                .addFilter("name", Operator.EQ, serviceName)
                                                .addFieldsIncluded(ID_KEY)
                                                .build();

      PageResponse<Service> services = serviceResourceService.list(svcPageRequest, false, true, false, null);
      return services.getResponse();
    } else {
      ServiceElement serviceElement = context.getContextElement(ContextElementType.SERVICE);
      if (serviceElement != null) {
        Service service = new Service();
        MapperUtils.mapObject(serviceElement, service);
        return Lists.newArrayList(service);
      }
    }
    return null;
  }

  /**
   * Sets service instance service.
   *
   * @param serviceInstanceService the service instance service
   */
  void setServiceInstanceService(ServiceInstanceService serviceInstanceService) {
    this.serviceInstanceService = serviceInstanceService;
  }

  /**
   * Sets service resource service.
   *
   * @param serviceResourceService the service resource service
   */
  void setServiceResourceService(ServiceResourceService serviceResourceService) {
    this.serviceResourceService = serviceResourceService;
  }

  /**
   * Sets service template service.
   *
   * @param serviceTemplateService the service template service
   */
  public void setServiceTemplateService(ServiceTemplateService serviceTemplateService) {
    this.serviceTemplateService = serviceTemplateService;
  }

  public void setSweepingOutputService(SweepingOutputService sweepingOutputService) {
    this.sweepingOutputService = sweepingOutputService;
  }

  public void setFeatureFlagService(FeatureFlagService featureFlagService) {
    this.featureFlagService = featureFlagService;
  }
}
