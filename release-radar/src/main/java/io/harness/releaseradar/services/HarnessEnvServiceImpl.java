package io.harness.releaseradar.services;

import io.harness.releaseradar.beans.EnvDeploymentStatus;
import io.harness.releaseradar.beans.Environment;
import io.harness.releaseradar.beans.Service;
import io.harness.releaseradar.clients.HarnessClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class HarnessEnvServiceImpl implements HarnessEnvService {
    HarnessClient harnessClient = new HarnessClient();

    @Override
    public Map<Environment, EnvDeploymentStatus> getDeploymentStatusForAllEnvs(Service service) {
        Map<Environment, EnvDeploymentStatus> deploymentStatusMap = new HashMap<>();

        Arrays.stream(Environment.values()).forEach(environment -> {
            deploymentStatusMap.put(environment, getDeploymentStatus(service, environment));
        });
        return deploymentStatusMap;
    }

    @Override
    public EnvDeploymentStatus getDeploymentStatus(Service service, Environment environment) {
        String url = environment.getVersionUrlTemplate();
        url = url.replace(Environment.SERVICE_PLACEHOLDER, service.getVersionUrlKeyword());
        HarnessClient.VersionInfo versionInfo = harnessClient.getCurrentVersionStatus(url);
        return EnvDeploymentStatus.toEnvDeploymentStatus(versionInfo);
    }
}
