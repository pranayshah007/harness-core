/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.repositories.EnforcementResultRepo;
import io.harness.spec.server.ssca.v1.model.EnforcementResultDTO;
import io.harness.ssca.beans.AllowLicense;
import io.harness.ssca.beans.AllowList.AllowListItem;
import io.harness.ssca.beans.AllowList.AllowListRuleType;
import io.harness.ssca.beans.DenyList.DenyListItem;
import io.harness.ssca.beans.Supplier;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.EnforcementResultEntity.EnforcementResultEntityKeys;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.transformers.EnforcementResultTransformer;

import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.ws.rs.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public class EnforcementResultServiceImpl implements EnforcementResultService {
  @Inject EnforcementResultRepo enforcementResultRepo;
  @Override
  public EnforcementResultEntity getEnforcementResults(NormalizedSBOMComponentEntity component, String violationType,
      String violationDetails, ArtifactEntity artifact, String enforcementId) {
    return EnforcementResultEntity.builder()
        .enforcementID(enforcementId)
        .artifactId(component.getArtifactId())
        .tag(artifact.getTag())
        .imageName(artifact.getName())
        .accountId(component.getAccountId())
        .orgIdentifier(component.getOrgIdentifier())
        .projectIdentifier(component.getProjectIdentifier())
        .orchestrationID(artifact.getOrchestrationId())
        .violationType(violationType)
        .violationDetails(violationDetails)
        .name(component.getPackageName())
        .supplier(component.getPackageOriginatorName())
        .supplierType(component.getOriginatorType())
        .packageManager(component.getPackageManager())
        .license(component.getPackageLicense())
        .purl(component.getPurl())
        .version(component.getPackageVersion())
        .build();
  }

  @Override
  public Page<EnforcementResultEntity> getPolicyViolations(String accountId, String orgIdentifier,
      String projectIdentifier, String enforcementId, String searchText, Pageable pageable) {
    Criteria criteria = Criteria.where(EnforcementResultEntityKeys.accountId)
                            .is(accountId)
                            .and(EnforcementResultEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(EnforcementResultEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(EnforcementResultEntityKeys.enforcementID)
                            .is(enforcementId);

    Criteria searchCriteria = new Criteria();
    Pattern pattern = Pattern.compile("[.]*" + searchText + "[.]*");
    if (Objects.nonNull(searchText) && !searchText.isEmpty()) {
      searchCriteria.orOperator(Arrays.asList(Criteria.where(EnforcementResultEntityKeys.name).regex(pattern),
          Criteria.where(EnforcementResultEntityKeys.license).regex(pattern),
          Criteria.where(EnforcementResultEntityKeys.supplier).regex(pattern)));
    }

    criteria = criteria.andOperator(searchCriteria);

    return enforcementResultRepo.findAll(criteria, pageable);
  }

  @Override
  public String getViolationDetails(
      NormalizedSBOMComponentEntity component, AllowListItem allowListItem, AllowListRuleType type) {
    if (type == AllowListRuleType.ALLOW_LICENSE_ITEM) {
      return getLicenseViolationDetails(
          component.getPackageName(), component.getPackageLicense(), allowListItem.getLicenses());
    } else if (type == AllowListRuleType.ALLOW_SUPPLIER_ITEM) {
      return getSupplierViolationDetails(
          component.getPackageName(), component.getPackageOriginatorName(), allowListItem.getSuppliers());
    } else if (type == AllowListRuleType.ALLOW_PURL_ITEM) {
      return getPurlViolationDetails(component.getPackageName(), component.getPurl(), allowListItem.getPurls());
    } else {
      throw new IllegalArgumentException(String.format("Violation Details not Implemented for type: %s", type.name()));
    }
  }

  @Override
  public String getViolationDetails(NormalizedSBOMComponentEntity component, DenyListItem denyListItem) {
    String violation = String.format("%s is denied:", component.getPackageName());
    Field[] fields = denyListItem.getClass().getDeclaredFields();

    for (Field f : fields) {
      String fieldName = f.getName();
      String fieldValue;
      try {
        f.setAccessible(true);
        fieldValue = (String) f.get(denyListItem);
      } catch (IllegalAccessException e) {
        throw new BadRequestException("Error getting field value");
      }

      if (fieldValue != null) {
        violation += String.format(" %s is %s,", fieldName, fieldValue);
      }
    }

    return violation.substring(0, violation.length() - 1);
  }

  @Override
  public void create(EnforcementResultDTO enforcementResultDTO) {
    enforcementResultRepo.save(EnforcementResultTransformer.toEntity(enforcementResultDTO));
  }

  private String getSupplierViolationDetails(String packageName, String packageSupplier, List<Supplier> suppliers) {
    List<String> allowedSuppliers = new ArrayList<>();
    for (Supplier supplier : suppliers) {
      if (supplier.getName() == null) {
        allowedSuppliers.add(supplier.getSupplier());
      } else if (supplier.getName().equals(packageName)) {
        if (supplier.getSupplier().contains("!")) {
          String deniedSupplier = supplier.getSupplier().split("!")[1];
          return String.format("Supplier for %s should not be %s", packageName, deniedSupplier);
        }
        return String.format(
            "Supplier for %s needs to be: %s, but got: %s", packageName, supplier.getSupplier(), packageSupplier);
      }
    }

    return String.format("Supplier for %s needs to be: %s, but got: %s", packageName,
        String.join(" or ", allowedSuppliers), packageSupplier);
  }

  private String getLicenseViolationDetails(
      String packageName, List<String> packageLicense, List<AllowLicense> licenses) {
    List<String> allowedLicenses = new ArrayList<>();
    for (AllowLicense license : licenses) {
      if (license.getName() == null) {
        allowedLicenses.add(license.getLicense());
      } else if (license.getName().equals(packageName)) {
        if (license.getLicense().contains("!")) {
          String deniedLicense = license.getLicense().split("!")[1];
          return String.format("License for %s should not be %s", packageName, deniedLicense);
        }
        return String.format(
            "License for %s needs to be: %s, but got: %s", packageName, license.getLicense(), packageLicense);
      }
    }

    return String.format("License for %s needs to be: %s, but got: %s", packageName,
        String.join(" or ", allowedLicenses), packageLicense);
  }

  private String getPurlViolationDetails(String packageName, String packagePurl, List<String> purls) {
    for (String purl : purls) {
      if (purl.contains(packageName)) {
        return String.format("Purl for %s needs to be of the format %s, but got: %s", packageName, purl, packagePurl);
      }
    }
    return null;
  }
}
