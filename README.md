# JMX command-line client to access the Spring Boot health endpoint


## Building

```
./gradlew distZip
```

## Usage

```
Usage: jmx-health-check -U <service_url> -O <object_name> 
  -o <operation_name> [--username <username>] [--password <password>] [-h]


Options are:

-h
    Help page, this page.
	
-U 
    JMX URL; default: "service:jmx:rmi://localhost:1234/jmxrmi"
	
-O 
    Object name to be checked, for default: "org.springframework.boot:type=Endpoint,name=healthEndpoint"
    
-o
    Operation to invoke on MBean after querying value, default: "getData"

--username
    Username, if JMX access is restricted; for example "monitorRole"
	
--password
    Password
```

## Example execution

First, start your spring boot process with JMX remote agent enabled.
See http://docs.oracle.com/javase/8/docs/technotes/guides/management/agent.html

Spring Boot by default exposes its actuator endpoint for health checks on the 
JMX object `org.springframework.boot` with `type=Endpoint` and `name=healthEndpoint`.
The JMX domain can be changed by setting the `spring.jmx.default-domain` property.

Then, access the jmx port (1234 in the example below) from this command-line client. 
You can use the result printed out on STDOUT, but for health checking it is more 
advisable to use the process exit code. Here is an example:

```
jmx-health-check -U service:jmx:rmi:///jndi/rmi://localhost:9010/jmxrmi -O org.springframework.boot:type=Endpoint,name=healthEndpoint -o getData
```

Result:

```
status=DOWN, diskSpace={status=UP, total=190163431424, free=16598224896, threshold=10485760}, rabbit={status=DOWN, error=org.springframework.amqp.AmqpConnectException: java.net.ConnectException: Connection refused}, refreshScope={status=UP}, configServer={status=UNKNOWN, error=no property sources located}
```

Process exit codes: 
* 0=OK - The JMX result does not have a "status", or the status is "UP".
* 1=CRITICAL - The JMX result has a "status" property, and it is not "UP".
* 2=UNKNOWN - The command-line arguments are incorrect.

## Acknowledgements

* Code derived from nagios JMX plugin. See https://sourceforge.net/projects/nagioscheckjmx/
