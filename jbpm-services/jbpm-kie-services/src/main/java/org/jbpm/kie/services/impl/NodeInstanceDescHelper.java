/*
 * Copyright 2015 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.kie.services.impl;

import java.util.ArrayList;
import java.util.List;
import org.jbpm.process.audit.NodeInstanceLog;
import org.jbpm.services.api.model.NodeInstanceDesc;

/**
 *
 * @author salaboy
 */
public class NodeInstanceDescHelper {
    public static NodeInstanceDesc adapt(NodeInstanceLog nil){
        return new org.jbpm.kie.services.impl.model.NodeInstanceDesc(nil.getNodeInstanceId(), nil.getNodeId(), 
                nil.getNodeName(), nil.getNodeType(), nil.getExternalId(), nil.getProcessInstanceId(), 
                nil.getDate(), nil.getConnection(), nil.getType(), nil.getWorkItemId());
    }
    
    public static List<NodeInstanceDesc> adaptCollection(List<NodeInstanceLog> nils){
        List<NodeInstanceDesc> nids = new ArrayList<NodeInstanceDesc>(nils.size());
        for(NodeInstanceLog nil : nils){
            nids.add(adapt(nil));
        }
        return nids;
    }
}
