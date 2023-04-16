package org.miun.constants;

import java.util.List;

public interface Constants {
    String DESIGNITE_JAR_PATH = "/home/alexander/tools/DesigniteJava.jar";
    String JACOCO_CLI_PATH = "/home/alexander/tools/jacococli.jar";
    String RESULTS_DIRECTORY = "/home/alexander/dt133g-files/snapshot-results";
    String JACOCO_AGENT_PATH = "/home/alexander/tools/jacocoagent.jar";
    String BASE_SNAPSHOT_DIRECTORY = "/home/alexander/dt133g-files/snapshots";
    List<String> OSS_PROJECTS = List.of("https://github.com/apolloconfig/apollo", "https://github.com/seata/seata",
            "https://github.com/GoogleContainerTools/jib", "https://github.com/apache/dolphinscheduler", "https://github.com/karatelabs/karate",
            "https://github.com/spring-cloud/spring-cloud-gateway", "https://github.com/apache/servicecomb-java-chassis",
            "https://github.com/box/mojito", "https://github.com/piranhacloud/piranha", "https://github.com/strimzi/strimzi-kafka-operator");
}

