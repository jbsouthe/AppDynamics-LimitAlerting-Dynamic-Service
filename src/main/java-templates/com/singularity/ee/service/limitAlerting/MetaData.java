package com.singularity.ee.service.limitAlerting;

import java.util.HashMap;
import java.util.Map;

public class MetaData {
    public static final String VERSION = "v${version}";
    public static final String BUILDTIMESTAMP = "${build.time}";
    public static final String SERVICENAME = "Limit Alerting Service";
    public static final String GECOS = "John Southerland josouthe@cisco.com AppDynamics Field Architecture Team";
    public static final String GITHUB = "https://github.com/jbsouthe/AppDynamics-LimitAlerting-Dynamic-Service";
    public static final String DEVNET = "";
    public static final String SUPPORT = "/issues";


    public static Map<String,String> getAsMap() {
        Map<String,String> map = new HashMap<>();
        map.put("plugin-version", VERSION);
        map.put("plugin-name", SERVICENAME);
        map.put("plugin-buildTimestamp", BUILDTIMESTAMP);
        map.put("plugin-developer", GECOS);
        map.put("plugin-github", GITHUB);
        map.put("plugin-devnet", DEVNET);
        map.put("plugin-support", SUPPORT);
        return map;
    }
}
