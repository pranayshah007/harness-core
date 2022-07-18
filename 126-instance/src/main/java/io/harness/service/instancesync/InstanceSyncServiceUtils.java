package io.harness.service.instancesync;

import io.harness.dtos.InstanceDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class InstanceSyncServiceUtils {
  @Inject private InstanceService instanceService;

  public void processInstances(Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified) {
    deleteInstances(instancesToBeModified.get(OperationsOnInstances.DELETE));
    // ** check if saveAll method can be used safely to update multiple records together
    saveInstances(instancesToBeModified.get(OperationsOnInstances.ADD));
    updateInstances(instancesToBeModified.get(OperationsOnInstances.UPDATE));
  }
  private void deleteInstances(List<InstanceDTO> instancesToBeDeleted) {
    logInstances(OperationsOnInstances.DELETE.name(), instancesToBeDeleted);
    instancesToBeDeleted.forEach(instanceDTO
        -> instanceService.delete(instanceDTO.getInstanceKey(), instanceDTO.getAccountIdentifier(),
            instanceDTO.getOrgIdentifier(), instanceDTO.getProjectIdentifier(),
            instanceDTO.getInfrastructureMappingId()));
  }

  private void saveInstances(List<InstanceDTO> instancesToBeSaved) {
    logInstances(OperationsOnInstances.ADD.name(), instancesToBeSaved);
    instancesToBeSaved.forEach(instanceDTO -> instanceService.saveOrReturnEmptyIfAlreadyExists(instanceDTO));
  }

  private void updateInstances(List<InstanceDTO> instancesToBeUpdated) {
    logInstances(OperationsOnInstances.UPDATE.name(), instancesToBeUpdated);
    instancesToBeUpdated.forEach(instanceDTO -> instanceService.findAndReplace(instanceDTO));
  }
  private void logInstances(String operation, List<InstanceDTO> instanceDTOList) {
    if (instanceDTOList.isEmpty()) {
      return;
    }
    StringBuilder stringBuilder = new StringBuilder();
    instanceDTOList.forEach(instanceDTO -> stringBuilder.append(instanceDTO.getInstanceKey()).append(" :: "));
    log.info("Instance Operation : {} , count : {} , details : {}", operation, instanceDTOList.size(),
        stringBuilder.toString());
  }
  Map<String, List<InstanceDTO>> getSyncKeyToInstances(
      AbstractInstanceSyncHandler instanceSyncHandler, List<InstanceDTO> instanceDTOS) {
    Map<String, List<InstanceDTO>> syncKeyToInstancesInDBMap = new HashMap<>();
    instanceDTOS.forEach(instanceDTO -> {
      String instanceSyncHandlerKey = instanceSyncHandler.getInstanceSyncHandlerKey(instanceDTO.getInstanceInfoDTO());
      List<InstanceDTO> existingInstanceDTOList =
          syncKeyToInstancesInDBMap.getOrDefault(instanceSyncHandlerKey, new ArrayList<>());
      existingInstanceDTOList.add(instanceDTO);
      syncKeyToInstancesInDBMap.put(instanceSyncHandlerKey, existingInstanceDTOList);
    });
    return syncKeyToInstancesInDBMap;
  }

  Map<String, List<InstanceInfoDTO>> getSyncKeyToInstancesFromServerMap(
      AbstractInstanceSyncHandler instanceSyncHandler, List<InstanceInfoDTO> instanceInfosFromServer) {
    Map<String, List<InstanceInfoDTO>> syncKeyToInstanceInfoFromServerMap = new HashMap<>();
    instanceInfosFromServer.forEach(instanceInfoDTO -> {
      String instanceSyncHandlerKey = instanceSyncHandler.getInstanceSyncHandlerKey(instanceInfoDTO);
      List<InstanceInfoDTO> instanceInfoDTOList =
          syncKeyToInstanceInfoFromServerMap.getOrDefault(instanceSyncHandlerKey, new ArrayList<>());
      instanceInfoDTOList.add(instanceInfoDTO);
      syncKeyToInstanceInfoFromServerMap.put(instanceSyncHandlerKey, instanceInfoDTOList);
    });
    return syncKeyToInstanceInfoFromServerMap;
  }

  Map<OperationsOnInstances, List<InstanceDTO>> initMapForTrackingFinalListOfInstances() {
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = new HashMap<>();
    instancesToBeModified.put(OperationsOnInstances.ADD, new ArrayList<>());
    instancesToBeModified.put(OperationsOnInstances.DELETE, new ArrayList<>());
    instancesToBeModified.put(OperationsOnInstances.UPDATE, new ArrayList<>());
    return instancesToBeModified;
  }
}
