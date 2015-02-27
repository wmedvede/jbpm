/*
 * Copyright 2014 JBoss by Red Hat.
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
package org.jbpm.services.task.audit.impl.model;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;
import javax.persistence.ElementCollection;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Temporal;
import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Boost;
import org.hibernate.search.annotations.DateBridge;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.FullTextFilterDef;
import org.hibernate.search.annotations.FullTextFilterDefs;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.search.annotations.Store;
import org.jbpm.services.task.audit.impl.filters.BusinessAdministratorFilterFactory;
import org.jbpm.services.task.audit.impl.filters.DeploymentFilterFactory;
import org.jbpm.services.task.audit.impl.filters.ProcessDefinitionIdFilterFactory;
import org.jbpm.services.task.audit.impl.filters.OwnerFilterFactory;
import org.jbpm.services.task.audit.impl.filters.PotentialOwnerFilterFactory;
import org.jbpm.services.task.audit.impl.filters.ProcessDefinitionNameFilterFactory;
import org.jbpm.services.task.audit.impl.filters.ProcessInstanceIdFilterFactory;
import org.jbpm.services.task.audit.impl.filters.ProcessInstanceInitiatorFilterFactory;
import org.jbpm.services.task.audit.impl.filters.TaskStateFilterFactory;
import org.jbpm.services.task.audit.impl.filters.ProcessStatusFilterFactory;

import org.kie.internal.task.api.AuditTask;

/**
 *
 * @author salaboy
 */
@Entity
@Indexed
@SequenceGenerator(name = "auditIdSeq", sequenceName = "AUDIT_ID_SEQ", allocationSize = 1)
@FullTextFilterDefs({
    @FullTextFilterDef(name = "processId", impl = ProcessDefinitionIdFilterFactory.class),
    @FullTextFilterDef(name = "processName", impl = ProcessDefinitionNameFilterFactory.class),
    @FullTextFilterDef(name = "initiator", impl = ProcessInstanceInitiatorFilterFactory.class),
    @FullTextFilterDef(name = "processInstanceId", impl = ProcessInstanceIdFilterFactory.class),
    @FullTextFilterDef(name = "owner", impl = OwnerFilterFactory.class),
    @FullTextFilterDef(name = "potentialOwner", impl = PotentialOwnerFilterFactory.class),
    @FullTextFilterDef(name = "businessAdministrator", impl = BusinessAdministratorFilterFactory.class),
    @FullTextFilterDef(name = "states", impl = TaskStateFilterFactory.class),
    @FullTextFilterDef(name = "status", impl = ProcessStatusFilterFactory.class),
    @FullTextFilterDef(name = "deployment", impl = DeploymentFilterFactory.class)

})
public class AuditTaskImpl implements Serializable, AuditTask {

    private static final long serialVersionUID = 5388016330549830043L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "auditIdSeq")
    private Long id;

    @Field(analyze = Analyze.NO, store = Store.YES)
    private Long taskId;
    @Field(analyze = Analyze.NO, store = Store.YES)
    private String status;

    @Field(analyze = Analyze.NO, store = Store.YES)
    @Temporal(javax.persistence.TemporalType.DATE)
    private Date activationTime;
    @Field(store = Store.YES)
    private String name = "";

    @Field(store = Store.YES)
    @Boost(0.8f)
    private String description = "";

    @Field(store = Store.YES)
    @Boost(0.5f)
    private int priority;

    @Field(name = "createdBy", store = Store.YES)
    private String createdBy;

    @Field(name = "actualOwner", store = Store.YES)
    private String actualOwner = "";

    @Field(store = org.hibernate.search.annotations.Store.YES)
    @Temporal(javax.persistence.TemporalType.DATE)
    @DateBridge(resolution = org.hibernate.search.annotations.Resolution.SECOND)
    private Date createdOn;
    @Field(store = org.hibernate.search.annotations.Store.YES)
    @Temporal(javax.persistence.TemporalType.DATE)

    private Date dueDate;

    @Field(analyze = Analyze.NO, store = Store.YES)
    private long processInstanceId;

    @Field(analyze = Analyze.NO, name = "process", store = Store.YES)
    private String processId = "";

    private long processSessionId;

    @Field(analyze = Analyze.NO, store = Store.YES)
    private long parentId;

    @Field(analyze = Analyze.NO, name = "deployment", store = Store.YES)
    private String deploymentId = "";

    @Field(analyze = Analyze.NO, store = Store.YES)
    private Long workItemId;

    @Field(analyze = Analyze.NO)
    @ElementCollection
    @IndexedEmbedded()
    private Set<String> potentialOwners;

    @Field(analyze = Analyze.NO)
    @ElementCollection
    @IndexedEmbedded()
    private Set<String> businessAdministrators;

    public AuditTaskImpl() {
    }

    public AuditTaskImpl(long taskId, String name, String status, Date activationTime,
            String actualOwner, String description, int priority, String createdBy,
            Date createdOn, Date dueDate, long processInstanceId, String processId,
            long processSessionId, String deploymentId, long parentId, long workItemId, Set<String> potentialOwners, Set<String> businessAdministrators) {
        this.taskId = taskId;
        this.status = status;
        this.activationTime = activationTime;
        this.name = name;
        this.description = description;
        this.priority = priority;
        this.createdBy = createdBy;
        this.createdOn = createdOn;
        this.actualOwner = actualOwner;
        this.dueDate = dueDate;
        this.processInstanceId = processInstanceId;
        this.processId = processId;
        this.processSessionId = processSessionId;
        this.deploymentId = deploymentId;
        this.parentId = parentId;
        this.workItemId = workItemId;
        this.potentialOwners = potentialOwners;
        this.businessAdministrators = businessAdministrators;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public long getTaskId() {
        return taskId;
    }

    @Override
    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public Date getActivationTime() {
        return activationTime;
    }

    @Override
    public void setActivationTime(Date activationTime) {
        this.activationTime = activationTime;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public Date getCreatedOn() {
        return createdOn;
    }

    @Override
    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    @Override
    public Date getDueDate() {
        return dueDate;
    }

    @Override
    public void setDueDate(Date dueDate) {
        this.dueDate = dueDate;
    }

    @Override
    public long getProcessInstanceId() {
        return processInstanceId;
    }

    @Override
    public void setProcessInstanceId(long processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    @Override
    public String getProcessId() {
        return processId;
    }

    @Override
    public void setProcessId(String processId) {
        this.processId = processId;
    }

    @Override
    public long getProcessSessionId() {
        return processSessionId;
    }

    @Override
    public void setProcessSessionId(long processSessionId) {
        this.processSessionId = processSessionId;
    }

    @Override
    public long getParentId() {
        return parentId;
    }

    @Override
    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    @Override
    public String getActualOwner() {
        return actualOwner;
    }

    @Override
    public void setActualOwner(String actualOwner) {
        this.actualOwner = actualOwner;
    }

    @Override
    public String getDeploymentId() {
        return deploymentId;
    }

    @Override
    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    @Override
    public long getWorkItemId() {
        return workItemId;
    }

    @Override
    public void setWorkItemId(long workItemId) {
        this.workItemId = workItemId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public void setWorkItemId(Long workItemId) {
        this.workItemId = workItemId;
    }

    public Set<String> getPotentialOwners() {
        return potentialOwners;
    }

    public void setPotentialOwners(Set<String> potentialOwners) {
        this.potentialOwners = potentialOwners;
    }

    public Set<String> getBusinessAdministrators() {
        return businessAdministrators;
    }

    public void setBusinessAdministrators(Set<String> businessAdministrators) {
        this.businessAdministrators = businessAdministrators;
    }

}
