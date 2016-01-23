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

package com.elasticbox.jenkins;

import com.elasticbox.Client;
import com.elasticbox.jenkins.model.services.deployment.execution.order.DeployBoxOrderResult;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.repository.BoxRepository;
import com.elasticbox.jenkins.model.repository.api.BoxRepositoryAPIImpl;
import com.elasticbox.jenkins.model.services.deployment.DeployBoxOrderServiceImpl;
import com.elasticbox.jenkins.model.services.error.ServiceException;
import com.elasticbox.jenkins.util.ClientCache;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Node;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 *
 * @author Phong Nguyen Le
 */
public class SlaveConfiguration extends AbstractSlaveConfiguration {
    public static final String SLAVE_CONFIGURATIONS = "slaveConfigurations";

    private static final Logger LOGGER = Logger.getLogger(SlaveConfiguration.class.getName());

    @DataBoundConstructor
    public SlaveConfiguration(String id, String workspace, String box, String boxVersion, String profile,
            String claims, String provider, String location, int minInstances, int maxInstances, String tags,
            String variables, String labels,String description, String remoteFS, Node.Mode mode, int retentionTime,
            String maxBuildsText, int executors, int launchTimeout) {
        super(id, workspace, box, boxVersion, profile, claims, provider, location, minInstances, maxInstances,
                tags, variables, labels, description, remoteFS, mode, retentionTime,
                StringUtils.isBlank(maxBuildsText) ? 0 : Integer.parseInt(maxBuildsText), executors, launchTimeout);
    }

    @Extension
    public static final class DescriptorImpl extends AbstractSlaveConfigurationDescriptor {

        @Override
        public String getDisplayName() {
            return "Slave Configuration";
        }

        public void validateSlaveConfiguration(SlaveConfiguration slaveConfig, ElasticBoxCloud newCloud) throws FormException {
            String slaveConfigText = slaveConfig.getDescription() != null ? MessageFormat.format("slave configuration ''{0}''", slaveConfig.getDescription()) : "a slave configuration";
            if (StringUtils.isBlank(slaveConfig.getWorkspace())) {
                throw new FormException(MessageFormat.format("No Workspace is selected for {0} of ElasticBox cloud ''{1}''.", slaveConfigText, newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            if (StringUtils.isBlank(slaveConfig.getBox())) {
                throw new FormException(MessageFormat.format("No Box is selected for {0} of ElasticBox cloud ''{1}''.", slaveConfigText, newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            if (StringUtils.isBlank(slaveConfig.getBoxVersion())) {
                throw new FormException(MessageFormat.format("No Version is selected for the selected box in {0} of ElasticBox cloud ''{1}''.", slaveConfigText, newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            if (slaveConfig.getProfile() != null) {
                if (StringUtils.isBlank(slaveConfig.getProfile())) {
                    throw new FormException(MessageFormat.format("No Deployment Policy is selected for {0} of ElasticBox cloud ''{1}''.", slaveConfigText, newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
                }
            } else if (slaveConfig.getClaims() != null) {
                if (StringUtils.isBlank(slaveConfig.getClaims())) {
                    throw new FormException(MessageFormat.format("Claims must be specified to select a Deployment Policy for {0} of ElasticBox cloud ''{1}''.", slaveConfigText, newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
                }
            } else if (slaveConfig.getProvider() != null) {
                if (StringUtils.isBlank(slaveConfig.getProvider())) {
                    throw new FormException(MessageFormat.format("No Provider is selected for {0} of ElasticBox cloud ''{1}''.", slaveConfigText, newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
                }
                if (StringUtils.isBlank(slaveConfig.getLocation())) {
                    throw new FormException(MessageFormat.format("No Region is selected for {0} of ElasticBox cloud ''{1}''.", slaveConfigText, newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
                }
            } else {
                throw new FormException(MessageFormat.format("No deployment option is selected for {0} of ElasticBox cloud ''{1}''.", slaveConfigText, newCloud.getDisplayName()), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }

            if (slaveConfig.getExecutors() < 1) {
                slaveConfig.setExecutors(1);
            }

            if (StringUtils.isBlank(slaveConfig.getId())) {
                slaveConfig.setId(UUID.randomUUID().toString());
            }

            FormValidation result = ((SlaveConfiguration.DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(SlaveConfiguration.class)).doCheckBoxVersion(slaveConfig.getBoxVersion(),
                    newCloud.getEndpointUrl(), newCloud.getToken(),
                    slaveConfig.getWorkspace(), slaveConfig.getBox());
            if (result.kind == FormValidation.Kind.ERROR) {
                throw new FormException(result.getMessage(), SlaveConfiguration.SLAVE_CONFIGURATIONS);
            }
        }

        private Client createClient(String endpointUrl, String token) {
            if (StringUtils.isBlank(endpointUrl) || StringUtils.isBlank(token)) {
                return null;
            }

            Client client = ClientCache.getClient(endpointUrl, token);
            if (client == null) {
                if (StringUtils.isNotBlank(token)) {
                    client = new Client(endpointUrl, token);
                } else {
                    LOGGER.log(Level.SEVERE, "You need an ElasticBox token to be able to connect");
                }
                try {
                    client.connect();
                } catch (IOException ex) {
                    client = null;
                    LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }

            return client;
        }

        public ListBoxModel doFillWorkspaceItems(@RelativePath("..") @QueryParameter String endpointUrl,
                @RelativePath("..") @QueryParameter String token) {
            return DescriptorHelper.getWorkspaces(createClient(endpointUrl, token));
        }

        public ListBoxModel doFillBoxItems(@RelativePath("..") @QueryParameter String endpointUrl,
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String workspace) {
            return DescriptorHelper.getBoxes(createClient(endpointUrl, token), workspace);
        }

        public ListBoxModel doFillBoxVersionItems(@RelativePath("..") @QueryParameter String endpointUrl,
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String workspace, @QueryParameter String box) {
            return DescriptorHelper.getBoxVersions(createClient(endpointUrl, token), workspace, box);
        }

        public FormValidation doCheckBoxVersion(@QueryParameter String value,
                @RelativePath("..") @QueryParameter String endpointUrl,
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String workspace,
                @QueryParameter String box) {
            Client client = createClient(endpointUrl, token);
            return checkBoxVersion(value, box, workspace, client);
        }

        public ListBoxModel doFillProfileItems(
                @RelativePath("..") @QueryParameter String endpointUrl,
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String workspace, @QueryParameter String box) {

            LOGGER.log(Level.FINE, "doFill ProfileItems - cloud: "+endpointUrl+", workspace: "+workspace+", box: "+box);

            ListBoxModel profiles = new ListBoxModel();

                if (StringUtils.isEmpty(endpointUrl) || StringUtils.isEmpty(workspace) || StringUtils.isEmpty(box))
                    return profiles;

            try {
                final BoxRepository boxRepository = new BoxRepositoryAPIImpl(ClientCache.getClient(endpointUrl, token));

                final DeployBoxOrderResult<List<PolicyBox>> result = new DeployBoxOrderServiceImpl(boxRepository).deploymentPolicies(workspace, box);
                final List<PolicyBox> policyBoxList = result.getResult();
                for (PolicyBox policyBox : policyBoxList) {
                    profiles.add(policyBox.getName(), policyBox.getId());
                }

            } catch (ServiceException e) {
                LOGGER.log(Level.SEVERE, "ERROR doFillProfileItems - cloud: "+endpointUrl+", workspace: "+workspace+", box: "+box+" return an empty list", e);
            }

            return profiles;


        }

        public ListBoxModel doFillProviderItems(
                @RelativePath("..") @QueryParameter String endpointUrl,
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String workspace) {
            return DescriptorHelper.getCloudFormationProviders(createClient(endpointUrl, token), workspace);
        }

        public ListBoxModel doFillLocationItems(
                @RelativePath("..") @QueryParameter String endpointUrl,
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String provider) {
            return DescriptorHelper.getCloudFormationLocations(createClient(endpointUrl, token), provider);
        }

        public DescriptorHelper.JSONArrayResponse doGetBoxStack(
                @RelativePath("..") @QueryParameter String endpointUrl,
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String workspace,
                @QueryParameter String box,
                @QueryParameter String boxVersion) {
            return DescriptorHelper.getBoxStack(createClient(endpointUrl, token), workspace, box,
                    StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }

        public DescriptorHelper.JSONArrayResponse doGetInstances(
                @RelativePath("..") @QueryParameter String endpointUrl,
                @RelativePath("..") @QueryParameter String token,
                @QueryParameter String workspace,
                @QueryParameter String box,
                @QueryParameter String boxVersion) {
            return DescriptorHelper.getInstancesAsJSONArrayResponse(createClient(endpointUrl, token),
                    workspace, StringUtils.isBlank(boxVersion) ? box : boxVersion);
        }

    }

}
