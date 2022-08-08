/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.UnknownArtifactStreamTypeException;

import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.service.intfc.AcrBuildService;
import software.wings.service.intfc.AmazonS3BuildService;
import software.wings.service.intfc.AmiBuildService;
import software.wings.service.intfc.ArtifactoryBuildService;
import software.wings.service.intfc.AzureArtifactsBuildService;
import software.wings.service.intfc.AzureMachineImageBuildService;
import software.wings.service.intfc.BambooBuildService;
import software.wings.service.intfc.BuildService;
import software.wings.service.intfc.CustomBuildService;
import software.wings.service.intfc.DockerBuildService;
import software.wings.service.intfc.EcrBuildService;
import software.wings.service.intfc.GcrBuildService;
import software.wings.service.intfc.GcsBuildService;
import software.wings.service.intfc.JenkinsBuildService;
import software.wings.service.intfc.NexusBuildService;
import software.wings.service.intfc.SftpBuildService;
import software.wings.service.intfc.SmbBuildService;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

/**
 * Service class locator for all the artifact streams.
 * We currently use guice based lookup but we have an issue with the current design.
 * Each config (compute provider / connector) can only be associated to one Service.
 * In our case, both ECR and S3 services use AwsConfig.
 * @author rktummala on 08/17/17
 */
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ServiceClassLocator {
  @Inject Injector injector;
  private Map<Class, List> cache = new ConcurrentHashMap<>();

  public Class<? extends BuildService> getBuildServiceClass(String artifactStreamTypeStr) {
    ArtifactStreamType artifactStreamType = ArtifactStreamType.valueOf(artifactStreamTypeStr);
    switch (artifactStreamType) {
      case AMAZON_S3:
        return AmazonS3BuildService.class;
      case ARTIFACTORY:
        return ArtifactoryBuildService.class;
      case BAMBOO:
        return BambooBuildService.class;
      case DOCKER:
        return DockerBuildService.class;
      case ECR:
        return EcrBuildService.class;
      case GCR:
        return GcrBuildService.class;
      case GCS:
        return GcsBuildService.class;
      case JENKINS:
        return JenkinsBuildService.class;
      case NEXUS:
        return NexusBuildService.class;
      case AMI:
        return AmiBuildService.class;
      case SMB:
        return SmbBuildService.class;
      case SFTP:
        return SftpBuildService.class;
      case ACR:
        return AcrBuildService.class;
      case AZURE_ARTIFACTS:
        return AzureArtifactsBuildService.class;
      case AZURE_MACHINE_IMAGE:
        return AzureMachineImageBuildService.class;
      case CUSTOM:
        return CustomBuildService.class;
      default:
        throw new UnknownArtifactStreamTypeException(artifactStreamTypeStr);
    }
  }

  public static <T> List<T> descendingServices(Object inst, Class cls, Class<T> intr) {
    List<T> descendings = new ArrayList<>();

    synchronized (cls) {
      for (Field field : cls.getDeclaredFields()) {
        boolean originalAccessible = field.isAccessible();
        if (!originalAccessible) {
          field.setAccessible(true);
        }
        try {
          Object obj = field.get(inst);
          if (intr.isInstance(obj)) {
            T descending = (T) obj;
            descendings.add(descending);
          }
        } catch (IllegalAccessException e) {
          log.error("", e);
        } finally {
          if (!originalAccessible) {
            field.setAccessible(false);
          }
        }
      }
    }

    return descendings;
  }

  public <T> List<T> descendingServicesForInterface(Class<T> intr) {
    return cache.computeIfAbsent(intr, k -> {
      Set<Class<? extends T>> services = new HashSet<>();
      Reflections harnessReflections = new Reflections("io.harness");
      services.addAll(harnessReflections.getSubTypesOf(intr));

      Reflections wingsReflections = new Reflections("software.wings");
      services.addAll(wingsReflections.getSubTypesOf(intr));

      List<T> result = new ArrayList<>();
      for (Class service : services) {
        if (service.isInterface()) {
          result.add((T) injector.getInstance(service));
        }
      }
      return result;
    });
  }
}
