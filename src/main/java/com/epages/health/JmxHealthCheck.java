package com.epages.health;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class JmxHealthCheck {

    enum Status {
        OK(0), CRITICAL(1), UNKNOWN(2);

        private int exitCode;

        private Status(int exitCode) {
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }

    }

    /**
     * Username system property.
     */
    public static final String PROP_USERNAME = "username";
    /**
     * Password system property.
     */
    public static final String PROP_PASSWORD = "password";
    /**
     * Object name system property.
     */
    public static final String PROP_OBJECT_NAME = "objectName";
    /**
     * Service URL system property.
     */
    public static final String PROP_SERVICE_URL = "serviceUrl";
    /**
     * Operation to invoke on MBean.
     */
    public static final String PROP_OPERATION = "operation";
    /**
     * Help output.
     */
    public static final String PROP_HELP = "help";

    /**
     * Open a connection to a MBean server.
     * 
     * @param serviceUrl
     *            Service URL, e.g.
     *            service:jmx:rmi://HOST:PORT/jndi/rmi://HOST:PORT/jmxrmi
     * @param username
     *            Username
     * @param password
     *            Password
     * @return MBeanServerConnection if succesfull.
     * @throws IOException
     *             XX
     */
    public JMXConnector openConnection(JMXServiceURL serviceUrl, String username, String password) throws IOException {
        HashMap<String, Object> environment = new HashMap<>();
        // Add environment variable to check for dead connections.
        if (username != null && password != null) {
            environment.put(JMXConnector.CREDENTIALS, new String[] { username, password });
        } else {
            environment.put("jmx.remote.x.client.connection.check.period", 5000);
        }
        return JMXConnectorFactory.connect(serviceUrl, environment);
    }

    /**
     * Get object name object.
     * 
     * @param connection
     *            MBean server connection.
     * @param objectName
     *            Object name string.
     * @return Object name object.
     * @throws InstanceNotFoundException
     *             If object not found.
     * @throws MalformedObjectNameException
     *             If object name is malformed.
     * @throws IOException
     *             In case of a communication error.
     */
    public ObjectName getObjectName(MBeanServerConnection connection, String objectName) throws Exception {
        ObjectName objName = new ObjectName(objectName);
        if (objName.isPropertyPattern() || objName.isDomainPattern()) {
            Set<ObjectInstance> mBeans = connection.queryMBeans(objName, null);

            if (mBeans.isEmpty()) {
                throw new InstanceNotFoundException();
            } else if (mBeans.size() > 1) {
                throw new IllegalArgumentException(
                        "Object name not unique: objectName pattern matches " + mBeans.size() + " MBeans.");
            } else {
                objName = mBeans.iterator().next().getObjectName();
            }
        }
        return objName;
    }

    /**
     * Invoke an operation on MBean.
     * 
     * @param connection
     *            MBean server connection.
     * @param objectName
     *            Object name.
     * @param operationName
     *            Operation name.
     * @throws InstanceNotFoundException
     *             XX
     * @throws IOException
     *             XX
     * @throws MalformedObjectNameException
     *             XX
     * @throws MBeanException
     *             XX
     * @throws ReflectionException
     *             XX
     */
    public Object invoke(MBeanServerConnection connection, String objectName, String operationName) throws Exception {
        ObjectName objName = getObjectName(connection, objectName);
        return connection.invoke(objName, operationName, null, null);
    }

    /**
     * Get system properties and execute query.
     * 
     * @param args
     *            Arguments as properties.
     * @return Exit code.
     * @throws JsonProcessingException
     */
    public int execute(Properties args) throws Exception {
        String username = args.getProperty(PROP_USERNAME);
        String password = args.getProperty(PROP_PASSWORD);
        String objectName = args.getProperty(PROP_OBJECT_NAME);
        String serviceUrl = args.getProperty(PROP_SERVICE_URL);
        String operation = args.getProperty(PROP_OPERATION);
        String help = args.getProperty(PROP_HELP);

        PrintStream out = System.out;

        if (help != null) {
            showHelp();
            return Status.OK.getExitCode();
        }

        if (objectName == null || operation == null || serviceUrl == null) {
            showUsage();
            return Status.UNKNOWN.getExitCode();
        }

        JMXServiceURL url = new JMXServiceURL(serviceUrl);
        try (JMXConnector connector = openConnection(url, username, password)) {
            MBeanServerConnection connection = connector.getMBeanServerConnection();
            Object value = invoke(connection, objectName, operation);
            if (value != null) {
                Status status = Status.OK;

                if (value instanceof Map) {
                    Map<?, ?> mapValue = (Map<?, ?>) value;
                    if (mapValue.containsKey("status")) {
                        status = "UP".equals(mapValue.get("status")) ? Status.OK : Status.CRITICAL;
                    }
                    value = mapValue.entrySet() //
                            .stream() //
                            .map(entry -> entry.getKey() + "=" + entry.getValue()) //
                            .collect(Collectors.joining(", "));
                } else if (value instanceof List) {
                    value = ((List<?>) value).stream().map(String::valueOf).collect(Collectors.joining(", "));
                } else {
                    value = String.valueOf(value);
                }
                out.println(value);
                return status.getExitCode();
            } else {
                out.println("Value not set. JMX query returned null value.");
                return Status.CRITICAL.getExitCode();
            }
        }
    }

    /**
     * Main method.
     * 
     * @param args
     *            Command line arguments.
     */
    public static void main(String[] args) {
        PrintStream out = System.out;
        JmxHealthCheck plugin = new JmxHealthCheck();
        int exitCode;
        Properties props = parseArguments(args);
        try {
            exitCode = plugin.execute(props);
        } catch (Throwable e) {
            out.println(e.getMessage());
            e.printStackTrace(System.err);
            exitCode = Status.CRITICAL.getExitCode();
        }
        System.exit(exitCode);
    }

    /**
     * Show usage.
     * 
     */
    private void showUsage() {
        outputResource(getClass().getResource("usage.txt"));
    }

    /**
     * Show help.
     * 
     */
    private void showHelp() {
        outputResource(getClass().getResource("help.txt"));
    }

    /**
     * Output resource.
     * 
     * @param url
     *            Resource URL.
     */
    private void outputResource(URL url) {
        PrintStream out = System.out;
        try (Reader r = new InputStreamReader(url.openStream())) {
            StringBuilder sbHelp = new StringBuilder();
            char[] buffer = new char[1024];
            for (int len = r.read(buffer); len != -1; len = r.read(buffer)) {
                sbHelp.append(buffer, 0, len);
            }
            out.println(sbHelp.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse command line arguments.
     * 
     * @param args
     *            Command line arguments.
     * @return Command line arguments as properties.
     */
    private static Properties parseArguments(String[] args) {
        Properties props = new Properties();
        int i = 0;
        while (i < args.length) {
            if ("-h".equals(args[i]))
                props.put(PROP_HELP, "");
            else if ("-U".equals(args[i]))
                props.put(PROP_SERVICE_URL, args[++i]);
            else if ("-O".equals(args[i]))
                props.put(PROP_OBJECT_NAME, args[++i]);
            else if ("--username".equals(args[i]))
                props.put(PROP_USERNAME, args[++i]);
            else if ("--password".equals(args[i]))
                props.put(PROP_PASSWORD, args[++i]);
            else if ("-o".equals(args[i]))
                props.put(PROP_OPERATION, args[++i]);
            i++;
        }
        return props;
    }
}
