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
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManagerFactory;
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
import org.jbpm.services.api.model.NodeInstanceDesc;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import org.jbpm.services.api.model.UserTaskInstanceDesc;
import org.jbpm.services.api.model.VariableDesc;
import org.jbpm.services.task.audit.impl.model.AuditTaskImpl;
import org.jbpm.services.task.audit.impl.model.TaskEventImpl;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.query.QueryContext;
import org.kie.internal.query.QueryFilter;
import org.kie.internal.task.api.AuditTask;
import org.kie.internal.task.api.model.TaskEvent;

/**
 *
 * @author salaboy
 */
public class IndexedRuntimeDataServiceImpl extends AbstractRuntimeDataService {

    

    protected EntityManagerFactory emf;

    public IndexedRuntimeDataServiceImpl(EntityManagerFactory emf) {
        this.emf = emf;

    }


    @Override
    public Collection<ProcessInstanceDesc> getProcessInstances(QueryContext queryContext) {
        return executeQueryAgainstIndex(ProcessInstanceLog.class, queryContext, "", new String[]{},
                new GetProcessInstanceIndexCommand());
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstances(List<Integer> states, String initiator, QueryContext queryContext) {
        return executeQueryAgainstIndex(ProcessInstanceLog.class, queryContext, "", new String[]{},
                new GetProcessIntanceByStatusAndInitiator(states, initiator));
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessId(List<Integer> states, String processId, String initiator, QueryContext queryContext) {
        return executeQueryAgainstIndex(ProcessInstanceLog.class, queryContext, "", new String[]{},
                new GetProcessInstanceByProcessIdStatusAndInitiator(processId, states, initiator));
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessName(List<Integer> states, String processName, String initiator, QueryContext queryContext) {
        return executeQueryAgainstIndex(ProcessInstanceLog.class, queryContext, "", new String[]{},
                new GetProcessInstanceByProcessNameStatusAndInitiator(processName, states, initiator));
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByDeploymentId(String deploymentId, List<Integer> states, QueryContext queryContext) {
        return executeQueryAgainstIndex(ProcessInstanceLog.class, queryContext, "", new String[]{},
                new GetProcessInstanceByDeploymentAndStatus(deploymentId, states));
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessDefinition(String processDefId, QueryContext queryContext) {
        return executeQueryAgainstIndex(ProcessInstanceLog.class, queryContext, "", new String[]{},
                new GetProcessInstanceByProcessDefinitionId(processDefId));
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessDefinition(String processDefId, List<Integer> states, QueryContext queryContext) {
        return executeQueryAgainstIndex(ProcessInstanceLog.class, queryContext, "", new String[]{},
                new GetProcessInstanceByProcessDefinitionIdAndStatus(processDefId, states));
    }

    @Override
    // This one should be done with queries to the DB which should be more efficient than the index because it is a query by ID
    public ProcessInstanceDesc getProcessInstanceById(long processInstanceId) {

        ProcessInstanceDesc pi = (ProcessInstanceDesc) executeQueryAgainstIndex(ProcessInstanceLog.class, true, null, "", new String[]{},
                new GetProcessInstanceByIdIndexCommand(processInstanceId));

        List<String> states = new ArrayList<String>();
                states.add(Status.Ready.name());
                states.add(Status.Reserved.name());
                states.add(Status.InProgress.name());
        List<AuditTaskImpl> auditTaskImpls = executeQueryAgainstIndex(AuditTaskImpl.class, null, "", new String[]{},
                new GetTasksByProcessInstanceIdAndStatusIndexCommand(processInstanceId, states));
        List<UserTaskInstanceDesc> userTaskInstanceDescs = UserTaskInstanceDescHelper.adaptCollection(auditTaskImpls);
        ((org.jbpm.kie.services.impl.model.ProcessInstanceDesc) pi).setActiveTasks(userTaskInstanceDescs);

        return pi;

    }

    @Override
    public NodeInstanceDesc getNodeInstanceForWorkItem(Long workItemId) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(NodeInstanceLog.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            Query query = bool.must(qb.keyword().onField("workItemId").matching(workItemId).createQuery())
                    .createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, NodeInstanceLog.class);

            NodeInstanceLog nodeInstanceLog = (NodeInstanceLog) fullTextQuery.getSingleResult();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return NodeInstanceDescHelper.adapt(nodeInstanceLog);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceHistoryActive(long processInstanceId, QueryContext queryContext) {
        return getProcessInstanceHistory(processInstanceId, false, queryContext);
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceHistoryCompleted(long processInstanceId, QueryContext queryContext) {
        return getProcessInstanceHistory(processInstanceId, true, queryContext);
    }

    protected Collection<NodeInstanceDesc> getProcessInstanceHistory(long processInstanceId, boolean completed, QueryContext queryContext) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(NodeInstanceLog.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery());
            if (completed) {
                bool.must(qb.keyword().onField("type").matching(1).createQuery());
            } else {
                bool.must(qb.keyword().onField("type").matching(0).createQuery());
            }

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, NodeInstanceLog.class);
            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, queryContext);

            List<NodeInstanceLog> nodeInstanceLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return NodeInstanceDescHelper.adaptCollection(nodeInstanceLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceFullHistory(long processInstanceId, QueryContext queryContext) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(NodeInstanceLog.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery());

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, NodeInstanceLog.class);
            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, queryContext);

            List<NodeInstanceLog> nodeInstanceLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return NodeInstanceDescHelper.adaptCollection(nodeInstanceLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceFullHistoryByType(long processInstanceId, EntryType type, QueryContext queryContext) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(NodeInstanceLog.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery());
            bool.must(qb.keyword().onField("type").matching(type).createQuery());
            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, NodeInstanceLog.class);
            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, queryContext);

            List<NodeInstanceLog> nodeInstanceLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return NodeInstanceDescHelper.adaptCollection(nodeInstanceLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public Collection<VariableDesc> getVariablesCurrentState(long processInstanceId) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(VariableInstanceLog.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery());
            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, VariableInstanceLog.class);

            List<VariableInstanceLog> variableLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return VariableDescHelper.adaptCollection(variableLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public Collection<VariableDesc> getVariableHistory(long processInstanceId, String variableId, QueryContext queryContext) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(VariableInstanceLog.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery());
            bool.must(qb.keyword().onField("variableId").matching(variableId).createQuery());
            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, VariableInstanceLog.class);
            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, queryContext);

            List<VariableInstanceLog> variableLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return VariableDescHelper.adaptCollection(variableLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

   

    @Override
    public UserTaskInstanceDesc getTaskByWorkItemId(Long workItemId) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().onField("workItemId").matching(workItemId).createQuery());

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);

            AuditTaskImpl auditTaskLog = (AuditTaskImpl) fullTextQuery.getSingleResult();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return UserTaskInstanceDescHelper.adapt(auditTaskLog);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public UserTaskInstanceDesc getTaskById(Long taskId) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().onField("taskId").matching(taskId).createQuery());

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);

            AuditTaskImpl auditTaskLog = (AuditTaskImpl) fullTextQuery.getSingleResult();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return UserTaskInstanceDescHelper.adapt(auditTaskLog);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsBusinessAdministrator(String userId, QueryFilter filter) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();
            Map<String, Object> params = filter.getParams();
            //Get the parameters from the query filter and create filter for()
            for (String key : params.keySet()) {
                bool.must(qb.keyword().onField(key).matching(params.get(key)).createQuery());
            }

            bool.must(qb.keyword().onField("businessAdministrators").matching(userId).createQuery());

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
            applyQueryContext(fullTextQuery, filter);

            fullTextQuery.setProjection("taskId", "name", "description", "status",
                    "priority", "actualOwner", "createdBy", "createdOn", "activationTime",
                    "dueDate", "process", "processInstanceId", "parentId", "deployent"); //, ProjectionConstants.EXPLANATION, ProjectionConstants.DOCUMENT);
            fullTextQuery.setResultTransformer(new TaskSummaryResultTransformer());

            return (List<TaskSummary>) fullTextQuery.getResultList();
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsBusinessAdministratorByStatus(String userId, List<Status> statuses, QueryFilter filter) {
        throw new UnsupportedOperationException("This method wasn't implemented against the audit logs"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, QueryFilter filter) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().wildcard().onField("potentialOwners").matching(userId + "*").createQuery());

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
            applyQueryContext(fullTextQuery, filter);

            fullTextQuery.setProjection("taskId", "name", "description", "status",
                    "priority", "actualOwner", "createdBy", "createdOn", "activationTime",
                    "dueDate", "process", "processInstanceId", "parentId", "deployent"); //, ProjectionConstants.EXPLANATION, ProjectionConstants.DOCUMENT);
            fullTextQuery.setResultTransformer(new TaskSummaryResultTransformer());
            return (List<TaskSummary>) fullTextQuery.getResultList();
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, List<String> groupIds, QueryFilter filter) {
        throw new UnsupportedOperationException("This method wasn't implemented against the audit logs"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwnerByStatus(String userId, List<Status> states, QueryFilter queryContext) {
        List<String> status = new ArrayList<String>(states.size());
        for(Status s : states){
            status.add(s.toString());
        }
        return executeQueryAgainstIndex(AuditTaskImpl.class, queryContext, "", new String[]{},
                new GetTasksByPotentialOwnerAndStatusIndexCommand(status, userId ));
        
        
        
//        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
//        try {
//            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();
//
//            BooleanJunction<BooleanJunction> bool = qb.bool();
//            if (states != null && !states.isEmpty()) {
//                for (Status st : states) {
//                    bool.should(qb.keyword().onField("status").matching(st.toString()).createQuery());
//                }
//
//            }
//            bool.must(qb.keyword().wildcard().onField("potentialOwners").matching(userId + "*").createQuery());
//
//            Query query = bool.createQuery();
//
//            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
//            fullTextQuery.setProjection("taskId", "name", "description", "status",
//                    "priority", "actualOwner", "createdBy", "createdOn", "activationTime",
//                    "dueDate", "process", "processInstanceId", "parentId", "deployent"); //, ProjectionConstants.EXPLANATION, ProjectionConstants.DOCUMENT);
//            fullTextQuery.setResultTransformer(new TaskSummaryResultTransformer());
//            return (List<TaskSummary>) fullTextQuery.getResultList();
//        } finally {
//            fullTextEntityManager.close();
//        }
    }

    protected String extractGroupIdsString(List<String> groupIds) {
        StringBuilder sb = new StringBuilder();
        for (String g : groupIds) {
            sb.append(g).append(",");
        }
        return sb.toString();
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, List<String> groupIds, List<Status> status, QueryFilter filter) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();

            Query query = qb.all().createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
            if (status != null && !status.isEmpty()) {
                for (Status s : status) {
                    fullTextQuery.enableFullTextFilter("status").setParameter("status", s.toString());
                }
            }
            List<String> potentialOwners = new ArrayList<String>();
            potentialOwners.add(userId);
            potentialOwners.addAll(groupIds);

            for (String potentialOwner : potentialOwners) {
                fullTextQuery.enableFullTextFilter("potentialOwner").setParameter("potentialOwner", potentialOwner);
            }

            fullTextQuery.setProjection("taskId", "name", "description", "status",
                    "priority", "actualOwner", "createdBy", "createdOn", "activationTime",
                    "dueDate", "process", "processInstanceId", "parentId", "deployent"); //, ProjectionConstants.EXPLANATION, ProjectionConstants.DOCUMENT);
            fullTextQuery.setResultTransformer(new TaskSummaryResultTransformer());
            return (List<TaskSummary>) fullTextQuery.getResultList();
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwnerByExpirationDateOptional(String userId, List<Status> status, Date from, QueryFilter filter) {
        throw new UnsupportedOperationException("This method wasn't implemented against the audit logs"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksOwnedByExpirationDateOptional(String userId, List<Status> strStatuses, Date from, QueryFilter filter) {
        throw new UnsupportedOperationException("This method wasn't implemented against the audit logs"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksOwned(String userId, QueryFilter filter) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();

            Query query = qb.all().createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);

            fullTextQuery.enableFullTextFilter("owner").setParameter("owner", userId);
            applyQueryContext(fullTextQuery, filter);

            fullTextQuery.setProjection("taskId", "name", "description", "status",
                    "priority", "actualOwner", "createdBy", "createdOn", "activationTime",
                    "dueDate", "process", "processInstanceId", "parentId", "deployent"); //, ProjectionConstants.EXPLANATION, ProjectionConstants.DOCUMENT);
            fullTextQuery.setResultTransformer(new TaskSummaryResultTransformer());
            return (List<TaskSummary>) fullTextQuery.getResultList();
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public List<TaskSummary> getTasksOwnedByStatus(String userId, List<Status> status, QueryFilter filter) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();

            Query query = qb.all().createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
            if (status != null && !status.isEmpty()) {
                for (Status s : status) {
                    fullTextQuery.enableFullTextFilter("status").setParameter("status", s.toString());
                }
            }
            fullTextQuery.enableFullTextFilter("owner").setParameter("owner", userId);
            applyQueryContext(fullTextQuery, filter);

            fullTextQuery.setProjection("taskId", "name", "description", "status",
                    "priority", "actualOwner", "createdBy", "createdOn", "activationTime",
                    "dueDate", "process", "processInstanceId", "parentId", "deployent"); //, ProjectionConstants.EXPLANATION, ProjectionConstants.DOCUMENT);
            fullTextQuery.setResultTransformer(new TaskSummaryResultTransformer());
            return (List<TaskSummary>) fullTextQuery.getResultList();
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public List<Long> getTasksByProcessInstanceId(Long processInstanceId) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery());

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);

            List<AuditTask> auditTaskLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
            List<Long> taskIds = new ArrayList<Long>();
            for (AuditTask at : auditTaskLogs) {
                taskIds.add(at.getTaskId());
            }
            return taskIds;
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public List<TaskSummary> getTasksByStatusByProcessInstanceId(Long processInstanceId, List<Status> status, QueryFilter filter) {
        throw new UnsupportedOperationException("This method wasn't implemented against the audit logs"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<AuditTask> getAllAuditTask(String userId, QueryFilter filter) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().onField("actualOwner").matching(userId).createQuery());

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);

            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, filter);

            List<AuditTask> auditTaskLog = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return auditTaskLog;
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public List<TaskEvent> getTaskEvents(long taskId, QueryFilter filter) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(TaskEventImpl.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().onField("taskId").matching(taskId).createQuery());

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, TaskEventImpl.class);

            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, filter);

            List<TaskEvent> taskEventsLog = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return taskEventsLog;
        } finally {
            fullTextEntityManager.close();
        }
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

    

    protected void applyQueryContext(FullTextQuery fullTextQuery, QueryContext queryContext) {
        if (fullTextQuery != null && queryContext != null) {
            fullTextQuery.setFirstResult(queryContext.getOffset());
            fullTextQuery.setMaxResults(queryContext.getCount());

            if (queryContext.getOrderBy() != null && !queryContext.getOrderBy().isEmpty()) {
                boolean order = false; // to set the reverse order in lucene by default
                if (!queryContext.isAscending()) {
                    order = true;
                }
                fullTextQuery.setSort(new Sort(new SortField(queryContext.getOrderBy(), SortField.STRING, order)));

            }
        }
    }

    private class BaseTaskIndexedCommand implements IndexCommand {

        @Override
        public void execute(FullTextQuery fullTextQuery) {

            fullTextQuery.setProjection("taskId", "name", "description", "status", "priority", "actualOwner", "createdBy",
                    "createdOn", "activationTime", "dueDate", "processId", "processInstanceId", "parentId",
                    "deploymentId");
            fullTextQuery.setResultTransformer(new TaskSummaryResultTransformer());
        }

    }

    private class GetTasksByPotentialOwnerIndexCommand extends BaseTaskIndexedCommand {
        private String owner;

        public GetTasksByPotentialOwnerIndexCommand(String owner) {
            this.owner = owner;
        }
        
        
        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (owner != null && !owner.isEmpty()) {
                    fullTextQuery.enableFullTextFilter("potentialOwner").setParameter("potentialOwner", owner);
            }
            super.execute(fullTextQuery);
        }

    }
    
    private class GetTasksByPotentialOwnerAndStatusIndexCommand extends GetTasksByStatusIndexCommand {
        private String owner;

        public GetTasksByPotentialOwnerAndStatusIndexCommand(List<String> states, String owner) {
            super(states);
            this.owner = owner;
        }
        
        
        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (owner != null && !owner.isEmpty()) {
                    fullTextQuery.enableFullTextFilter("potentialOwner").setParameter("potentialOwner", owner);
            }
            super.execute(fullTextQuery);
        }

    }
    
    
    private class GetTasksByStatusIndexCommand extends BaseTaskIndexedCommand {
        private List<String> states;

        public GetTasksByStatusIndexCommand(List<String> states) {
            this.states = states;
        }
        
        
        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (states != null && !states.isEmpty()) {
                for (String state : states) {
                    fullTextQuery.enableFullTextFilter("states").setParameter("state", state);
                }
            }
            super.execute(fullTextQuery);
        }

    }

    private class GetTasksByProcessInstanceIdAndStatusIndexCommand extends GetTasksByStatusIndexCommand {

        private Long processInstanceId;
       
        public GetTasksByProcessInstanceIdAndStatusIndexCommand(Long processInstanceId, List<String> states) {
            super(states);
            this.processInstanceId = processInstanceId;
            
        }
        
        
        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (processInstanceId != null && processInstanceId > 0) {
                fullTextQuery.enableFullTextFilter("processInstanceId").setParameter("processInstanceId", processInstanceId);
            }
            super.execute(fullTextQuery);
        }

    }

    private class BaseProcessInstanceIndexCommand implements IndexCommand {

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            fullTextQuery.setProjection("processInstanceId", "processId", "processName", "processVersion",
                    "status", "externalId", "start", "initiator",
                    "processInstanceDescription"); //, ProjectionConstants.EXPLANATION, ProjectionConstants.DOCUMENT);
            fullTextQuery.setResultTransformer(new ProcessInstanceDescResultTransformer());
        }

    }

    private class GetProcessInstanceByIdIndexCommand extends GetProcessInstanceIndexCommand {

        Long processInstanceId;

        public GetProcessInstanceByIdIndexCommand(Long processInstanceId) {
            this.processInstanceId = processInstanceId;
        }

        public GetProcessInstanceByIdIndexCommand() {
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (processInstanceId != null && processInstanceId > 0) {
                fullTextQuery.enableFullTextFilter("processInstanceId").setParameter("processInstanceId", processInstanceId);
            }
            super.execute(fullTextQuery);

        }
    }

    private class GetProcessInstanceIndexCommand extends BaseProcessInstanceIndexCommand {

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            List<String> deploymentIdForUser = getDeploymentsForUser();
            if (deploymentIdForUser != null && !deploymentIdForUser.isEmpty()) {
                for (String deployment : deploymentIdForUser) {
                    fullTextQuery.enableFullTextFilter("deployment").setParameter("deployment", deployment);
                }
            }
            super.execute(fullTextQuery);

        }
    }

    private class GetProcessInstanceByStatus extends GetProcessInstanceIndexCommand {

        private List<Integer> states;

        public GetProcessInstanceByStatus(List<Integer> states) {
            this.states = states;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (states != null && !states.isEmpty()) {
                for (Integer i : states) {
                    fullTextQuery.enableFullTextFilter("status").setParameter("status", i);
                }
            }
            super.execute(fullTextQuery);

        }
    }

    private class GetProcessIntanceByStatusAndInitiator extends GetProcessInstanceByStatus {

        private String initiator;

        public GetProcessIntanceByStatusAndInitiator(List<Integer> states, String initiator) {
            super(states);
            this.initiator = initiator;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (initiator != null && !initiator.isEmpty()) {
                fullTextQuery.enableFullTextFilter("initiator").setParameter("initiator", initiator);
            }
            super.execute(fullTextQuery);

        }
    }

    private class GetProcessInstanceByProcessNameAndStatus extends GetProcessInstanceByStatus {

        private String processName;

        public GetProcessInstanceByProcessNameAndStatus(String processName, List<Integer> states) {
            super(states);
            this.processName = processName;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            fullTextQuery.enableFullTextFilter("processName").setParameter("processName", processName);
            super.execute(fullTextQuery);

        }
    }

    private class GetProcessInstanceByProcessDefinitionIdAndStatus extends GetProcessInstanceByStatus {

        private String processId;

        public GetProcessInstanceByProcessDefinitionIdAndStatus(String processId, List<Integer> states) {
            super(states);
            this.processId = processId;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (processId != null && !processId.isEmpty()) {
                fullTextQuery.enableFullTextFilter("processId").setParameter("processId", processId);
            }
            super.execute(fullTextQuery);

        }
    }

    private class GetProcessInstanceByProcessIdStatusAndInitiator extends GetProcessIntanceByStatusAndInitiator {

        private String processId;

        public GetProcessInstanceByProcessIdStatusAndInitiator(String processId, List<Integer> states, String initiator) {
            super(states, initiator);
            this.processId = processId;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {

            fullTextQuery.enableFullTextFilter("processId").setParameter("processId", processId);
            super.execute(fullTextQuery);

        }
    }

    private class GetProcessInstanceByDeploymentAndStatus extends GetProcessInstanceByStatus {

        private String deploymentId;

        public GetProcessInstanceByDeploymentAndStatus(String deploymentId, List<Integer> states) {
            super(states);
            this.deploymentId = deploymentId;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            fullTextQuery.enableFullTextFilter("deployment").setParameter("deploymentId", deploymentId);
            super.execute(fullTextQuery);

        }

    }

    private class GetProcessInstanceByProcessNameStatusAndInitiator extends GetProcessIntanceByStatusAndInitiator {

        private String processName;

        public GetProcessInstanceByProcessNameStatusAndInitiator(String processName, List<Integer> states, String initiator) {
            super(states, initiator);
            this.processName = processName;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (processName != null && !processName.isEmpty()) {
                fullTextQuery.enableFullTextFilter("processName").setParameter("processName", processName);
            }
            super.execute(fullTextQuery);

        }

    }

    private class GetProcessInstanceByProcessName extends GetProcessInstanceIndexCommand {

        private String processName;

        public GetProcessInstanceByProcessName(String processName) {
            super();
            this.processName = processName;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (processName != null && !processName.isEmpty()) {
                fullTextQuery.enableFullTextFilter("processName").setParameter("processName", processName);
            }
            super.execute(fullTextQuery);

        }

    }

    private class GetProcessInstanceByProcessDefinitionId extends GetProcessInstanceIndexCommand {

        private String processId;

        public GetProcessInstanceByProcessDefinitionId(String processId) {
            super();
            this.processId = processId;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (processId != null && !processId.isEmpty()) {
                fullTextQuery.enableFullTextFilter("processId").setParameter("processId", processId);
            }
            super.execute(fullTextQuery);

        }

    }

    protected List executeQueryAgainstIndex(Class entity, QueryContext queryContext, String keyword, String[] fields, IndexCommand cmd) {
        return (List) executeQueryAgainstIndex(entity, false, queryContext, keyword, fields, cmd);
    }

    protected Object executeQueryAgainstIndex(Class entity, boolean single, QueryContext queryContext, String keyword, String[] fields, IndexCommand cmd) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(entity).get();
            Query query;
            if (keyword != null && !keyword.isEmpty() && fields.length > 0) {
                String matching;
                if (keyword.contains("%")) {
                    matching = keyword.replace("%", "*");
                } else {
                    matching = keyword;
                }
                query = qb.keyword().onFields(fields).matching(matching).createQuery();
            } else {
                query = qb.all().createQuery();
            }
            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, entity);

            cmd.execute(fullTextQuery);
            applyQueryContext(fullTextQuery, queryContext);
            if(single){
                return fullTextQuery.getSingleResult();
            }else{
                return fullTextQuery.getResultList();
            }
        } finally {
            fullTextEntityManager.close();
        }
    }

}
