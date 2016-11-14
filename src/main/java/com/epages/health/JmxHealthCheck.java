package com.epages.health;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.ConnectException;
import java.util.HashMap;
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
        OK (0),
        CRITICAL (2),
        UNKNOWN (3);

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
     * Verbose output.
     */
    public static final String PROP_VERBOSE = "verbose";
    /**
     * Help output.
     */
    public static final String PROP_HELP = "help";

    private HashMap<MBeanServerConnection, JMXConnector> connections =
        new HashMap<MBeanServerConnection, JMXConnector>();

    /**
     * Open a connection to a MBean server.
     * @param serviceUrl Service URL,
     *     e.g. service:jmx:rmi://HOST:PORT/jndi/rmi://HOST:PORT/jmxrmi
     * @param username Username
     * @param password Password
     * @return MBeanServerConnection if succesfull.
     * @throws IOException XX
     */
    public MBeanServerConnection openConnection(
            JMXServiceURL serviceUrl, String username, String password)
    throws IOException
    {
        JMXConnector connector;
        HashMap<String, Object> environment = new HashMap<>();
        // Add environment variable to check for dead connections.
        environment.put("jmx.remote.x.client.connection.check.period", 5000);
        if (username != null && password != null) {
            environment = new HashMap<>();
            environment.put(JMXConnector.CREDENTIALS,
                    new String[] {username, password});
            connector = JMXConnectorFactory.connect(serviceUrl, environment);
        } else {
            connector = JMXConnectorFactory.connect(serviceUrl, environment);
        }
        MBeanServerConnection connection = connector.getMBeanServerConnection();
        connections.put(connection, connector);
        return connection;
    }

    /**
     * Close JMX connection.
     * @param connection Connection.
     * @throws IOException XX.
     */
    public void closeConnection(MBeanServerConnection connection)
    throws IOException
    {
        JMXConnector connector = connections.remove(connection);
        if (connector != null)
            connector.close();
    }

    /**
     * Get object name object.
     * @param connection MBean server connection.
     * @param objectName Object name string.
     * @return Object name object.
     * @throws InstanceNotFoundException If object not found.
     * @throws MalformedObjectNameException If object name is malformed.
     * @throws NagiosJmxPluginException If object name is not unqiue.
     * @throws IOException In case of a communication error.
     */
    public ObjectName getObjectName(MBeanServerConnection connection,
            String objectName)
    throws InstanceNotFoundException, MalformedObjectNameException, IOException
    {
        ObjectName objName = new ObjectName(objectName);
        if (objName.isPropertyPattern() || objName.isDomainPattern()) {
            Set<ObjectInstance> mBeans = connection.queryMBeans(objName, null);

            if (mBeans.isEmpty()) {
                throw new InstanceNotFoundException();
            } else if (mBeans.size() > 1) {
                throw new IllegalArgumentException(
                        "Object name not unique: objectName pattern matches " +
                        mBeans.size() + " MBeans.");
            } else {
                objName = mBeans.iterator().next().getObjectName();
            }
        }
        return objName;
    }

    /**
     * Invoke an operation on MBean.
     * @param connection MBean server connection.
     * @param objectName Object name.
     * @param operationName Operation name.
     * @throws InstanceNotFoundException XX
     * @throws IOException XX
     * @throws MalformedObjectNameException XX
     * @throws MBeanException XX
     * @throws ReflectionException XX
     * @throws NagiosJmxPluginException XX
     */
    public Object invoke(MBeanServerConnection connection, String objectName,
            String operationName)
    throws InstanceNotFoundException, IOException,
            MalformedObjectNameException, MBeanException, ReflectionException
    {
        ObjectName objName = getObjectName(connection, objectName);
        return connection.invoke(objName, operationName, null, null);
    }

    /**
     * Get system properties and execute query.
     * @param args Arguments as properties.
     * @return Nagios exit code.
     * @throws JsonProcessingException 
     * @throws NagiosJmxPluginException XX
     */
    public int execute(Properties args) {
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

        if (objectName == null || operation == null || serviceUrl == null)
        {
            showUsage();
            return Status.CRITICAL.getExitCode();
        }

        JMXServiceURL url = null;
        try {
            url = new JMXServiceURL(serviceUrl);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed service URL [" +
                    serviceUrl + "]", e);
        }
        // Connect to MBean server.
        MBeanServerConnection connection = null;
        Object value = null;
        try {
            try {
                connection = openConnection(url, username, password);
            } catch (ConnectException ce) {
                throw new IllegalArgumentException(
                        "Error opening RMI connection: " + ce.getMessage(), ce);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Error opening connection: " + e.getMessage(), e);
            }
            // Invoke operation if defined.
            if (operation != null) {
                try {
                    value = invoke(connection, objectName, operation);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Error invoking operation [" + operation + "]: " +
                            e.getMessage(), e);
                }
            }
        } finally {
            if (connection != null) {
                try {
                    closeConnection(connection);
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Error closing JMX connection", e);
                }
            }
        }
        int exitCode;
        if (value != null) {
            Status status;
            if (value instanceof Number || value instanceof String) {
                status = Status.OK;
            } else if (value instanceof Map) {
                status = "UP".equals(((Map<?,?>)value).get("status")) ? Status.OK : Status.CRITICAL;
                value = ((Map<?,?>)value).entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(", "));
            } else {
                throw new IllegalArgumentException(
                        "Type of return value not supported [" +
                        value.getClass().getName() + "]. Must be either a " +
                        "Number or String object.");
            }
            outputStatus(out, value);
            out.println();
            exitCode = status.getExitCode();
        } else {
            out.println("Value not set. JMX query returned null value.");
            exitCode = Status.CRITICAL.getExitCode();
        }
        return exitCode;
    }

    /**
     * Output status.
     * @param out Print stream.
     * @param status Status.
     * @param objectName Object name.
     * @param value Value
     * @param unit Unit.
     */
    private void outputStatus(PrintStream out, Object value)
    {
        out.print(value);
    }

    /**
     * Main method.
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        PrintStream out = System.out;
        JmxHealthCheck plugin = new JmxHealthCheck();
        int exitCode;
        Properties props = parseArguments(args);
        String verbose = props.getProperty(PROP_VERBOSE);
        try {
            exitCode = plugin.execute(props);
        } catch (Exception e) {
            out.println(e.getMessage());
            if (verbose != null)
                e.printStackTrace(System.out);
            exitCode = Status.UNKNOWN.getExitCode();
        }
        System.exit(exitCode);
    }

    /**
     * Show usage.
     * @throws NagiosJmxPluginException XX
     */
    private void showUsage() {
        outputResource(getClass().getResource("usage.txt"));
    }

    /**
     * Show help.
     * @throws NagiosJmxPluginException XX
     */
    private void showHelp() {
        outputResource(getClass().getResource("help.txt"));
    }

    /**
     * Output resource.
     * @param url Resource URL.
     * @throws NagiosJmxPluginException XX
     */
    private void outputResource(URL url) {
        PrintStream out = System.out;
        try {
            Reader r = new InputStreamReader(url.openStream());
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
     * @param args Command line arguments.
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
            else if ("-A".equals(args[i]))
                props.put(PROP_VERBOSE, "true");
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
