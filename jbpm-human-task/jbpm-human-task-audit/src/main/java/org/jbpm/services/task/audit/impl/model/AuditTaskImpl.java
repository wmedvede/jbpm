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
import org.hibernate.search.annotations.FieldBridge;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Resolution;
import org.hibernate.search.bridge.builtin.IntegerBridge;

import org.kie.internal.task.api.AuditTask;

/**
 *
 * @author salaboy
 */
@Entity
@Indexed
@SequenceGenerator(name="auditIdSeq", sequenceName="AUDIT_ID_SEQ", allocationSize=1)
public class AuditTaskImpl implements Serializable, AuditTask {
    
	private static final long serialVersionUID = 5388016330549830043L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator="auditIdSeq")
    private Long id;
    
    @Field(analyze = Analyze.NO)
    private Long taskId;
    @Field(analyze = Analyze.NO)
    private String status;

    @Field
    @DateBridge(resolution = Resolution.HOUR)
    @Temporal(javax.persistence.TemporalType.DATE)

    private Date activationTime;
    @Field()
    private String name = "";

    @Field()
    @Boost(0.8f)
    private String description = "";
    
    @Field()
    @FieldBridge(impl = IntegerBridge.class)
    @Boost(0.5f)
    private int priority;
    
    @Field( name = "user")
    private String createdBy ;
    
    @Field( name = "actualOwner")

    private String actualOwner = "";
    
    @Field
    @DateBridge(resolution = Resolution.HOUR)
    @Temporal(javax.persistence.TemporalType.DATE)
    private Date createdOn;
    @Field
    @DateBridge(resolution = Resolution.HOUR)
    @Temporal(javax.persistence.TemporalType.DATE)

    private Date dueDate;
    
    @Field(analyze = Analyze.NO, name = "processInstance")
    @FieldBridge(impl = IntegerBridge.class)
    private long processInstanceId;
    
    @Field(analyze = Analyze.NO, name = "process")
    private String processId = "";
    
    
    private long processSessionId;
    private long parentId;
            

    @Field(analyze = Analyze.NO, name = "deployment")
    private String deploymentId = "";
    
    @Field(analyze = Analyze.NO)
    private Long workItemId;


    public AuditTaskImpl() {
    }
    
    public AuditTaskImpl(long taskId, String name, String status, Date activationTime, 
            String actualOwner , String description, int priority, String createdBy, 
            Date createdOn, Date dueDate, long processInstanceId, String processId, 
            long processSessionId, String deploymentId, long parentId, long workItemId) {
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

}
