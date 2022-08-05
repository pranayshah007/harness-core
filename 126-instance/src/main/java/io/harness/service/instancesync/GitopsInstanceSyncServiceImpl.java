/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesync;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;
import io.harness.logging.AutoLogContext;
import io.harness.models.constants.InstanceSyncFlow;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.ng.core.logging.NGProjectLogContext;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;
import io.harness.util.logging.InstanceSyncLogContext;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(GITOPS)
@Singleton
public class GitopsInstanceSyncServiceImpl implements GitopsInstanceSyncService {
  @Inject private InstanceService instanceService;
  @Inject private InstanceSyncServiceUtils utils;
  @Inject private InstanceSyncHandlerFactoryService instanceSyncHandlerFactoryService;
  @Override
  public void processInstanceSync(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<InstanceDTO> instanceList) {
    try (AutoLogContext ignore1 = new NGProjectLogContext(
             accountIdentifier, orgIdentifier, projectIdentifier, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR);
         AutoLogContext ignore2 = InstanceSyncLogContext.builder()
                                      .instanceSyncFlow(InstanceSyncFlow.GITOPS_APPLICATION_SYNC.name())
                                      .build(AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      final Map<String, List<InstanceDTO>> instancesGroupedByService =
          instanceList.stream().collect(Collectors.groupingBy(InstanceDTO::getServiceIdentifier));

      final AbstractInstanceSyncHandler instanceSyncHandler =
          instanceSyncHandlerFactoryService.getInstanceSyncHandler(ServiceSpecType.GITOPS);

      for (Map.Entry<String, List<InstanceDTO>> instanceDTOList : instancesGroupedByService.entrySet()) {
        List<InstanceDTO> instances = instanceDTOList.getValue();
        Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified =
            handleInstanceSync(instances, instanceSyncHandler);
        utils.processInstances(instancesToBeModified);
      }
      log.info("Instance Sync completed");
    } catch (Exception exception) {
      log.error("Exception occurred during instance sync", exception);
    }
  }

  private Map<OperationsOnInstances, List<InstanceDTO>> handleInstanceSync(
      List<InstanceDTO> instancesFromServer, AbstractInstanceSyncHandler instanceSyncHandler) {
    InstanceDTO instanceDTO = instancesFromServer.get(0);
    List<InstanceDTO> instancesInDB = instanceService.getActiveInstancesByServiceId(instanceDTO.getAccountIdentifier(),
        instanceDTO.getOrgIdentifier(), instanceDTO.getProjectIdentifier(), instanceDTO.getServiceIdentifier());
    // map all instances and server instances infos to instance sync handler key (corresponding to deployment info)
    // basically trying to group instances corresponding to a "cluster" together
    Map<String, List<InstanceDTO>> syncKeyToInstancesInDBMap =
        utils.getSyncKeyToInstances(instanceSyncHandler, instancesInDB);

    Map<String, List<InstanceDTO>> syncKeyToInstancesFromServerMap =
        utils.getSyncKeyToInstances(instanceSyncHandler, instancesFromServer);

    // Declare map sets
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified =
        utils.initMapForTrackingFinalListOfInstances();

    // Assign sync values to map sets create, update, delete
    processInstanceSyncForSyncKeysFromServerInstances(
        instanceSyncHandler, syncKeyToInstancesInDBMap, syncKeyToInstancesFromServerMap, instancesToBeModified);

    return instancesToBeModified;
  }

  private void processInstanceSyncForSyncKeysFromServerInstances(AbstractInstanceSyncHandler instanceSyncHandler,
      Map<String, List<InstanceDTO>> syncKeyToInstancesInDBMap,
      Map<String, List<InstanceDTO>> syncKeyToInstanceFromServerMap,
      Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified) {
    Set<String> instanceSyncHandlerKeys = syncKeyToInstanceFromServerMap.keySet();
    instanceSyncHandlerKeys.forEach(instanceSyncHandlerKey
        -> processInstancesByInstanceSyncHandlerKey(instanceSyncHandler,
            syncKeyToInstancesInDBMap.getOrDefault(instanceSyncHandlerKey, new ArrayList<>()),
            syncKeyToInstanceFromServerMap.getOrDefault(instanceSyncHandlerKey, new ArrayList<>()),
            instancesToBeModified));
  }

  private void processInstancesByInstanceSyncHandlerKey(AbstractInstanceSyncHandler instanceSyncHandler,
      List<InstanceDTO> instancesInDB, List<InstanceDTO> instancesDTOsFromServer,
      Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified) {
    // Now, map all instances by instance key and find out instances to be deleted/added/updated
    Map<String, InstanceDTO> instancesInDBMap = new HashMap<>();
    Map<String, InstanceDTO> instancesFromServerMap = new HashMap<>();
    instancesInDB.forEach(instanceDTO
        -> instancesInDBMap.put(instanceSyncHandler.getInstanceKey(instanceDTO.getInstanceInfoDTO()), instanceDTO));
    instancesDTOsFromServer.forEach(instanceDTO
        -> instancesFromServerMap.put(
            instanceSyncHandler.getInstanceKey(instanceDTO.getInstanceInfoDTO()), instanceDTO));

    prepareGitOpsInstancesToBeDeleted(instancesToBeModified, instancesInDBMap, instancesFromServerMap);
    prepareInstancesToBeAdded(instancesToBeModified, instancesInDBMap, instancesFromServerMap);
  }

  private void prepareGitOpsInstancesToBeDeleted(Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified,
      Map<String, InstanceDTO> instancesInDBMap, Map<String, InstanceDTO> instancesFromServerMap) {
    Sets.SetView<String> instancesToBeDeleted =
        Sets.difference(instancesInDBMap.keySet(), instancesFromServerMap.keySet());

    log.info("prepareInstancesToBeDeleted, instanceInfosFromServerMap: {}", instancesFromServerMap.keySet());
    log.info("prepareInstancesToBeDeleted, instancesInDBMap: {}", instancesInDBMap.keySet());
    log.info("prepareInstancesToBeDeleted, Instances to be deleted: {}", instancesToBeDeleted);

    // Add instances to be deleted to the global map
    instancesToBeModified.get(OperationsOnInstances.DELETE)
        .addAll(instancesToBeDeleted.stream().map(instancesInDBMap::get).collect(Collectors.toSet()));
  }
  private void prepareInstancesToBeAdded(Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified,
      Map<String, InstanceDTO> instancesInDBMap, Map<String, InstanceDTO> instancesFromServerMap) {
    Sets.SetView<String> instancesToBeAdded =
        Sets.difference(instancesFromServerMap.keySet(), instancesInDBMap.keySet());

    instancesToBeModified.get(OperationsOnInstances.ADD)
        .addAll(instancesToBeAdded.stream().map(instancesFromServerMap::get).collect(Collectors.toList()));
  }
}
