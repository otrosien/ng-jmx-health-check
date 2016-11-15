# JMX command-line client to access the Spring Boot health endpoint

First, start your spring boot process with JMX remote agent enabled.
See http://docs.oracle.com/javase/8/docs/technotes/guides/management/agent.html

Then, access the jmx port (9010 in the example below) from this command-line client.

Example usage:

```
./gradlew build
java -Xmx20m -Xms20m -XX:+UseParallelGC -jar build/libs/jmx_spring_health-1.0.jar -U service:jmx:rmi:///jndi/rmi://localhost:9010/jmxrmi -O org.springframework.boot:type=Endpoint,name=healthEndpoint -o getData
```

