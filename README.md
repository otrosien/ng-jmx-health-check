# JMX command-line client to access the Spring Boot health endpoint

First, start your spring boot process with JMX remote agent enabled.
See http://docs.oracle.com/javase/8/docs/technotes/guides/management/agent.html

Then, access the jmx port (9010 in the example below) from this command-line client.

Example usage:

```
./gradlew build
java -Xmx20m -Xms20m -XX:+UseParallelGC -jar build/libs/jmx_spring_health-1.0.jar -U service:jmx:rmi:///jndi/rmi://localhost:9010/jmxrmi -O org.springframework.boot:type=Endpoint,name=healthEndpoint -o getData
```

## Result

You can use the result printed out on STDOUT, but for health checking it is more advisable to use the process exit code. Here is an example:

```
status=DOWN, diskSpace={status=UP, total=190163431424, free=16598224896, threshold=10485760}, rabbit={status=DOWN, error=org.springframework.amqp.AmqpConnectException: java.net.ConnectException: Connection refused}, refreshScope={status=UP}, configServer={status=UNKNOWN, error=no property sources located}
```

Process exit codes: 0=OK, 1=CRITICAL, 2=UNKNOWN

## Acknowledgements

* Code derived from nagios JMX plugin. See https://sourceforge.net/projects/nagioscheckjmx/

