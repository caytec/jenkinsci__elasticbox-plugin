/*
 *
 *  ElasticBox Confidential
 *  Copyright (c) 2016 All Right Reserved, ElasticBox Inc.
 *
 *  NOTICE:  All information contained herein is, and remains the property
 *  of ElasticBox. The intellectual and technical concepts contained herein are
 *  proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 *  and are protected by trade secret or copyright law. Dissemination of this
 *  information or reproduction of this material is strictly forbidden unless prior
 *  written permission is obtained from ElasticBox.
 *
 */

package com.elasticbox.jenkins.model.repository.api.criteria.instance;

import com.elasticbox.BoxStack;
import com.elasticbox.Client;
import com.elasticbox.Constants;
import com.elasticbox.jenkins.model.instance.Instance;
import com.elasticbox.jenkins.model.repository.api.criteria.AbstractJSONCriteria;
import com.elasticbox.jenkins.model.repository.api.factory.ModelFactory;
import com.elasticbox.jenkins.model.repository.api.factory.instance.InstanceFactoryImpl;
import net.sf.json.JSONObject;

/**
 * Created by serna on 1/8/16.
 */
public class ReconfigurableInstancesCriteria extends AbstractJSONCriteria<Instance> {

    public ReconfigurableInstancesCriteria() {
        this(new InstanceFactoryImpl());
    }

    public ReconfigurableInstancesCriteria(ModelFactory<Instance> factory) {
        super(factory);
    }


    @Override
    public boolean fits(JSONObject instance) {
        // reject inaccessible instances that cannot be reconfigured
        String operation = instance.getJSONObject("operation").getString("event");
        if (Client.InstanceState.UNAVAILABLE.equals(instance.getString("state")) &&
                Client.InstanceOperation.RECONFIGURE.equals(operation)) {
            return true;
        }
        return !Client.TERMINATE_OPERATIONS.contains(operation);
    }

}