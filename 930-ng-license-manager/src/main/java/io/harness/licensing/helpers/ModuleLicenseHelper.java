/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.ModuleType;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.checks.ModuleLicenseState;
import io.harness.licensing.entities.modules.CDModuleLicense;
import io.harness.licensing.entities.modules.CEModuleLicense;
import io.harness.licensing.entities.modules.CETModuleLicense;
import io.harness.licensing.entities.modules.CFModuleLicense;
import io.harness.licensing.entities.modules.CIModuleLicense;
import io.harness.licensing.entities.modules.ChaosModuleLicense;
import io.harness.licensing.entities.modules.IACMModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.licensing.entities.modules.SRMModuleLicense;
import io.harness.licensing.entities.modules.STOModuleLicense;
import io.harness.subscription.params.UsageKey;

import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@Singleton
public class ModuleLicenseHelper {
  private final String MODULE_NOT_SUPPORTED_ERROR = "Module %s is not supported for recommendations.";

  public static Map<ModuleType, ModuleLicense> getLastExpiredLicenseForEachModuleType(
      List<ModuleLicense> allModuleLicenses) {
    Map<ModuleType, ModuleLicense> result = new HashMap<>();
    for (ModuleType moduleType : ModuleType.getModules()) {
      if (!moduleType.isInternal()) {
        result.put(moduleType, null);
      }
    }

    allModuleLicenses.forEach(license -> {
      ModuleType licenseModuleType = license.getModuleType();
      result.compute(licenseModuleType, (k, v) -> {
        if (v == null) {
          return license;
        } else {
          // return last expired license
          if (license.getExpiryTime() > v.getExpiryTime()) {
            return license;
          }
        }
        return v;
      });
    });
    return result;
  }

  public static boolean isPaidEditionInOtherModules(
      ModuleType moduleType, Edition edition, Map<ModuleType, ModuleLicense> lastExpiredActiveLicenseMap) {
    return lastExpiredActiveLicenseMap.values().stream().anyMatch(license
        -> license != null && !moduleType.equals(license.getModuleType())
            && LicenseType.PAID.equals(license.getLicenseType()) && edition.equals(license.getEdition()));
  }

  public static ModuleLicense getLatestLicense(List<ModuleLicense> licenses) {
    if (isEmpty(licenses)) {
      return null;
    }

    ModuleLicense latestLicense = licenses.get(0);
    for (int i = 1; i < licenses.size(); i++) {
      ModuleLicense compareLicense = licenses.get(i);
      if (latestLicense.getExpiryTime() < compareLicense.getExpiryTime()) {
        latestLicense = compareLicense;
      }
    }
    return latestLicense;
  }

  public static EnumMap<UsageKey, Long> getUsageLimits(ModuleLicense moduleLicense) {
    EnumMap<UsageKey, Long> usageLimitMap = new EnumMap<>(UsageKey.class);

    switch (moduleLicense.getModuleType().name()) {
      case "CF":
        CFModuleLicense cfModuleLicense = (CFModuleLicense) moduleLicense;
        usageLimitMap.put(UsageKey.NUMBER_OF_USERS, (long) cfModuleLicense.getNumberOfUsers());
        usageLimitMap.put(UsageKey.NUMBER_OF_MAUS, cfModuleLicense.getNumberOfClientMAUs());
        break;
      case "CI":
        CIModuleLicense ciModuleLicense = (CIModuleLicense) moduleLicense;
        usageLimitMap.put(UsageKey.NUMBER_OF_COMMITTERS, (long) ciModuleLicense.getNumberOfCommitters());
        break;
      default:
        throw new InvalidRequestException(String.format(MODULE_NOT_SUPPORTED_ERROR, moduleLicense.getModuleType()));
    }

    return usageLimitMap;
  }

  public static ModuleLicenseDTO getMostRecentUpdatedLicense(List<ModuleLicenseDTO> licenses) {
    if (isEmpty(licenses)) {
      return null;
    }

    ModuleLicenseDTO lastUpdatedLicense = licenses.get(0);
    for (int i = 1; i < licenses.size(); i++) {
      ModuleLicenseDTO compareLicense = licenses.get(i);
      if (lastUpdatedLicense.getLastModifiedAt() < compareLicense.getLastModifiedAt()) {
        lastUpdatedLicense = compareLicense;
      }
    }
    return lastUpdatedLicense;
  }

  public static boolean isTrialExisted(List<ModuleLicense> licensesWithSameModuleType) {
    return licensesWithSameModuleType.stream().anyMatch(
        license -> license.getTrialExtended() != null && license.getTrialExtended());
  }

  public static boolean isTrialLicenseUnderExtendPeriod(long expiryTime) {
    Duration duration = Duration.ofMillis(Instant.now().toEpochMilli() - expiryTime);
    return duration.toMillis() > 0 && duration.toDays() <= 14;
  }

  public static ModuleLicenseState getCurrentModuleState(List<ModuleLicense> currentLicenses) {
    if (currentLicenses.isEmpty()) {
      return ModuleLicenseState.NO_LICENSE;
    }

    ModuleLicense latestLicense = ModuleLicenseHelper.getLatestLicense(currentLicenses);
    Edition edition = latestLicense.getEdition();
    long currentTime = Instant.now().toEpochMilli();
    switch (edition) {
      case FREE:
        return ModuleLicenseState.ACTIVE_FREE;
      case TEAM:
        if (latestLicense.checkExpiry(currentTime)) {
          // license expired
          if (LicenseType.TRIAL.equals(latestLicense.getLicenseType())) {
            // expired trial
            if (currentLicenses.size() == 1
                && ModuleLicenseHelper.isTrialLicenseUnderExtendPeriod(latestLicense.getExpiryTime())
                && !ModuleLicenseHelper.isTrialExisted(currentLicenses)) {
              return ModuleLicenseState.EXPIRED_TEAM_TRIAL_CAN_EXTEND;
            } else {
              return ModuleLicenseState.EXPIRED_TEAM_TRIAL;
            }
          } else {
            // expired paid
            return ModuleLicenseState.EXPIRED_TEAM_PAID;
          }
        } else {
          // license active
          if (LicenseType.TRIAL.equals(latestLicense.getLicenseType())) {
            // active trial
            return ModuleLicenseState.ACTIVE_TEAM_TRIAL;
          } else {
            // active paid
            return ModuleLicenseState.ACTIVE_TEAM_PAID;
          }
        }
      case ENTERPRISE:
        if (latestLicense.checkExpiry(currentTime)) {
          // license expired
          if (LicenseType.TRIAL.equals(latestLicense.getLicenseType())) {
            // expired trial
            if (currentLicenses.size() == 1
                && ModuleLicenseHelper.isTrialLicenseUnderExtendPeriod(latestLicense.getExpiryTime())
                && !ModuleLicenseHelper.isTrialExisted(currentLicenses)) {
              return ModuleLicenseState.EXPIRED_ENTERPRISE_TRIAL_CAN_EXTEND;
            } else {
              return ModuleLicenseState.EXPIRED_ENTERPRISE_TRIAL;
            }
          } else {
            // expired paid
            return ModuleLicenseState.EXPIRED_ENTERPRISE_PAID;
          }
        } else {
          // license active
          if (LicenseType.TRIAL.equals(latestLicense.getLicenseType())) {
            // active trial
            return ModuleLicenseState.ACTIVE_ENTERPRISE_TRIAL;
          } else {
            // active paid
            return ModuleLicenseState.ACTIVE_ENTERPRISE_PAID;
          }
        }
      default:
        throw new UnsupportedOperationException(String.format("Edition [%s] is not supported", edition));
    }
  }

  public static ModuleLicense compareAndUpdate(ModuleLicense current, ModuleLicense update) {
    if (update.getLicenseType() != null && !update.getLicenseType().equals(current.getLicenseType())) {
      current.setLicenseType(update.getLicenseType());
    }
    if (update.getEdition() != null && !update.getEdition().equals(current.getEdition())) {
      current.setEdition(update.getEdition());
    }
    if (update.getExpiryTime() != 0 && update.getExpiryTime() != current.getExpiryTime()) {
      current.setExpiryTime(update.getExpiryTime());
    }
    if (update.getStatus() != null && !update.getStatus().equals(current.getStatus())) {
      current.setStatus(update.getStatus());
    }
    if (update.getLicenseType() != null && !update.getLicenseType().equals(current.getLicenseType())) {
      current.setLicenseType(update.getLicenseType());
    }
    if (update.getTrialExtended() != null && !update.getTrialExtended().equals(current.getTrialExtended())) {
      current.setTrialExtended(update.getTrialExtended());
    }

    if (update.isPremiumSupport() != current.isPremiumSupport()) {
      current.setPremiumSupport(update.isPremiumSupport());
    }

    if (update.isSelfService() != current.isSelfService()) {
      current.setSelfService(update.isSelfService());
    }

    switch (update.getModuleType()) {
      case CD:
        CDModuleLicense cdLicense = (CDModuleLicense) update;
        CDModuleLicense currentCDLicense = (CDModuleLicense) current;
        if (cdLicense.getCdLicenseType() != null
            && !cdLicense.getCdLicenseType().equals(currentCDLicense.getCdLicenseType())) {
          currentCDLicense.setCdLicenseType(cdLicense.getCdLicenseType());
        }
        if (cdLicense.getServiceInstances() != null
            && !cdLicense.getServiceInstances().equals(currentCDLicense.getServiceInstances())) {
          currentCDLicense.setServiceInstances(cdLicense.getServiceInstances());
        }
        if (cdLicense.getWorkloads() != null && !cdLicense.getWorkloads().equals(currentCDLicense.getWorkloads())) {
          currentCDLicense.setWorkloads(cdLicense.getWorkloads());
        }
        break;
      case CE:
        CEModuleLicense ceLicense = (CEModuleLicense) update;
        CEModuleLicense currentCELicense = (CEModuleLicense) current;
        if (ceLicense.getSpendLimit() != null && !ceLicense.getSpendLimit().equals(currentCELicense.getSpendLimit())) {
          currentCELicense.setSpendLimit(ceLicense.getSpendLimit());
        }
        if (ceLicense.getStartTime() != 0 && ceLicense.getStartTime() != currentCELicense.getStartTime()) {
          currentCELicense.setStartTime(ceLicense.getStartTime());
        }
        break;
      case CF:
        CFModuleLicense cfLicense = (CFModuleLicense) update;
        CFModuleLicense currentCFLicense = (CFModuleLicense) current;
        if (cfLicense.getNumberOfClientMAUs() != null
            && !cfLicense.getNumberOfClientMAUs().equals(currentCFLicense.getNumberOfClientMAUs())) {
          currentCFLicense.setNumberOfClientMAUs(cfLicense.getNumberOfClientMAUs());
        }
        if (cfLicense.getNumberOfUsers() != null
            && !cfLicense.getNumberOfUsers().equals(currentCFLicense.getNumberOfUsers())) {
          currentCFLicense.setNumberOfUsers(cfLicense.getNumberOfUsers());
        }
        break;
      case CV:
      case SRM:
        SRMModuleLicense cvLicense = (SRMModuleLicense) update;
        SRMModuleLicense currentCVLicense = (SRMModuleLicense) current;
        if (cvLicense.getNumberOfServices() != null
            && !cvLicense.getNumberOfServices().equals(currentCVLicense.getNumberOfServices())) {
          currentCVLicense.setNumberOfServices(cvLicense.getNumberOfServices());
        }
        break;
      case CI:
        CIModuleLicense ciLicense = (CIModuleLicense) update;
        CIModuleLicense currentCILicense = (CIModuleLicense) current;
        if (ciLicense.getNumberOfCommitters() != null
            && !ciLicense.getNumberOfCommitters().equals(currentCILicense.getNumberOfCommitters())) {
          currentCILicense.setNumberOfCommitters(ciLicense.getNumberOfCommitters());
        }
        if (ciLicense.getCacheAllowance() != null
            && !ciLicense.getCacheAllowance().equals(currentCILicense.getCacheAllowance())) {
          currentCILicense.setCacheAllowance(ciLicense.getCacheAllowance());
        }
        if (ciLicense.getHostingCredits() != null
            && !ciLicense.getHostingCredits().equals(currentCILicense.getHostingCredits())) {
          currentCILicense.setHostingCredits(ciLicense.getHostingCredits());
        }
        break;
      case STO:
        STOModuleLicense stoLicense = (STOModuleLicense) update;
        STOModuleLicense currentSTOLicense = (STOModuleLicense) current;
        if (stoLicense.getNumberOfDevelopers() != null
            && !stoLicense.getNumberOfDevelopers().equals(currentSTOLicense.getNumberOfDevelopers())) {
          currentSTOLicense.setNumberOfDevelopers(stoLicense.getNumberOfDevelopers());
        }
        break;
      case CHAOS:
        ChaosModuleLicense chaosLicense = (ChaosModuleLicense) update;
        ChaosModuleLicense currentCHAOSLicense = (ChaosModuleLicense) current;
        if (chaosLicense.getTotalChaosInfrastructures() != null
            && !chaosLicense.getTotalChaosInfrastructures().equals(
                currentCHAOSLicense.getTotalChaosInfrastructures())) {
          currentCHAOSLicense.setTotalChaosInfrastructures(chaosLicense.getTotalChaosInfrastructures());
        }
        if (chaosLicense.getTotalChaosExperimentRuns() != null
            && !chaosLicense.getTotalChaosExperimentRuns().equals(currentCHAOSLicense.getTotalChaosExperimentRuns())) {
          currentCHAOSLicense.setTotalChaosExperimentRuns(chaosLicense.getTotalChaosExperimentRuns());
        }
        break;
      case IACM:
        IACMModuleLicense iacmLicense = (IACMModuleLicense) update;
        IACMModuleLicense currentIACMLicense = (IACMModuleLicense) current;
        if (iacmLicense.getNumberOfDevelopers() != null
            && !iacmLicense.getNumberOfDevelopers().equals(currentIACMLicense.getNumberOfDevelopers())) {
          currentIACMLicense.setNumberOfDevelopers(iacmLicense.getNumberOfDevelopers());
        }
        break;
      case CET:
        CETModuleLicense cetLicense = (CETModuleLicense) update;
        CETModuleLicense currentCETLicense = (CETModuleLicense) current;
        if (cetLicense.getNumberOfAgents() != null
            && !cetLicense.getNumberOfAgents().equals(currentCETLicense.getNumberOfAgents())) {
          currentCETLicense.setNumberOfAgents(cetLicense.getNumberOfAgents());
        }
        break;
      default:
        // Do nothing
        break;
    }
    return current;
  }
}
