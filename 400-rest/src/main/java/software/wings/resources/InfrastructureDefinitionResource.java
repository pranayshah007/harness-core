/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static software.wings.security.PermissionAttribute.Action.READ;
import static software.wings.security.PermissionAttribute.Action.UPDATE;
import static software.wings.security.PermissionAttribute.PermissionType.ENV;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.azure.model.VirtualMachineScaleSetData;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.AwsLoadBalancerDetails;
import io.harness.delegate.task.azure.appservice.webapp.response.DeploymentSlotData;
import io.harness.delegate.task.spotinst.response.SpotinstElastigroupRunningCountData;
import io.harness.rest.RestResponse;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.api.DeploymentType;
import software.wings.infra.InfraDefinitionDetail;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.InfrastructureDefinition.InfrastructureDefinitionKeys;
import software.wings.infra.ListInfraDefinitionParams;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountData;
import software.wings.service.impl.aws.model.AwsRoute53HostedZoneData;
import software.wings.service.impl.aws.model.AwsSecurityGroup;
import software.wings.service.impl.aws.model.AwsSubnet;
import software.wings.service.impl.aws.model.AwsVPC;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.settings.SettingVariableTypes;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import org.hibernate.validator.constraints.NotEmpty;

@Api("infrastructure-definitions")
@Path("infrastructure-definitions")
@Produces("application/json")
@Consumes("application/json")
@Scope(ResourceType.APPLICATION)
public class InfrastructureDefinitionResource {
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;

  @GET
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ)
  public RestResponse<PageResponse<InfrastructureDefinition>> list(
      @BeanParam PageRequest<InfrastructureDefinition> pageRequest) {
    return new RestResponse<>(infrastructureDefinitionService.list(pageRequest));
  }

  @POST
  @Path("list")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ)
  public RestResponse<PageResponse<InfrastructureDefinition>> listPost(
      @Context PageRequest pageRequest, ListInfraDefinitionParams listInfraDefinitionParams) {
    if (EmptyPredicate.isNotEmpty(listInfraDefinitionParams.getDeploymentTypeFromMetaData())) {
      pageRequest.addFilter(InfrastructureDefinitionKeys.deploymentType, SearchFilter.Operator.IN,
          listInfraDefinitionParams.getDeploymentTypeFromMetaData().toArray());
    }
    return new RestResponse<>(
        infrastructureDefinitionService.list(pageRequest, listInfraDefinitionParams.getServiceIds()));
  }

  @GET
  @Path("details")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ)
  public RestResponse<PageResponse<InfraDefinitionDetail>> listDetails(
      @BeanParam PageRequest<InfrastructureDefinition> pageRequest, @NotEmpty @QueryParam("appId") String appId,
      @NotEmpty @QueryParam("envId") String envId) {
    return new RestResponse<>(infrastructureDefinitionService.listInfraDefinitionDetail(pageRequest, appId, envId));
  }

  @POST
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = UPDATE)
  public RestResponse<InfrastructureDefinition> save(@QueryParam("appId") String appId,
      @QueryParam("envId") String envId, InfrastructureDefinition infrastructureDefinition) {
    infrastructureDefinition.setAppId(appId);
    infrastructureDefinition.setEnvId(envId);
    return new RestResponse<>(infrastructureDefinitionService.save(infrastructureDefinition, false));
  }

  @GET
  @Path("{infraDefinitionId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ)
  public RestResponse<InfrastructureDefinition> get(@QueryParam("appId") String appId,
      @QueryParam("envId") String envId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.get(appId, infraDefinitionId));
  }

  @GET
  @Path("detail/{infraDefinitionId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ)
  public RestResponse<InfraDefinitionDetail> getDetail(@QueryParam("appId") String appId,
      @QueryParam("envId") String envId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.getDetail(appId, infraDefinitionId));
  }

  @DELETE
  @Path("{infraDefinitionId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = UPDATE)
  public RestResponse delete(@QueryParam("appId") String appId, @QueryParam("envId") String envId,
      @PathParam("infraDefinitionId") String infraDefinitionId) {
    infrastructureDefinitionService.delete(appId, infraDefinitionId);
    return new RestResponse();
  }

  @PUT
  @Path("{infraDefinitionId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = UPDATE)
  public RestResponse<InfrastructureDefinition> update(@QueryParam("appId") String appId,
      @QueryParam("envId") String envId, @PathParam("infraDefinitionId") String infraDefinitionId,
      InfrastructureDefinition infrastructureDefinition) {
    infrastructureDefinition.setAppId(appId);
    infrastructureDefinition.setEnvId(envId);
    infrastructureDefinition.setUuid(infraDefinitionId);
    return new RestResponse<>(infrastructureDefinitionService.update(infrastructureDefinition));
  }

  @GET
  @Path("deployment-cloudProviders")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Map<DeploymentType, List<SettingVariableTypes>>> infrastructureTypes() {
    return new RestResponse<>(infrastructureDefinitionService.getDeploymentTypeCloudProviderOptions());
  }

  @GET
  @Path("{infraDefinitionId}/hosts")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<String>> listHosts(
      @QueryParam("appId") String appId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.listHostDisplayNames(appId, infraDefinitionId, null));
  }

  @GET
  @Path("{infraDefinitionId}/iam-roles")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<Map<String, String>> getInstanceRoles(
      @QueryParam("appId") String appId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.listAwsIamRoles(appId, infraDefinitionId));
  }

  @GET
  @Path("{infraDefinitionId}/load-balancers")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<Map<String, String>> getLoadBalancers(
      @QueryParam("appId") String appId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.listLoadBalancers(appId, infraDefinitionId));
  }

  @GET
  @Path("{infraDefinitionId}/aws-elastic-balancers")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<Map<String, String>> getAwsLoadBalancers(
      @QueryParam("appId") String appId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.listElasticLoadBalancers(appId, infraDefinitionId));
  }

  @GET
  @Path("{infraDefinitionId}/aws-elastic-balancers-details")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<AwsLoadBalancerDetails>> getAwsLoadBalancerDetails(
      @QueryParam("appId") String appId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.listElasticLoadBalancerDetails(appId, infraDefinitionId));
  }

  @GET
  @Path("{infraDefinitionId}/aws-network-balancers")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<Map<String, String>> getAwsNetworkLoadBalancers(
      @QueryParam("appId") String appId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.listNetworkLoadBalancers(appId, infraDefinitionId));
  }

  @GET
  @Path("{infraDefinitionId}/aws-network-balancers-details")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<AwsLoadBalancerDetails>> getAwsNetworkLoadBalancerDetails(
      @QueryParam("appId") String appId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.listNetworkLoadBalancerDetails(appId, infraDefinitionId));
  }

  @GET
  @Path("{infraDefinitionId}/load-balancers/{loadbalancerName}/target-groups")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<Map<String, String>> getTargetGroups(@QueryParam("appId") String appId,
      @PathParam("infraDefinitionId") String infraDefinitionId,
      @PathParam("loadbalancerName") String loadbalancerName) {
    return new RestResponse<>(
        infrastructureDefinitionService.listTargetGroups(appId, infraDefinitionId, loadbalancerName));
  }

  @GET
  @Path("{infraDefinitionId}/load-balancers/{loadbalancerName}/listeners")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<AwsElbListener>> getListeners(@QueryParam("appId") String appId,
      @PathParam("infraDefinitionId") String infraDefinitionId,
      @PathParam("loadbalancerName") String loadbalancerName) {
    return new RestResponse<>(
        infrastructureDefinitionService.listListeners(appId, infraDefinitionId, loadbalancerName));
  }

  @GET
  @Path("{infraDefinitionId}/hosted-zones")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<AwsRoute53HostedZoneData>> getHostedZones(
      @QueryParam("appId") String appId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.listHostedZones(appId, infraDefinitionId));
  }

  @GET
  @Path("{infraDefinitionId}/pcf/runningcount")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<Integer> getRunningCountForPcfApp(@QueryParam("appId") String appId,
      @QueryParam("appNameExpr") String appNameExpr, @PathParam("infraDefinitionId") String infraDefinitionId,
      @QueryParam("serviceId") String serviceId) {
    return new RestResponse<>(
        infrastructureDefinitionService.getPcfRunningInstances(appId, infraDefinitionId, appNameExpr, serviceId));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/elasti-groups")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<ElastiGroup>> listElastgroups(
      @QueryParam("appId") String appId, @PathParam("computeProviderId") String computeProviderId) {
    return new RestResponse<>(infrastructureDefinitionService.listElastiGroups(appId, computeProviderId));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/elasti-groups/{elastigroupId}/json")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<String> getElastigroupJson(@QueryParam("appId") String appId,
      @PathParam("computeProviderId") String computeProviderId, @PathParam("elastigroupId") String elastigroupId) {
    return new RestResponse<>(
        infrastructureDefinitionService.getElastigroupJson(appId, computeProviderId, elastigroupId));
  }

  @GET
  @Path("{infraDefinitionId}/ami/runningcount")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<AwsAsgGetRunningCountData> getRunningCountForAmi(@QueryParam("appId") String appId,
      @PathParam("infraDefinitionId") String infraDefinitionId, @QueryParam("serviceId") String serviceId) {
    return new RestResponse<>(
        infrastructureDefinitionService.getAmiCurrentlyRunningInstanceCount(appId, infraDefinitionId, serviceId));
  }

  @GET
  @Path("{infraDefinitionId}/elastigroup/runningcount")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<SpotinstElastigroupRunningCountData> getRunningCountForSpotinst(@QueryParam("appId") String appId,
      @PathParam("infraDefinitionId") String infraDefinitionId, @QueryParam("serviceId") String serviceId,
      @QueryParam("blueGreen") boolean blueGreen, @QueryParam("groupNameExpr") String groupNameExpr) {
    return new RestResponse<>(infrastructureDefinitionService.getElastigroupRunningCountData(
        appId, infraDefinitionId, groupNameExpr, serviceId, blueGreen));
  }

  @GET
  @Path("{infraDefinitionId}/containers")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<String> getRunningContainerCount(@QueryParam("appId") String appId,
      @QueryParam("serviceNameExpr") String serviceNameExpr, @PathParam("infraDefinitionId") String infraDefinitionId,
      @QueryParam("serviceId") String serviceId) {
    return new RestResponse<>(infrastructureDefinitionService.getContainerRunningInstances(
        appId, infraDefinitionId, serviceId, serviceNameExpr));
  }

  @GET
  @Path("{infraDefinitionId}/routes")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ)
  public RestResponse<List<String>> getRoutesForPcf(
      @QueryParam("appId") String appId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.listRoutesForPcf(appId, infraDefinitionId));
  }

  @GET
  @Path("{infraDefinitionId}/azure-load-balancers")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<String>> getAzureLoadBalancers(
      @QueryParam("appId") String appId, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.listAzureLoadBalancers(appId, infraDefinitionId));
  }

  @GET
  @Path("{infraDefinitionId}/azure-load-balancers/{loadBalancerName}/backend-pools")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<String>> getAzureLoadBalancerBackendPools(@QueryParam("appId") String appId,
      @PathParam("infraDefinitionId") String infraDefinitionId,
      @PathParam("loadBalancerName") String loadBalancerName) {
    return new RestResponse<>(
        infrastructureDefinitionService.listAzureLoadBalancerBackendPools(appId, infraDefinitionId, loadBalancerName));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/vpcs")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<AwsVPC>> listVpcs(@QueryParam("appId") String appId, @QueryParam("region") String region,
      @PathParam("computeProviderId") String computeProviderId) {
    return new RestResponse<>(infrastructureDefinitionService.listVPC(appId, computeProviderId, region));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/security-groups")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<AwsSecurityGroup>> listSecurityGroups(@QueryParam("appId") String appId,
      @QueryParam("region") String region, @QueryParam("vpcIds") @NotNull List<String> vpcIds,
      @PathParam("computeProviderId") String computeProviderId) {
    return new RestResponse<>(
        infrastructureDefinitionService.listSecurityGroups(appId, computeProviderId, region, vpcIds));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/subnets")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<AwsSubnet>> listSubnets(@QueryParam("appId") String appId,
      @QueryParam("region") String region, @QueryParam("vpcIds") @NotNull List<String> vpcIds,
      @PathParam("computeProviderId") String computeProviderId) {
    return new RestResponse<>(infrastructureDefinitionService.listSubnets(appId, computeProviderId, region, vpcIds));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/subscriptions")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<Map<String, String>> getAzureSubscriptions(@QueryParam("appId") String appId,
      @QueryParam("deploymentType") String deploymentType, @PathParam("computeProviderId") String computeProviderId) {
    return new RestResponse<>(
        infrastructureDefinitionService.listSubscriptions(appId, deploymentType, computeProviderId));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/resource-groups")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<String>> getAzureResourceGroupsNames(@QueryParam("appId") String appId,
      @QueryParam("deploymentType") String deploymentType, @QueryParam("subscriptionId") String subscriptionId,
      @PathParam("computeProviderId") String computeProviderId) {
    return new RestResponse<>(infrastructureDefinitionService.listResourceGroupsNames(
        appId, deploymentType, computeProviderId, subscriptionId));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/subscriptions/{subscriptionId}/locations")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<String>> getSubscriptionLocations(@QueryParam("appId") String appId,
      @PathParam("computeProviderId") String computeProviderId, @PathParam("subscriptionId") String subscriptionId) {
    return new RestResponse<>(
        infrastructureDefinitionService.listSubscriptionLocations(appId, computeProviderId, subscriptionId));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/locations")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<String>> getCloudProviderLocations(
      @QueryParam("appId") String appId, @PathParam("computeProviderId") String computeProviderId) {
    return new RestResponse<>(
        infrastructureDefinitionService.listAzureCloudProviderLocations(appId, computeProviderId));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/management-groups")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<Map<String, String>> getManagementGroups(
      @QueryParam("appId") String appId, @PathParam("computeProviderId") String computeProviderId) {
    return new RestResponse<>(infrastructureDefinitionService.listManagementGroups(appId, computeProviderId));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/vm-scale-sets")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<Map<String, String>> getAzureVirtualMachineScaleSets(@QueryParam("appId") String appId,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroupName") String resourceGroupName,
      @QueryParam("deploymentType") String deploymentType, @PathParam("computeProviderId") String computeProviderId) {
    return new RestResponse<>(infrastructureDefinitionService.listVirtualMachineScaleSets(
        appId, deploymentType, computeProviderId, subscriptionId, resourceGroupName));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/vm-scale-sets/{vmssName}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<VirtualMachineScaleSetData> getAzureVirtualMachineScaleSetByName(
      @QueryParam("appId") String appId, @QueryParam("subscriptionId") String subscriptionId,
      @QueryParam("resourceGroupName") String resourceGroupName, @QueryParam("deploymentType") String deploymentType,
      @PathParam("vmssName") String vmssName, @PathParam("computeProviderId") String computeProviderId) {
    return new RestResponse<>(infrastructureDefinitionService.getVirtualMachineScaleSet(
        appId, deploymentType, computeProviderId, subscriptionId, resourceGroupName, vmssName));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/azure-app-services")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<String>> getAppServiceNamesByResourceGroup(@QueryParam("appId") String appId,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroupName") String resourceGroupName,
      @QueryParam("appType") String appType, @PathParam("computeProviderId") String computeProviderId) {
    return new RestResponse<>(infrastructureDefinitionService.getAppServiceNamesByResourceGroup(
        appId, computeProviderId, subscriptionId, resourceGroupName, appType));
  }

  @GET
  @Path("{infraDefinitionId}/azure-app-services")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<String>> getAppServiceNames(@QueryParam("appId") String appId,
      @QueryParam("appType") String appType, @PathParam("infraDefinitionId") String infraDefinitionId) {
    return new RestResponse<>(infrastructureDefinitionService.getAppServiceNames(appId, infraDefinitionId, appType));
  }

  @GET
  @Path("compute-providers/{computeProviderId}/azure-app-services/{appName}/slots")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<DeploymentSlotData>> getAppServiceDeploymentSlotNames(@QueryParam("appId") String appId,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroupName") String resourceGroupName,
      @QueryParam("appType") String appType, @PathParam("computeProviderId") String computeProviderId,
      @PathParam("appName") String appName) {
    return new RestResponse<>(infrastructureDefinitionService.getAppServiceDeploymentSlots(
        appId, computeProviderId, subscriptionId, resourceGroupName, appType, appName));
  }

  @GET
  @Path("{infraDefinitionId}/azure-app-services/{appName}/slots")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ENV, action = READ, skipAuth = true)
  public RestResponse<List<DeploymentSlotData>> getDeploymentSlotNames(@QueryParam("appId") String appId,
      @QueryParam("appType") String appType, @PathParam("infraDefinitionId") String infraDefinitionId,
      @PathParam("appName") String appName) {
    return new RestResponse<>(
        infrastructureDefinitionService.getAppServiceDeploymentSlots(appId, infraDefinitionId, appType, appName));
  }
}
