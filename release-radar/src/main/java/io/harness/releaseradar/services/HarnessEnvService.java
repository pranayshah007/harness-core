package io.harness.releaseradar.services;

import io.harness.releaseradar.beans.EnvDeploymentStatus;
import io.harness.releaseradar.beans.Environment;
import io.harness.releaseradar.beans.Service;

import java.util.Map;

public interface HarnessEnvService {
    Map<Environment, EnvDeploymentStatus> getDeploymentStatusForAllEnvs(Service service);

    EnvDeploymentStatus getDeploymentStatus(Service service, Environment environment);
}
