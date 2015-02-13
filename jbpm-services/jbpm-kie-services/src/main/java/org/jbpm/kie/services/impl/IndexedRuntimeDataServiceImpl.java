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
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.persistence.EntityManagerFactory;
import org.apache.commons.collections.CollectionUtils;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.jpa.Search;
import org.hibernate.search.query.dsl.BooleanJunction;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.jbpm.process.audit.NodeInstanceLog;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.process.audit.VariableInstanceLog;
import org.jbpm.services.api.DeploymentEvent;
import org.jbpm.services.api.DeploymentEventListener;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.model.NodeInstanceDesc;
import org.jbpm.services.api.model.ProcessDefinition;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import org.jbpm.services.api.model.UserTaskInstanceDesc;
import org.jbpm.services.api.model.VariableDesc;
import org.jbpm.services.task.audit.impl.model.AuditTaskImpl;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.identity.IdentityProvider;
import org.kie.internal.query.QueryContext;
import org.kie.internal.query.QueryFilter;
import org.kie.internal.task.api.AuditTask;
import org.kie.internal.task.api.model.TaskEvent;

/**
 *
 * @author salaboy
 */
public class IndexedRuntimeDataServiceImpl implements RuntimeDataService, DeploymentEventListener  {
    
    	private static final int MAX_CACHE_ENTRIES = Integer.parseInt(System.getProperty("org.jbpm.service.cache.size", "100"));
	
    protected Set<ProcessDefinition> availableProcesses = new HashSet<ProcessDefinition>();
    protected Map<String, List<String>> deploymentsRoles = new HashMap<String, List<String>>();
    
	protected Map<String, List<String>> userDeploymentIdsCache = new LinkedHashMap<String, List<String>>() {
		private static final long serialVersionUID = -2324394641773215253L;
		
		protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
			return size() > MAX_CACHE_ENTRIES;
		}
	};
    
        
    private IdentityProvider identityProvider;
    
    private EntityManagerFactory emf;
    protected FullTextEntityManager fullTextEntityManager;

    public IndexedRuntimeDataServiceImpl(EntityManagerFactory emf) {
        this.emf = emf;
        fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        
    }

    
    
    public EntityManagerFactory getEmf() {
        return emf;
    }

    public void setEmf(EntityManagerFactory emf) {
        this.emf = emf;
    }
    
    
    

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstances(QueryContext queryContext) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
        
        
        if(filters != null){
            bool.must(filters);
        }
        Query query = bool.must(qb.all().createQuery()).createQuery();
        
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
        //Apply Pagination & Sort Here
        applyQueryContext(fullTextQuery, queryContext);
        

        List<ProcessInstanceLog> processInstanceLogs =  fullTextQuery.getResultList();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
        
    }
    
   

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstances(List<Integer> states, String initiator, QueryContext queryContext) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        Query query = bool.must(qb.keyword().onField("initiator").matching(initiator).createQuery())
                .must(qb.keyword().onField("status").matching(states).createQuery()).createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
        //Apply Pagination & Sort Here
        applyQueryContext(fullTextQuery, queryContext);
        

        List<ProcessInstanceLog> processInstanceLogs =  fullTextQuery.getResultList();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessId(List<Integer> states, String processId, String initiator, QueryContext queryContext) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        Query query = bool.must(qb.keyword().onField("initiator").matching(initiator).createQuery())
                .must(qb.keyword().onField("status").matching(states).createQuery())
                .must(qb.keyword().onField("processId").matching(processId).createQuery())
                .createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
        //Apply Pagination & Sort Here
        applyQueryContext(fullTextQuery, queryContext);
        

        List<ProcessInstanceLog> processInstanceLogs =  fullTextQuery.getResultList();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessName(List<Integer> states, String processName, String initiator, QueryContext queryContext) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        Query query = bool.must(qb.keyword().onField("initiator").matching(initiator).createQuery())
                .must(qb.keyword().onField("status").matching(states).createQuery())
                .must(qb.keyword().onField("processName").matching(processName).createQuery())
                .createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
        //Apply Pagination & Sort Here
        applyQueryContext(fullTextQuery, queryContext);
        

        List<ProcessInstanceLog> processInstanceLogs =  fullTextQuery.getResultList();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByDeploymentId(String deploymentId, List<Integer> states, QueryContext queryContext) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        Query query = bool.must(qb.keyword().onField("externalId").matching(deploymentId).createQuery())
                .must(qb.keyword().onField("status").matching(states).createQuery())
                .createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
        //Apply Pagination & Sort Here
        applyQueryContext(fullTextQuery, queryContext);
        

        List<ProcessInstanceLog> processInstanceLogs =  fullTextQuery.getResultList();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
    }

    @Override
    public ProcessInstanceDesc getProcessInstanceById(long processInstanceId) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        Query query = bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery())
               
                .createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
    
        ProcessInstanceLog processInstanceLog =  (ProcessInstanceLog)fullTextQuery.getSingleResult();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
        ProcessInstanceDesc processInstanceDesc = ProcessInstanceDescHelper.adapt(processInstanceLog);
        
        if(processInstanceLog != null){
            List<String> statuses = new ArrayList<String>();
        	statuses.add(Status.Ready.name());
        	statuses.add(Status.Reserved.name());
        	statuses.add(Status.InProgress.name());
            QueryBuilder qbTasks = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();
            Query queryTasks = qbTasks.bool().must(qb.keyword().onField("processInstanceId").matching(processInstanceDesc.getId()).createQuery())
                    .must(qb.keyword().onField("status").matching(statuses).createQuery())
               
                .createQuery();
            FullTextQuery fullTextQueryTasks = fullTextEntityManager.createFullTextQuery(queryTasks, AuditTaskImpl.class);
            List<AuditTaskImpl> auditTaskImpls = fullTextQueryTasks.getResultList();
            ((org.jbpm.kie.services.impl.model.ProcessInstanceDesc)processInstanceDesc).setActiveTasks(UserTaskInstanceDescHelper.adaptCollection(auditTaskImpls));
        }
   
        return processInstanceDesc;
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessDefinition(String processDefId, QueryContext queryContext) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        Query query = bool.must(qb.keyword().onField("processId").matching(processDefId).createQuery())
                .createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
        //Apply Pagination & Sort Here
        applyQueryContext(fullTextQuery, queryContext);
        

        List<ProcessInstanceLog> processInstanceLogs =  fullTextQuery.getResultList();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessDefinition(String processDefId, List<Integer> states, QueryContext queryContext) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        Query query = bool.must(qb.keyword().onField("processId").matching(processDefId).createQuery())
                .must(qb.keyword().onField("status").matching(states).createQuery())
                .createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
        //Apply Pagination & Sort Here
        applyQueryContext(fullTextQuery, queryContext);
        

        List<ProcessInstanceLog> processInstanceLogs =  fullTextQuery.getResultList();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
    }

    @Override
    public NodeInstanceDesc getNodeInstanceForWorkItem(Long workItemId) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(NodeInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        Query query = bool.must(qb.keyword().onField("workItemId").matching(workItemId).createQuery())
                
                .createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);


        NodeInstanceLog nodeInstanceLog =  (NodeInstanceLog)fullTextQuery.getSingleResult();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return NodeInstanceDescHelper.adapt(nodeInstanceLog);
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceHistoryActive(long processInstanceId, QueryContext queryContext) {
        return getProcessInstanceHistory(processInstanceId, false, queryContext);
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceHistoryCompleted(long processInstanceId, QueryContext queryContext) {
        return getProcessInstanceHistory(processInstanceId, true, queryContext);
    }
    
    protected Collection<NodeInstanceDesc> getProcessInstanceHistory(long processInstanceId, boolean completed, QueryContext queryContext){
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(NodeInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        
        bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery());
        if(completed){
            bool.must(qb.keyword().onField("type").matching(1).createQuery()); 
        }else{
            bool.must(qb.keyword().onField("type").matching(0).createQuery()) ;
        }
               
        Query query = bool.createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
        //Apply Pagination & Sort Here
        applyQueryContext(fullTextQuery, queryContext);

        List<NodeInstanceLog> nodeInstanceLogs =  fullTextQuery.getResultList();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return NodeInstanceDescHelper.adaptCollection(nodeInstanceLogs);
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceFullHistory(long processInstanceId, QueryContext queryContext) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(NodeInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        
        bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery());
       
        Query query = bool.createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
        //Apply Pagination & Sort Here
        applyQueryContext(fullTextQuery, queryContext);

        List<NodeInstanceLog> nodeInstanceLogs =  fullTextQuery.getResultList();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return NodeInstanceDescHelper.adaptCollection(nodeInstanceLogs);
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceFullHistoryByType(long processInstanceId, EntryType type, QueryContext queryContext) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(NodeInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        
        bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery());
        bool.must(qb.keyword().onField("type").matching(type).createQuery());
        Query query = bool.createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
        //Apply Pagination & Sort Here
        applyQueryContext(fullTextQuery, queryContext);

        List<NodeInstanceLog> nodeInstanceLogs =  fullTextQuery.getResultList();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return NodeInstanceDescHelper.adaptCollection(nodeInstanceLogs);
    }

    @Override
    public Collection<VariableDesc> getVariablesCurrentState(long processInstanceId) {
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(VariableInstanceLog.class).get();
        
        // Apply query filters 
        Query filters = applyDeploymentFilter(qb);

        BooleanJunction<BooleanJunction> bool = qb.bool();
   
        if(filters != null){
            bool.must(filters);
        }
        
        bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery());
        Query query = bool.createQuery();
                
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
        

        List<VariableInstanceLog> variableLogs =  fullTextQuery.getResultList();
        // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
   
        return VariableDescHelper.adaptCollection(variableLogs);
    }

    @Override
    public Collection<VariableDesc> getVariableHistory(long processInstanceId, String variableId, QueryContext queryContext) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Collection<ProcessDefinition> getProcessesByDeploymentId(String deploymentId, QueryContext queryContext) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Collection<ProcessDefinition> getProcessesByFilter(String filter, QueryContext queryContext) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Collection<ProcessDefinition> getProcesses(QueryContext queryContext) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Collection<String> getProcessIds(String deploymentId, QueryContext queryContext) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ProcessDefinition getProcessById(String processId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ProcessDefinition getProcessesByDeploymentIdProcessId(String deploymentId, String processId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public UserTaskInstanceDesc getTaskByWorkItemId(Long workItemId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public UserTaskInstanceDesc getTaskById(Long taskId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsBusinessAdministrator(String userId, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsBusinessAdministratorByStatus(String userId, List<Status> statuses, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, List<String> groupIds, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwnerByStatus(String userId, List<Status> status, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, List<String> groupIds, List<Status> status, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwnerByExpirationDateOptional(String userId, List<Status> status, Date from, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksOwnedByExpirationDateOptional(String userId, List<Status> strStatuses, Date from, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksOwned(String userId, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksOwnedByStatus(String userId, List<Status> status, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Long> getTasksByProcessInstanceId(Long processInstanceId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksByStatusByProcessInstanceId(Long processInstanceId, List<Status> status, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<AuditTask> getAllAuditTask(String userId, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskEvent> getTaskEvents(long taskId, QueryFilter filter) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onDeploy(DeploymentEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onUnDeploy(DeploymentEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onActivate(DeploymentEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void onDeactivate(DeploymentEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
     protected Query applyDeploymentFilter(QueryBuilder qb) {
    	List<String> deploymentIdForUser = getDeploymentsForUser();
    	
    	if (deploymentIdForUser != null && !deploymentIdForUser.isEmpty()) {
                return qb.keyword().onField("externalId").matching(deploymentIdForUser).createQuery();
//    		params.put(FILTER, " log.externalId in (:deployments) ");
//    		params.put("deployments", deploymentIdForUser);
        }
        return null;
    }
    
    protected List<String> getDeploymentsForUser() {
    	String identityName = null;
    	List<String> roles = null;
    	try {
	    	identityName = identityProvider.getName();
	    	roles = identityProvider.getRoles();
    	} catch (Exception e) {
    		// in case there is no way to collect either name of roles of the requesting used return empty list
    		return new ArrayList<String>();
    	}
    	List<String> usersDeploymentIds = userDeploymentIdsCache.get(identityName);
    	if (usersDeploymentIds != null) {
    		return usersDeploymentIds;
    	}
    	
    	usersDeploymentIds = new ArrayList<String>();
    	userDeploymentIdsCache.put(identityName, usersDeploymentIds);
    	boolean isSecured = false;
    	for (Map.Entry<String, List<String>> entry : deploymentsRoles.entrySet()){
    		if (entry.getValue().isEmpty() || CollectionUtils.containsAny(roles, entry.getValue())) {
    			usersDeploymentIds.add(entry.getKey());
    		}
    		if (entry.getValue() != null && !entry.getValue().isEmpty()) {
    			isSecured = true;
    		}
    	}
    	
    	if (isSecured && usersDeploymentIds.isEmpty()) {
    		usersDeploymentIds.add("deployments-are-secured");
    	}
    	
    	return usersDeploymentIds;
    }
     
    protected void applyQueryContext(FullTextQuery fullTextQuery, QueryContext queryContext ) {
    	if (fullTextQuery != null) {
        	fullTextQuery.setFirstResult(queryContext.getOffset());
        	fullTextQuery.setMaxResults(queryContext.getCount());
        	
        	if (queryContext.getOrderBy() != null && !queryContext.getOrderBy().isEmpty()) {
                    boolean order = false; // to set the reverse order in lucene by default
                    if (!queryContext.isAscending()) {
	        		order = true;      	
	        	}
                    fullTextQuery.setSort(new Sort(new org.apache.lucene.search.SortField(queryContext.getOrderBy(), SortField.STRING, order)));
	        	
        	}
        }
    }
    
}
