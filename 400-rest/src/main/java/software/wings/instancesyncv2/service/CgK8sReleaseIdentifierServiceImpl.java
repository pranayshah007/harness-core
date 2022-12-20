package software.wings.instancesyncv2.service;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;

import software.wings.api.ContainerDeploymentInfoWithLabels;
import software.wings.api.DeploymentInfo;
import software.wings.api.K8sDeploymentInfo;
import software.wings.instancesyncv2.model.CgK8sReleaseIdentifier;
import software.wings.instancesyncv2.model.CgReleaseIdentifiers;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CgK8sReleaseIdentifierServiceImpl implements ReleaseIdentifiersService {
  @Override
  public Set<CgReleaseIdentifiers> mergeReleaseIdentifiers(
      Set<CgReleaseIdentifiers> existingIdentifiers, Set<CgReleaseIdentifiers> newIdentifiers) {
    if (CollectionUtils.isEmpty(existingIdentifiers)) {
      return newIdentifiers;
    }

    if (CollectionUtils.isEmpty(newIdentifiers)) {
      return existingIdentifiers;
    }

    Set<CgReleaseIdentifiers> identifiers = new HashSet<>();
    for (CgReleaseIdentifiers newIdentifier : newIdentifiers) {
      if (newIdentifier instanceof CgK8sReleaseIdentifier) {
        CgK8sReleaseIdentifier k8sNewIdentifier = (CgK8sReleaseIdentifier) newIdentifier;
        Optional<CgK8sReleaseIdentifier> matchingIdentifier =
            existingIdentifiers.parallelStream()
                .filter(existingIdentifier -> existingIdentifier instanceof CgK8sReleaseIdentifier)
                .map(CgK8sReleaseIdentifier.class ::cast)
                .filter(existingIdentifier
                    -> StringUtils.equals(existingIdentifier.getReleaseName(), k8sNewIdentifier.getReleaseName()))
                .filter(existingIdentifier
                    -> StringUtils.equals(existingIdentifier.getClusterName(), k8sNewIdentifier.getClusterName()))
                .filter(existingIdentifier
                    -> StringUtils.equals(
                        existingIdentifier.getContainerServiceName(), k8sNewIdentifier.getContainerServiceName()))
                .findAny();

        if (matchingIdentifier.isPresent()) {
          CgK8sReleaseIdentifier k8sReleaseIdentifier = matchingIdentifier.get();
          k8sReleaseIdentifier.getNamespaces().addAll(k8sNewIdentifier.getNamespaces());
          identifiers.add(k8sReleaseIdentifier);
        } else {
          identifiers.add(k8sNewIdentifier);
        }
      } else {
        log.error("Unknown release identifier found: [{}]", newIdentifier);
      }
    }

    return identifiers;
  }

  @Override
  public Set<CgReleaseIdentifiers> buildReleaseIdentifiers(DeploymentInfo deploymentInfo) {
    if (deploymentInfo instanceof K8sDeploymentInfo) {
      K8sDeploymentInfo k8sDeploymentInfo = (K8sDeploymentInfo) deploymentInfo;
      Set<String> namespaces = getNamespaces(k8sDeploymentInfo.getNamespaces(), k8sDeploymentInfo.getNamespace());
      if (CollectionUtils.isEmpty(namespaces)) {
        log.error("No namespace found for deployment info. Returning empty");
        return Collections.emptySet();
      }

      return Collections.singleton(CgK8sReleaseIdentifier.builder()
                                       .clusterName(k8sDeploymentInfo.getClusterName())
                                       .releaseName(k8sDeploymentInfo.getReleaseName())
                                       .namespaces(namespaces)
                                       .isHelmDeployment(false)
                                       .build());
    } else if (deploymentInfo instanceof ContainerDeploymentInfoWithLabels) {
      ContainerDeploymentInfoWithLabels containerDeploymentInfo = (ContainerDeploymentInfoWithLabels) deploymentInfo;
      Set<String> namespaces =
          getNamespaces(containerDeploymentInfo.getNamespaces(), containerDeploymentInfo.getNamespace());
      if (CollectionUtils.isEmpty(namespaces)) {
        log.error("No namespace found for deployment info. Returning empty");
        return Collections.emptySet();
      }

      Set<String> controllers = emptyIfNull(containerDeploymentInfo.getContainerInfoList())
                                    .stream()
                                    .map(io.harness.container.ContainerInfo::getWorkloadName)
                                    .filter(EmptyPredicate::isNotEmpty)
                                    .collect(Collectors.toSet());
      if (isNotEmpty(controllers)) {
        return controllers.parallelStream()
            .map(controller
                -> CgK8sReleaseIdentifier.builder()
                       .containerServiceName(controller)
                       .namespaces(namespaces)
                       .releaseName(containerDeploymentInfo.getReleaseName())
                       .isHelmDeployment(true)
                       .build())
            .collect(Collectors.toSet());
      } else if (isNotEmpty(containerDeploymentInfo.getContainerInfoList())) {
        return Collections.singleton(CgK8sReleaseIdentifier.builder()
                                         .namespaces(namespaces)
                                         .releaseName(containerDeploymentInfo.getReleaseName())
                                         .isHelmDeployment(true)
                                         .build());
      }

      return Collections.emptySet();
    }

    throw new InvalidRequestException("DeploymentInfo of type: [" + deploymentInfo.getClass().getCanonicalName()
        + "] not supported with V2 Instance Sync framework.");
  }
  private Set<String> getNamespaces(Collection<String> namespaces, String namespace) {
    Set<String> namespacesSet = new HashSet<>();
    if (StringUtils.isNotBlank(namespace)) {
      namespacesSet.add(namespace);
    }

    if (CollectionUtils.isNotEmpty(namespaces)) {
      namespacesSet.addAll(namespaces);
      namespacesSet = namespacesSet.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    return namespacesSet;
  }
}
