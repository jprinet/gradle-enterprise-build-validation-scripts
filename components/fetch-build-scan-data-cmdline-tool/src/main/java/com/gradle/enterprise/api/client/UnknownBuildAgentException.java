package com.gradle.enterprise.api.client;

import java.net.URL;

public class UnknownBuildAgentException extends ApiClientException {
    public UnknownBuildAgentException(String buildAgent, String buildScanId, URL gradleEnterpriseServer) {
        super(String.format("Build scan %s was generated by an unknown build agent: %s.", buildScanUrl(gradleEnterpriseServer, buildScanId), buildAgent));
    }
}
