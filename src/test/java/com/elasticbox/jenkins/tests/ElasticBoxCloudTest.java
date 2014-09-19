/*
 * ElasticBox Confidential
 * Copyright (c) 2014 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.tests;

import com.elasticbox.Client;
import com.elasticbox.ClientException;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.ElasticBoxSlave;
import com.elasticbox.jenkins.SlaveConfiguration;
import com.elasticbox.jenkins.util.SlaveInstance;
import com.elasticbox.jenkins.util.VariableResolver;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.Scrambler;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.jvnet.hudson.test.HudsonTestCase;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.TextParameterValue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author Phong Nguyen Le
 */
public class ElasticBoxCloudTest extends HudsonTestCase {
    private static final String OPS_USER_NAME_PROPERTY = "elasticbox.jenkins.test.opsUsername";
    private static final String OPS_PASSWORD_PROPERTY = "elasticbox.jenkins.test.opsPassword";
    
    private static final String TEST_WORKSPACE="tphongio";
    private static final String JENKINS_SLAVE_BOX_NAME = "test-linux-jenkins-slave";
    private static final String TEST_BINDING_BOX_NAME = "test-binding-box";
    private static final String TEST_LINUX_BOX_NAME = "test-linux-box";
    private static final String TEST_NESTED_BOX_NAME = "test-nested-box";    
    
    private static final String JENKINS_PUBLIC_HOST = System.getProperty("elasticbox.jenkins.test.jenkinsPublicHost" ,"localhost");
    private static final String ELASTICBOX_URL = System.getProperty("elasticbox.jenkins.test.ElasticBoxURL", "https://catapult.elasticbox.com");
    private static final String USER_NAME = System.getProperty("elasticbox.jenkins.test.username", Scrambler.descramble("dHBob25naW9AZ21haWwuY29t"));
    private static final String PASSWORD = System.getProperty("elasticbox.jenkins.test.password", Scrambler.descramble("dHBob25naW8="));
    
    private static final Logger LOGGER = Logger.getLogger(ElasticBoxCloudTest.class.getName());

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        
        String jenkinsUrl = jenkins.getRootUrl();
        if (StringUtils.isBlank(jenkinsUrl)) {
            jenkinsUrl = createWebClient().getContextPath();
        }
        
        jenkinsUrl = jenkinsUrl.replace("localhost", JENKINS_PUBLIC_HOST);
        JenkinsLocationConfiguration.get().setUrl(jenkinsUrl);
    }
    
    

    @Override
    protected void tearDown() throws Exception {
        List<ElasticBoxSlave> slaves = new ArrayList<ElasticBoxSlave>();
        for (Node node : jenkins.getNodes()) {
            if (node instanceof ElasticBoxSlave) {
                ElasticBoxSlave slave = (ElasticBoxSlave) node;
                if (slave.getInstanceId() != null) {
                    try {
                        slave.terminate();
                        slaves.add(slave);
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, "Error terminating slave", ex);
                    }
                }
            }
        }
        
        long maxWaitTime = 600000;
        long waitStart = System.currentTimeMillis();
        do {
            Thread.sleep(5000);
            for (Iterator<ElasticBoxSlave> iter = slaves.iterator(); iter.hasNext();) {
                ElasticBoxSlave slave = iter.next();               
                JSONObject instance = null;
                try {
                    instance = slave.getInstance();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "Error fetching slave instance", ex);
                    iter.remove();
                    continue;
                }
                
                if ((instance != null && Client.FINISH_STATES.contains(instance.getString("state"))) || 
                        System.currentTimeMillis() - waitStart > maxWaitTime) {
                    try {
                        slave.delete();
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, "Error deleting slave", ex);
                    }
                    iter.remove();
                }
            }
        } while (!slaves.isEmpty());
        
        super.tearDown();
    }

    
    public void testClient() throws Exception {
        ElasticBoxCloud cloud = createCloud();
        testClient(cloud);   
    }
    
    public void testBuild() throws Exception { 
        ElasticBoxCloud cloud = createCloud();
        testBuildWithOldSteps(cloud);
        testBuildWithSteps(cloud);
    }
    
    private ElasticBoxCloud createCloud() throws IOException {
        return createCloud(ELASTICBOX_URL, USER_NAME, PASSWORD);
    }
    
    private ElasticBoxCloud createCloud(String endpointUrl, String username, String password) throws IOException {
        ElasticBoxCloud cloud = new ElasticBoxCloud("elasticbox", endpointUrl, 2, 10, username, password, Collections.EMPTY_LIST);
        jenkins.clouds.add(cloud);
        return cloud;        
    }
    
    private void testClient(ElasticBoxCloud cloud) throws Exception {
        Client client = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        client.connect();
        JSONArray workspaces = client.getWorkspaces();
        JSONObject personalWorkspace = null;
        for (Object workspace : workspaces) {
            if (((JSONObject) workspace).getString("email") != null) {
                personalWorkspace = (JSONObject) workspace;
                break;
            }
        }
        assertNotNull(personalWorkspace);
        JSONArray boxes = client.getBoxes(personalWorkspace.getString("id"));
        JSONObject testJenkinsSlaveBox = null;
        for (Object box : boxes) {
            JSONObject boxJson = (JSONObject) box;
            if (boxJson.getString("name").equals(JENKINS_SLAVE_BOX_NAME)) {
                testJenkinsSlaveBox = boxJson;
                break;
            }
        }
        assertNotNull(testJenkinsSlaveBox);
        JSONArray profiles = client.getProfiles(personalWorkspace.getString("id"), testJenkinsSlaveBox.getString("id"));
        assertTrue(profiles.size() > 0);
        for (Object profile : profiles) {
            JSONObject profileJson = client.getProfile(((JSONObject) profile).getString("id"));
            assertEquals(profile.toString(), profileJson.toString());
        }        
        
        // make sure that a deployment request can be successfully submitted
        JSONObject profile = profiles.getJSONObject(0);
        JSONArray variables = SlaveInstance.createJenkinsVariables(jenkins.getRootUrl(), JENKINS_SLAVE_BOX_NAME);
        JSONObject variable = new JSONObject();
        variable.put("name", "JNLP_SLAVE_OPTIONS");
        variable.put("type", "Text");
        variable.put("value", MessageFormat.format("-jnlpUrl {0}/computer/{1}/slave-agent.jnlp", JENKINS_SLAVE_BOX_NAME));
        variables.add(variable);                        
        IProgressMonitor monitor = client.deploy(profile.getString("id"), profile.getString("owner"), 
                "jenkins-plugin-unit-test", 1, variables);
        try {
            monitor.waitForDone(60);
        } catch (IProgressMonitor.IncompleteException ex) {
            
        }
        
        String instanceId = Client.getResourceId(monitor.getResourceUrl());
        monitor = client.terminate(instanceId);
        monitor.waitForDone(60);
        client.delete(instanceId);
        try {
            client.getInstance(instanceId);
            throw new Exception(MessageFormat.format("Instance {0} was not deleted", instanceId));
        } catch (ClientException ex) {
            assertEquals(ex.getStatusCode(), HttpStatus.SC_NOT_FOUND);
        }
    }
    
    private void testBuildWithOldSteps(ElasticBoxCloud cloud) throws Exception {    
        String testParameter = UUID.randomUUID().toString();
        runJob("test-old-job", "TestOldJob.xml", Collections.singletonMap("eb_test_build_parameter", testParameter));
        
        Client client = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        JSONObject instance = client.getInstance("i-c51bop");
        JSONObject connectionVar = null;
        JSONObject httpsVar = null;
        for (Object json : instance.getJSONArray("variables")) {
            JSONObject variable = (JSONObject) json;
            String name = variable.getString("name");
            if (name.equals("CONNECTION")) {
                connectionVar = variable;                
            } else if (name.equals("HTTPS")) {
                httpsVar = variable;
            }
        }
        
        assertEquals(connectionVar.toString(), testParameter, connectionVar.getString("value"));
        assertFalse(httpsVar.toString(), httpsVar.getString("value").equals("${BUILD_ID}"));
    }
    
    private void testBuildWithSteps(ElasticBoxCloud cloud) throws Exception {    
        final String testTag = UUID.randomUUID().toString().substring(0, 30);
        Map<String, String> testParameters = Collections.singletonMap("TEST_TAG", testTag);
        FreeStyleBuild build = runJob("test", "TestJob.xml", testParameters);
        
        // validate the results of executed build steps   
        VariableResolver variableResolver = new VariableResolver(build, TaskListener.NULL);
        String buildNumber = variableResolver.resolve("${BUILD_NUMBER}");
        String buildId = variableResolver.resolve("${BUILD_ID}");
        String buildTag = variableResolver.resolve("${BUILD_TAG}");
        Client client = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        JSONObject testLinuxBox = null;
        JSONObject testBindingBox = null;
        JSONObject testNestedBox = null;        
        JSONArray boxes = client.getBoxes(TEST_WORKSPACE);
        for (Object box : boxes) {
            JSONObject boxJson = (JSONObject) box;
            String boxName = boxJson.getString("name");
            if (TEST_LINUX_BOX_NAME.equals(boxName)) {
                testLinuxBox = boxJson;
            } else if (TEST_BINDING_BOX_NAME.equals(boxName)) {
                testBindingBox = boxJson;
            } else if (TEST_NESTED_BOX_NAME.equals(boxName)) {
                testNestedBox = boxJson;
            }
        }
        assertNotNull(MessageFormat.format("Cannot find box {0}", TEST_LINUX_BOX_NAME), testLinuxBox);
        assertNotNull(MessageFormat.format("Cannot find box {0}", TEST_BINDING_BOX_NAME), testBindingBox);
        assertNotNull(MessageFormat.format("Cannot find box {0}", TEST_NESTED_BOX_NAME), testNestedBox);
        
        JSONArray instances = client.getInstances(TEST_WORKSPACE);
        List<String> instanceIDs = new ArrayList<String>();
        for (Object instance : instances) {
            JSONObject instanceJson = (JSONObject) instance;
            if (instanceJson.getJSONArray("tags").contains(testTag)) {
                instanceIDs.add(instanceJson.getString("id"));
            }
        }
        instances = client.getInstances(TEST_WORKSPACE, instanceIDs);
        
        JSONObject testLinuxBoxInstance = null;
        JSONObject testBindingBoxInstance1 = null;
        JSONObject testBindingBoxInstance2 = null;
        JSONObject testBindingBoxInstance3 = null;
        JSONObject testNestedBoxInstance = null;   
        Collection<String> testBindingBoxInstanceEnvironments = Arrays.asList(new String[] {
            testTag, buildNumber, buildTag
        });
        for (Object instance : instances) {
            JSONObject instanceJson = (JSONObject) instance;
            String mainBoxId = instanceJson.getJSONArray("boxes").getJSONObject(0).getString("id");
            String environment = instanceJson.getString("environment");
            if (mainBoxId.equals(testLinuxBox.getString("id"))) {
                assertNull(MessageFormat.format("The build deployed more than one instance of box {0}", TEST_LINUX_BOX_NAME), testLinuxBoxInstance);                    
                assertEquals(buildId, environment);
                testLinuxBoxInstance = instanceJson;
            } else if (mainBoxId.equals(testNestedBox.getString("id"))) {
                assertNull(MessageFormat.format("The build deployed more than one instance of box {0}", TEST_NESTED_BOX_NAME), testNestedBoxInstance);
                assertEquals(testTag, environment);
                testNestedBoxInstance = instanceJson;                    
            } else if (mainBoxId.equals(testBindingBox.getString("id"))) {
                assertTrue(MessageFormat.format("Unexpected instance with environment ''{0}'' has been deployed", environment),
                        testBindingBoxInstanceEnvironments.contains(environment));
                if (environment.equals(testTag)) {
                    assertNull(MessageFormat.format("The build deployed more than one instance of box {0} with environment ''{1}''", 
                            TEST_BINDING_BOX_NAME, testTag), testBindingBoxInstance1);
                    testBindingBoxInstance1 = instanceJson;
                } else if (environment.equals(buildNumber)) {
                    assertNull(MessageFormat.format("The build deployed more than one instance of box {0} with environment ''{1}''", 
                            TEST_BINDING_BOX_NAME, buildNumber), testBindingBoxInstance2);
                    testBindingBoxInstance2 = instanceJson;
                } else if (environment.equals(buildTag)) {                        
                    assertNull(MessageFormat.format("The build deployed more than one instance of box {0} with environment ''{1}''", 
                            TEST_BINDING_BOX_NAME, buildTag), testBindingBoxInstance3);
                    testBindingBoxInstance3 = instanceJson;
                } 
            } else {
                
            }           
        }
        
        assertNotNull(testLinuxBoxInstance);
        assertNotNull(testBindingBoxInstance1);
        assertNotNull(testBindingBoxInstance2);
        assertNotNull(testBindingBoxInstance3);
        assertNotNull(testNestedBoxInstance);
        
        // check test-linux-box instance
        assertTrue(MessageFormat.format("Instance {0} is not terminated", Client.getPageUrl(cloud.getEndpointUrl(), testLinuxBoxInstance)),
                Client.TERMINATE_OPERATIONS.contains(testLinuxBoxInstance.getString("operation")));
        JSONArray variables = testLinuxBoxInstance.getJSONArray("variables");                
        assertEquals(testBindingBoxInstance1.getString("id"), findVariable(variables, "ANY_BINDING").getString("value"));
        assertEquals(MessageFormat.format("SLAVE_HOST_NAME: {0}", variableResolver.resolve("${SLAVE_HOST_NAME}")),
                findVariable(variables, "VAR_INSIDE").getString("value"));
        //assertNull(findVariable(variables, "HTTP"));
        assertNull(findVariable(variables, "VAR_WHOLE"));
        
        // check test-nested-box instance
        assertTrue(MessageFormat.format("Instance {0} is not on-line", Client.getPageUrl(cloud.getEndpointUrl(), testNestedBoxInstance)),
                Client.ON_OPERATIONS.contains(testNestedBoxInstance.getString("operation")) && 
                        !Client.InstanceState.UNAVAILABLE.equals(testNestedBoxInstance.getString("state")));
        variables = testNestedBoxInstance.getJSONArray("variables");
        assertEquals(testBindingBoxInstance2.getString("id"), findVariable(variables, "ANY_BINDING", "nested").getString("value"));
        assertEquals(testBindingBoxInstance3.getString("id"), findVariable(variables, "REQUIRED_BINDING").getString("value"));
        assertEquals(testTag, findVariable(variables, "VAR_INSIDE").getString("value"));
        assertEquals(testTag, findVariable(variables, "VAR_WHOLE").getString("value"));
        assertEquals(testTag, findVariable(variables, "VAR_INSIDE", "nested").getString("value"));
        assertNull(findVariable(variables, "HTTP", "nested"));
        
        runJob("cleanup", "CleanupJob.xml", testParameters);        
    }    
    
    private FreeStyleBuild runJob(String name, String configXml, Map<String, String> textParameters) throws Exception {
        String projectXml = IOUtils.toString((InputStream) getClass().getResource(configXml).getContent());
        FreeStyleProject project = (FreeStyleProject) jenkins.createProjectFromXML(name, new ByteArrayInputStream(projectXml.getBytes()));
        List<ParameterValue> parameters = new ArrayList<ParameterValue>();
        for (Map.Entry<String, String> entry : textParameters.entrySet()) {
            parameters.add(new TextParameterValue(entry.getKey(), entry.getValue()));
        }
        QueueTaskFuture future = project.scheduleBuild2(0, new Cause.LegacyCodeCause(), new ParametersAction(parameters));
        Future startCondition = future.getStartCondition();
        startCondition.get(60, TimeUnit.MINUTES);
        FreeStyleBuild result = (FreeStyleBuild) future.get(60, TimeUnit.MINUTES);
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        result.getLogText().writeLogTo(0, log);
        assertEquals(log.toString(), Result.SUCCESS, result.getResult());     
        return result;
    }
    
    private static JSONObject findVariable(JSONArray variables, String name) {
        return findVariable(variables, name, null);
    }
    
    private static JSONObject findVariable(JSONArray variables, String name, String scope) {
        for (Object variable : variables) {
            JSONObject variableJson = (JSONObject) variable;
            if (variableJson.getString("name").equals(name)) {
                if (scope == null) {
                    scope = StringUtils.EMPTY;
                }
                String variableScope = variableJson.containsKey("scope") ? variableJson.getString("scope") : StringUtils.EMPTY;
                if (scope.equals(variableScope)) {
                    return variableJson;
                }
            }
        }
        return null;
    }
    
    public void testBuildWithLinuxSlave() throws Exception {
        if (System.getProperty(OPS_USER_NAME_PROPERTY) != null) {
            testBuildWithSlave(JENKINS_SLAVE_BOX_NAME);
        }
    }
    
    public void testBuildWithWindowsSlave() throws Exception {
        if (System.getProperty(OPS_PASSWORD_PROPERTY) != null) {
            testBuildWithSlave("Windows Jenkins Slave");        
        }
    }
        
    private void testBuildWithProjectSpecificSlave(ElasticBoxCloud cloud) throws Exception {
        Client client = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        JSONArray profiles = (JSONArray) client.doGet(MessageFormat.format("/services/profiles?box_name={0}", JENKINS_SLAVE_BOX_NAME), true);
        assertTrue(MessageFormat.format("No profile is found for box {0} of ElasticBox cloud {1}", JENKINS_SLAVE_BOX_NAME, cloud.getDisplayName()), profiles.size() > 0);
        JSONObject profile = profiles.getJSONObject(0);        
        String projectXml = IOUtils.toString((InputStream) getClass().getResource("TestProjectWithSlave.xml").getContent());
        projectXml = projectXml.replace("{workspaceId}", profile.getString("owner")).
                replace("{InstanceCreator.boxId}", profile.getJSONObject("box").getString("version")).
                replace("{InstanceCreator.profileId}", profile.getString("id")).
                replace("{version}", "0.7.5-SNAPSHOT");   
        FreeStyleProject project = (FreeStyleProject) jenkins.createProjectFromXML("test", new ByteArrayInputStream(projectXml.getBytes()));
        QueueTaskFuture future = project.scheduleBuild2(0);
        Object scheduleResult = getResult(future.getStartCondition(), 30);
        assertNotNull("30 minutes after job scheduling but no result returned", scheduleResult);
        FreeStyleBuild result = (FreeStyleBuild) getResult(future, 60);      
        assertNotNull("60 minutes after job start but no result returned", result);
        assertEquals(Result.SUCCESS, result.getResult());
    }
    
    private void testConfigRoundtrip(ElasticBoxCloud cloud) throws Exception {
        WebClient webClient = createWebClient();
        HtmlForm configForm = webClient.goTo("configure").getFormByName("config");
        submit(webClient.goTo("configure").getFormByName("config"));        
        assertEqualBeans(cloud, jenkins.clouds.iterator().next(), "endpointUrl,maxInstances,retentionTime,username,password");
        
        configForm.submit(configForm.getButtonByCaption("Test Connection"));
        
        // test connection
        PostMethod post = new PostMethod(MessageFormat.format("{0}descriptorByName/{1}/testConnection?.crumb=test", jenkins.getRootUrl(), ElasticBoxCloud.class.getName()));        
        post.setRequestBody(Arrays.asList(new NameValuePair("endpointUrl", cloud.getEndpointUrl()),
                new NameValuePair("username", cloud.getUsername()),
                new NameValuePair("password", cloud.getPassword())).toArray(new NameValuePair[0]));
        HttpClient httpClient = new HttpClient();
        int status = httpClient.executeMethod(post);
        String content = post.getResponseBodyAsString();
        assertEquals(HttpStatus.SC_OK, status);
        assertStringContains(content, content, MessageFormat.format("Connection to {0} was successful.", cloud.getEndpointUrl()));
    }
    
    private void testBuildWithSlave(String slaveBoxName) throws Exception {  
        LOGGER.info(MessageFormat.format("Testing build with slave {0}", slaveBoxName));
        
        String username = System.getProperty(OPS_USER_NAME_PROPERTY);
        String password = System.getProperty(OPS_PASSWORD_PROPERTY);
        assertNotNull(MessageFormat.format("System property {0} must be specified to run this test", OPS_USER_NAME_PROPERTY), username);
        assertNotNull(MessageFormat.format("System property {0} must be specified to run this test", OPS_PASSWORD_PROPERTY), password);
        ElasticBoxCloud cloud = createCloud(ELASTICBOX_URL, username, password);        
        Client client = new Client(cloud.getEndpointUrl(), cloud.getUsername(), cloud.getPassword());
        JSONArray profiles = (JSONArray) client.doGet(MessageFormat.format("/services/profiles?box_name={0}", 
                URLEncoder.encode(slaveBoxName, "UTF-8")), true);
        assertTrue(MessageFormat.format("No profile is found for box {0} of ElasticBox cloud {1}", slaveBoxName, 
                cloud.getDisplayName()), profiles.size() > 0);
        JSONObject profile = profiles.getJSONObject(0);        
        String workspace = profile.getString("owner");
        String box = profile.getJSONObject("box").getString("version");
        String label = UUID.randomUUID().toString();
        SlaveConfiguration slaveConfig = new SlaveConfiguration(UUID.randomUUID().toString(), workspace, box, box, 
                profile.getString("id"), 0, 1, slaveBoxName, "[]", label, "", null, Node.Mode.NORMAL, 0, 1, 60);
        ElasticBoxCloud newCloud = new ElasticBoxCloud("elasticbox", cloud.getEndpointUrl(), cloud.getMaxInstances(), 
                cloud.getRetentionTime(), cloud.getUsername(), cloud.getPassword(), Collections.singletonList(slaveConfig));
        jenkins.clouds.remove(cloud);
        jenkins.clouds.add(newCloud);
        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class, 
                MessageFormat.format("Build with {0}", slaveBoxName));
        project.setAssignedLabel(jenkins.getLabel(label));
        QueueTaskFuture future = project.scheduleBuild2(0);
        Object scheduleResult = getResult(future.getStartCondition(), 60);
        assertNotNull("60 minutes after job scheduling but no result returned", scheduleResult);
        FreeStyleBuild result = (FreeStyleBuild) getResult(future, 30);      
        assertNotNull("30 minutes after job start but no result returned", result);
        assertEquals(Result.SUCCESS, result.getResult());
    }
    
    private Object getResult(Future<?> future, int waitMinutes) throws ExecutionException {
        Object result = null;
        long maxWaitTime = waitMinutes * 60000;
        long waitTime = 0;
        do {
            long waitStart = System.currentTimeMillis();
            try {
                result = future.get(maxWaitTime - waitTime, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
            } catch (TimeoutException ex) {
                break;
            }
            waitTime += (System.currentTimeMillis() - waitStart);
        } while (result == null && waitTime < maxWaitTime);
        
        return result;
    }
    
}
