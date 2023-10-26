/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.normalize;

import io.harness.exception.InvalidArgumentsException;
import io.harness.ssca.beans.CyclonedxDTO;
import io.harness.ssca.beans.SettingsDTO;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMComponentEntityBuilder;
import io.harness.ssca.utils.SBOMUtils;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Strings;

@Slf4j
public class CyclonedxNormalizer implements Normalizer<CyclonedxDTO> {
  @Override
  public List<NormalizedSBOMComponentEntity> normaliseSBOM(CyclonedxDTO sbom, SettingsDTO settings)
      throws ParseException {
    List<NormalizedSBOMComponentEntity> sbomEntityList = new ArrayList<>();

    for (CyclonedxDTO.Component component : sbom.getComponents()) {
      Instant createdOn = Instant.now();
      if (SBOMUtils.parseDateTime(sbom.getMetadata().getTimestamp()) != null) {
        createdOn = SBOMUtils.parseDateTime(sbom.getMetadata().getTimestamp()).toInstant();
      }

      NormalizedSBOMComponentEntityBuilder normalizedSBOMEntityBuilder =
          NormalizedSBOMComponentEntity.builder()
              .sbomVersion(sbom.getBomFormat() + sbom.getSpecVersion())
              .artifactId(settings.getArtifactID())
              .artifactUrl(settings.getArtifactURL())
              .artifactName(component.getName())
              .tags(Collections.singletonList(settings.getArtifactTag()))
              .createdOn(createdOn)
              .toolVersion(settings.getTool().getVersion())
              .toolName(settings.getTool().getName())
              .toolVendor(settings.getTool().getVendor())
              .packageId(component.getBomRef())
              .packageName(component.getName())
              .packageDescription(component.getDescription())
              .packageLicense(getPackageLicense(component.getLicenses()))
              .packageVersion(component.getVersion())
              .packageOriginatorName(component.getPublisher())
              .orchestrationId(settings.getOrchestrationID())
              .pipelineIdentifier(settings.getPipelineIdentifier())
              .projectIdentifier(settings.getProjectIdentifier())
              .orgIdentifier(settings.getOrgIdentifier())
              .accountId(settings.getAccountID());

      if (component.getPublisher() != null && component.getPublisher().contains(":")) {
        String[] splitOriginator = Strings.split(component.getPublisher(), ':');
        if (splitOriginator.length >= 2) {
          normalizedSBOMEntityBuilder.originatorType(splitOriginator[0].trim());
          normalizedSBOMEntityBuilder.packageOriginatorName(splitOriginator[1].trim());
        }
      }

      List<Integer> versionInfo = SBOMUtils.getVersionInfo(component.getVersion());
      normalizedSBOMEntityBuilder.majorVersion(versionInfo.get(0));
      normalizedSBOMEntityBuilder.minorVersion(versionInfo.get(1));
      normalizedSBOMEntityBuilder.patchVersion(versionInfo.get(2));
      try {
        List<String> packageManagerInfo = getPackageManagerCyclonedx(component);
        normalizedSBOMEntityBuilder.purl(packageManagerInfo.get(0));
        normalizedSBOMEntityBuilder.packageNamespace(packageManagerInfo.get(1));
        normalizedSBOMEntityBuilder.packageManager(packageManagerInfo.get(2));
      } catch (InvalidArgumentsException e) {
        continue;
      }

      sbomEntityList.add(normalizedSBOMEntityBuilder.build());
    }
    return sbomEntityList;
  }

  private List<String> getPackageManagerCyclonedx(CyclonedxDTO.Component component) {
    String packageNamespace = null;
    String packageManager = null;
    String purl = component.getPurl();
    if (purl == null) {
      throw new InvalidArgumentsException("Invalid Purl");
    }
    String[] splitPurl = Strings.split(purl, SBOMUtils.EXTERNAL_REF_LOCATOR_DELIM_PRIMARY);
    if (splitPurl.length != 2 && !splitPurl[0].equals("pkg")) {
      log.error(String.format("Invalid purl: %s", purl));
      throw new InvalidArgumentsException("Invalid Purl");
    }
    splitPurl = Strings.split(splitPurl[1], SBOMUtils.EXTERNAL_REF_LOCATOR_DELIM_SECONDAY);
    if (splitPurl.length < 2) {
      log.error(String.format("Invalid purl: %s", purl));
      throw new InvalidArgumentsException("Invalid Purl");
    }
    if (splitPurl.length > 2) {
      // if purl is of the format pkg:<package-manager>/<namespace>/<name>@<version>
      packageNamespace = splitPurl[1];
    }
    packageManager = splitPurl[0];
    return Arrays.asList(purl, packageNamespace, packageManager);
  }

  private List<String> getPackageLicense(List<CyclonedxDTO.Component.License> licenses) {
    List<String> result = new ArrayList<>();
    if (Objects.nonNull(licenses) && licenses.size() > 0) {
      for (CyclonedxDTO.Component.License license : licenses) {
        String packageLicense = null;
        if (license.getLicense() != null) {
          if (license.getLicense().getName() != null) {
            packageLicense = license.getLicense().getName();
          } else if (license.getLicense().getId() != null) {
            packageLicense = license.getLicense().getId();
          }
        } else if (license.getExpression() != null) {
          result.addAll(SBOMUtils.processExpression(license.getExpression()));
        }

        if (packageLicense != null && packageLicense.contains(SBOMUtils.LICENSE_REF_DELIM)) {
          String[] splitLicense = packageLicense.split(SBOMUtils.LICENSE_REF_DELIM);
          if (splitLicense.length >= 2) {
            packageLicense = splitLicense[1];
          }
        }
        if (packageLicense != null) {
          result.add(packageLicense);
        }
      }
    } else {
      result.add("NO_ASSERTION");
    }
    return result;
  }
}
