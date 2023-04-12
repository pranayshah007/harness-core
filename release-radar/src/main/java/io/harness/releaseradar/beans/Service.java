package io.harness.releaseradar.beans;

public enum Service {
    NG_MANAGER ("ng"),
    PIPELINE_SERVICE ("pipeline"),
    MANAGER ("");

    String versionUrlKeyword;

    Service(String versionUrlKeyword) {
        this.versionUrlKeyword = versionUrlKeyword;
    }

    public String getVersionUrlKeyword() {
        return versionUrlKeyword;
    }

    public static Service getService(String serviceName) {
        return Service.valueOf(serviceName);
    }
}
