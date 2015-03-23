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
import org.jbpm.services.api.model.UserTaskInstanceDesc;
import org.jbpm.services.task.audit.impl.model.AuditTaskImpl;
import org.jbpm.services.task.query.TaskSummaryImpl;

/**
 *
 * @author salaboy
 */
public class UserTaskInstanceDescHelper {
    public static UserTaskInstanceDesc adapt(AuditTaskImpl at){
        return new org.jbpm.kie.services.impl.model.UserTaskInstanceDesc(at.getId(), at.getStatus(), 
                at.getActivationTime(), at.getName(), at.getDescription(), at.getPriority(),
                at.getActualOwner(), at.getCreatedBy(), at.getDeploymentId(), 
                at.getProcessId(), at.getProcessInstanceId(), at.getCreatedOn(), at.getDueDate());
    }
    
    public static List<UserTaskInstanceDesc> adaptCollection(List<AuditTaskImpl> ats){
        List<UserTaskInstanceDesc> utids = new ArrayList<UserTaskInstanceDesc>(ats.size());
        for(AuditTaskImpl at : ats){
            utids.add(adapt(at));
        }
        return utids;
    }
    
    
     public static UserTaskInstanceDesc adaptTs(TaskSummaryImpl at){
        return new org.jbpm.kie.services.impl.model.UserTaskInstanceDesc(at.getId(), at.getStatusId(), 
                at.getActivationTime(), at.getName(), at.getDescription(), at.getPriority(),
                at.getActualOwner().getId(), at.getCreatedBy().getId(), at.getDeploymentId(), 
                at.getProcessId(), at.getProcessInstanceId(), at.getCreatedOn(), at.getExpirationTime());
    }
    
    public static List<UserTaskInstanceDesc> adaptCollectionTs(List<TaskSummaryImpl> ats){
        List<UserTaskInstanceDesc> utids = new ArrayList<UserTaskInstanceDesc>(ats.size());
        for(TaskSummaryImpl at : ats){
            utids.add(adaptTs(at));
        }
        return utids;
    }
}
