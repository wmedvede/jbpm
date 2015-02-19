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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.persistence.EntityManagerFactory;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;

import org.hibernate.search.ProjectionConstants;

import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.jpa.Search;
import org.hibernate.search.query.dsl.BooleanJunction;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.jbpm.kie.services.impl.model.ProcessAssetDesc;
import org.jbpm.process.audit.NodeInstanceLog;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.process.audit.VariableInstanceLog;
import org.jbpm.services.api.DeploymentEvent;
import org.jbpm.services.api.DeploymentEventListener;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.model.DeployedAsset;
import org.jbpm.services.api.model.NodeInstanceDesc;
import org.jbpm.services.api.model.ProcessDefinition;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import org.jbpm.services.api.model.UserTaskInstanceDesc;
import org.jbpm.services.api.model.VariableDesc;
import org.jbpm.services.task.audit.impl.model.AuditTaskImpl;
import org.jbpm.services.task.audit.impl.model.TaskEventImpl;
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
public class IndexedRuntimeDataServiceImpl implements RuntimeDataService, DeploymentEventListener {

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

    protected EntityManagerFactory emf;

    public IndexedRuntimeDataServiceImpl(EntityManagerFactory emf) {
        this.emf = emf;

    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstances(QueryContext queryContext) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();

            // Apply query filters 
            Query filters = applyDeploymentFilter(qb);
            BooleanJunction<BooleanJunction> bool = qb.bool();

            if (filters != null) {
                bool.must(filters);
            }
            Query query = bool.must(qb.all().createQuery()).createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, queryContext);

            List<ProcessInstanceLog> processInstanceLogs = fullTextQuery.getResultList();

            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
            return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
        } finally {
            fullTextEntityManager.close();
        }

    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstances(List<Integer> states, String initiator, QueryContext queryContext) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();

            // Apply query filters 
            Query filters = applyDeploymentFilter(qb);

            BooleanJunction<BooleanJunction> bool = qb.bool();

            if (filters != null) {
                bool.must(filters);
            }
            if (initiator != null) {
                bool.must(qb.keyword().onField("initiator").matching(initiator).createQuery());
            }
            for (Integer state : states) {
                bool.must(qb.keyword().onField("status").matching(state).createQuery());
            }
            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, queryContext);

            List<ProcessInstanceLog> processInstanceLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessId(List<Integer> states, String processId, String initiator, QueryContext queryContext) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();

            // Apply query filters 
            Query filters = applyDeploymentFilter(qb);

            BooleanJunction<BooleanJunction> bool = qb.bool();

            if (filters != null) {
                bool.must(filters);
            }
            if (initiator != null) {
                bool.must(qb.keyword().onField("initiator").matching(initiator).createQuery());
            }
            for (Integer state : states) {
                bool.must(qb.keyword().onField("status").matching(state).createQuery());
            }
            if (processId != null && processId.contains("%")) {
                String wildcardReplaced = processId.replace("%", "*");
                bool.must(qb.keyword().wildcard().onField("processId").matching(wildcardReplaced).createQuery());
            } else {
                bool.must(qb.keyword().onField("processId").matching(processId).createQuery());
            }
            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, queryContext);

            List<ProcessInstanceLog> processInstanceLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessName(List<Integer> states, String processName, String initiator, QueryContext queryContext) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();

            // Apply query filters 
            Query filters = applyDeploymentFilter(qb);

            BooleanJunction<BooleanJunction> bool = qb.bool();

            if (filters != null) {
                bool.must(filters);
            }
            if (initiator != null) {
                bool.must(qb.keyword().onField("initiator").matching(initiator).createQuery());
            }
            for (Integer state : states) {
                bool.must(qb.keyword().onField("status").matching(state).createQuery());
            }
            if (processName != null && processName.contains("%")) {
                String replacedWildcard = processName.replace("%", "*");
                bool.must(qb.keyword().wildcard().onField("processName").matching(replacedWildcard).createQuery());
            } else {
                bool.must(qb.keyword().onField("processName").matching(processName).createQuery());
            }
            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, queryContext);

            List<ProcessInstanceLog> processInstanceLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByDeploymentId(String deploymentId, List<Integer> states, QueryContext queryContext) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();

            // Apply query filters 
            Query filters = applyDeploymentFilter(qb);

            BooleanJunction<BooleanJunction> bool = qb.bool();

            if (filters != null) {
                bool.must(filters);
            }

            bool.must(qb.keyword().onField("externalId").matching(deploymentId).createQuery());
            for (Integer state : states) {
                bool.must(qb.keyword().onField("status").matching(state).createQuery());
            }
            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, queryContext);

            List<ProcessInstanceLog> processInstanceLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public ProcessInstanceDesc getProcessInstanceById(long processInstanceId) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();

            // Apply query filters 
            Query filters = applyDeploymentFilter(qb);

            BooleanJunction<BooleanJunction> bool = qb.bool();

            if (filters != null) {
                bool.must(filters);
            }
            Query query = bool.must(qb.keyword().onField("processInstanceId").matching(processInstanceId).createQuery())
                    .createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);

            ProcessInstanceLog processInstanceLog = (ProcessInstanceLog) fullTextQuery.getSingleResult();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
            ProcessInstanceDesc processInstanceDesc = ProcessInstanceDescHelper.adapt(processInstanceLog);

            if (processInstanceLog != null) {
                List<String> statuses = new ArrayList<String>();
                statuses.add(Status.Ready.name());
                statuses.add(Status.Reserved.name());
                statuses.add(Status.InProgress.name());
                QueryBuilder qbTasks = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();
                BooleanJunction<BooleanJunction> boolTasks = qbTasks.bool();
                boolTasks.must(qb.keyword().onField("processInstanceId").matching(processInstanceDesc.getId()).createQuery());
                for (String status : statuses) {
                    boolTasks.should(qb.keyword().onField("status").matching(status).createQuery());
                }
                Query queryTasks = boolTasks.createQuery();
                FullTextQuery fullTextQueryTasks = fullTextEntityManager.createFullTextQuery(queryTasks, AuditTaskImpl.class);
                List<AuditTaskImpl> auditTaskImpls = fullTextQueryTasks.getResultList();
                ((org.jbpm.kie.services.impl.model.ProcessInstanceDesc) processInstanceDesc).setActiveTasks(UserTaskInstanceDescHelper.adaptCollection(auditTaskImpls));
            }

            return processInstanceDesc;
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessDefinition(String processDefId, QueryContext queryContext) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();

            // Apply query filters 
            Query filters = applyDeploymentFilter(qb);

            BooleanJunction<BooleanJunction> bool = qb.bool();

            if (filters != null) {
                bool.must(filters);
            }
            Query query = bool.must(qb.keyword().onField("processId").matching(processDefId).createQuery())
                    .createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, queryContext);

            List<ProcessInstanceLog> processInstanceLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessDefinition(String processDefId, List<Integer> states, QueryContext queryContext) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(ProcessInstanceLog.class).get();

            // Apply query filters 
            Query filters = applyDeploymentFilter(qb);

            BooleanJunction<BooleanJunction> bool = qb.bool();

            if (filters != null) {
                bool.must(filters);
            }

            bool.must(qb.keyword().onField("processId").matching(processDefId).createQuery());
            for (Integer state : states) {
                bool.must(qb.keyword().onField("status").matching(state).createQuery());
            }
            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
            //Apply Pagination & Sort Here
            applyQueryContext(fullTextQuery, queryContext);

            List<ProcessInstanceLog> processInstanceLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return ProcessInstanceDescHelper.adaptCollection(processInstanceLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public NodeInstanceDesc getNodeInstanceForWorkItem(Long workItemId) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(NodeInstanceLog.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();

            Query query = bool.must(qb.keyword().onField("workItemId").matching(workItemId).createQuery())
                    .createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);

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

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
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

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
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

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);
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

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);

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

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, ProcessInstanceLog.class);

            List<VariableInstanceLog> variableLogs = fullTextQuery.getResultList();
            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc

            return VariableDescHelper.adaptCollection(variableLogs);
        } finally {
            fullTextEntityManager.close();
        }
    }

    /*
     * start
     * process definition methods
     */
    public Collection<ProcessDefinition> getProcessesByDeploymentId(String deploymentId, QueryContext queryContext) {
        List<ProcessDefinition> outputCollection = new ArrayList<ProcessDefinition>();
        CollectionUtils.select(availableProcesses, new ByDeploymentIdPredicate(deploymentId, identityProvider.getRoles()), outputCollection);

        applySorting(outputCollection, queryContext);
        return applyPaginition(outputCollection, queryContext);
    }

    public ProcessDefinition getProcessesByDeploymentIdProcessId(String deploymentId, String processId) {
        List<ProcessDefinition> outputCollection = new ArrayList<ProcessDefinition>();
        CollectionUtils.select(availableProcesses, new ByDeploymentIdProcessIdPredicate(deploymentId, processId, identityProvider.getRoles(), true), outputCollection);

        if (!outputCollection.isEmpty()) {
            return outputCollection.iterator().next();
        }
        return null;
    }

    public Collection<ProcessDefinition> getProcessesByFilter(String filter, QueryContext queryContext) {
        List<ProcessDefinition> outputCollection = new ArrayList<ProcessDefinition>();
        CollectionUtils.select(availableProcesses, new RegExPredicate("(?i)^.*" + filter + ".*$", identityProvider.getRoles()), outputCollection);

        applySorting(outputCollection, queryContext);
        return applyPaginition(outputCollection, queryContext);
    }

    public ProcessDefinition getProcessById(String processId) {

        Collection<ProcessAssetDesc> outputCollection = new HashSet<ProcessAssetDesc>();
        CollectionUtils.select(availableProcesses, new ByProcessIdPredicate(processId, identityProvider.getRoles()), outputCollection);
        if (!outputCollection.isEmpty()) {
            return outputCollection.iterator().next();
        }
        return null;
    }

    public Collection<ProcessDefinition> getProcesses(QueryContext queryContext) {
        List<ProcessDefinition> outputCollection = new ArrayList<ProcessDefinition>();
        CollectionUtils.select(availableProcesses, new SecurePredicate(identityProvider.getRoles(), false), outputCollection);

        applySorting(outputCollection, queryContext);
        return applyPaginition(outputCollection, queryContext);
    }

    @Override
    public Collection<String> getProcessIds(String deploymentId, QueryContext queryContext) {
        List<String> processIds = new ArrayList<String>(availableProcesses.size());
        if (deploymentId == null || deploymentId.isEmpty()) {
            return processIds;
        }
        for (ProcessDefinition procAssetDesc : availableProcesses) {
            if (((ProcessAssetDesc) procAssetDesc).getDeploymentId().equals(deploymentId) && ((ProcessAssetDesc) procAssetDesc).isActive()) {
                processIds.add(procAssetDesc.getId());
            }
        }
        return applyPaginition(processIds, queryContext);
    }
    /*
     * end
     * process definition methods
     */

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
            for(String key : params.keySet()){
                bool.must(qb.keyword().onField(key).matching(params.get(key)).createQuery());
            }

            bool.must(qb.keyword().onField("businessAdministrators").matching(userId).createQuery());

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
            applyQueryContext(fullTextQuery, filter);

            fullTextQuery.setProjection("taskId", "name", "description", "status",
                    "priority", "actualOwner", "createdBy", "createdOn", "activationTime",
                    "dueDate", "process", "processInstanceId", "parentId", "deployent", ProjectionConstants.EXPLANATION,
                    ProjectionConstants.DOCUMENT);//.setResultTransformer();
            List result = fullTextQuery.getResultList();
            List<TaskSummary> summaries = new ArrayList<TaskSummary>(result.size());
            for (Object o : result) {
                Object[] oa = (Object[]) o;
                summaries.add(new org.jbpm.services.task.query.TaskSummaryImpl((Long) oa[0], (String) oa[1], (String) oa[2], Status.valueOf((String) oa[3]), (Integer) oa[4], (String) oa[5],
                        (String) oa[6], (Date) oa[7], (Date) oa[8], (Date) oa[9], (String) oa[10], (Long) oa[11], (Long) oa[12], (String) oa[13]));
                System.out.println("Explanation: " + oa[14]);
                System.out.println("Document: " + oa[15]);
            }
            return summaries;
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
                    "dueDate", "process", "processInstanceId", "parentId", "deployent");
            List result = fullTextQuery.getResultList();
            List<TaskSummary> summaries = new ArrayList<TaskSummary>(result.size());
            for (Object o : result) {
                Object[] oa = (Object[]) o;
                summaries.add(new org.jbpm.services.task.query.TaskSummaryImpl((Long) oa[0], (String) oa[1], (String) oa[2], Status.valueOf((String) oa[3]), (Integer) oa[4], (String) oa[5],
                        (String) oa[6], (Date) oa[7], (Date) oa[8], (Date) oa[9], (String) oa[10], (Long) oa[11], (Long) oa[12], (String) oa[13]));
            }
            return summaries;
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, List<String> groupIds, QueryFilter filter) {
        throw new UnsupportedOperationException("This method wasn't implemented against the audit logs"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwnerByStatus(String userId, List<Status> status, QueryFilter filter) {
        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
        try {
            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();

            BooleanJunction<BooleanJunction> bool = qb.bool();
            if (status != null && !status.isEmpty()) {
                for (Status st : status) {
                    bool.should(qb.keyword().onField("status").matching(st.toString()).createQuery());
                }

            }
            bool.must(qb.keyword().wildcard().onField("potentialOwners").matching(userId + "*").createQuery());

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
            fullTextQuery.setProjection("taskId", "name", "description", "status",
                    "priority", "actualOwner", "createdBy", "createdOn", "activationTime",
                    "dueDate", "process", "processInstanceId", "parentId", "deployent");
            applyQueryContext(fullTextQuery, filter);
            List result = fullTextQuery.getResultList();
            List<TaskSummary> summaries = new ArrayList<TaskSummary>(result.size());
            for (Object o : result) {
                Object[] oa = (Object[]) o;
                summaries.add(new org.jbpm.services.task.query.TaskSummaryImpl((Long) oa[0], (String) oa[1], (String) oa[2], Status.valueOf((String) oa[3]), (Integer) oa[4], (String) oa[5],
                        (String) oa[6], (Date) oa[7], (Date) oa[8], (Date) oa[9], (String) oa[10], (Long) oa[11], (Long) oa[12], (String) oa[13]));
            }
            return summaries;
        } finally {
            fullTextEntityManager.close();
        }
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

            BooleanJunction<BooleanJunction> bool = qb.bool();
            if (status != null && !status.isEmpty()) {
                for (Status st : status) {
                    bool.should(qb.keyword().onField("status").matching(st.toString()).createQuery());
                }
            }
            bool.must(qb.keyword().onField("potentialOwners").matching(userId).createQuery());
            if (groupIds != null && !groupIds.isEmpty()) {
                String groupIdsString = extractGroupIdsString(groupIds);
                bool.must(qb.keyword().onField("potentialOwners").matching(groupIdsString).createQuery());
            }

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
            fullTextQuery.setProjection("taskId", "name", "description", "status",
                    "priority", "actualOwner", "createdBy", "createdOn", "activationTime",
                    "dueDate", "process", "processInstanceId", "parentId", "deployent");
            List result = fullTextQuery.getResultList();
            List<TaskSummary> summaries = new ArrayList<TaskSummary>(result.size());
            for (Object o : result) {
                Object[] oa = (Object[]) o;
                summaries.add(new org.jbpm.services.task.query.TaskSummaryImpl((Long) oa[0], (String) oa[1], (String) oa[2], Status.valueOf((String) oa[3]), (Integer) oa[4], (String) oa[5],
                        (String) oa[6], (Date) oa[7], (Date) oa[8], (Date) oa[9], (String) oa[10], (Long) oa[11], (Long) oa[12], (String) oa[13]));
            }
            return summaries;
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

            BooleanJunction<BooleanJunction> bool = qb.bool();

            bool.must(qb.keyword().onField("actualOwner").matching(userId).createQuery());

            Query query = bool.createQuery();

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
            fullTextQuery.setProjection("taskId", "name", "description", "status",
                    "priority", "actualOwner", "createdBy", "createdOn", "activationTime",
                    "dueDate", "process", "processInstanceId", "parentId", "deployent");
            List result = fullTextQuery.getResultList();
            List<TaskSummary> summaries = new ArrayList<TaskSummary>(result.size());
            for (Object o : result) {
                Object[] oa = (Object[]) o;
                summaries.add(new org.jbpm.services.task.query.TaskSummaryImpl((Long) oa[0], (String) oa[1], (String) oa[2], Status.valueOf((String) oa[3]), (Integer) oa[4], (String) oa[5],
                        (String) oa[6], (Date) oa[7], (Date) oa[8], (Date) oa[9], (String) oa[10], (Long) oa[11], (Long) oa[12], (String) oa[13]));
            }
            return summaries;
        } finally {
            fullTextEntityManager.close();
        }
    }

    @Override
    public List<TaskSummary> getTasksOwnedByStatus(String userId, List<Status> status, QueryFilter filter) {
        throw new UnsupportedOperationException("This method wasn't implemented against the audit logs"); //To change body of generated methods, choose Tools | Templates.
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
        for (Map.Entry<String, List<String>> entry : deploymentsRoles.entrySet()) {
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

    protected void applyQueryContext(FullTextQuery fullTextQuery, QueryContext queryContext) {
        if (fullTextQuery != null) {
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

    /*
     * start
     * helper methods to index data upon deployment
     */
    public void onDeploy(DeploymentEvent event) {
        Collection<DeployedAsset> assets = event.getDeployedUnit().getDeployedAssets();
        List<String> roles = null;
        for (DeployedAsset asset : assets) {
            if (asset instanceof ProcessAssetDesc) {
                availableProcesses.add((ProcessAssetDesc) asset);
                if (roles == null) {
                    roles = ((ProcessAssetDesc) asset).getRoles();
                }
            }
        }
        if (roles == null) {
            roles = Collections.emptyList();
        }
        deploymentsRoles.put(event.getDeploymentId(), roles);
        userDeploymentIdsCache.clear();
    }

    public void onUnDeploy(DeploymentEvent event) {
        Collection<ProcessAssetDesc> outputCollection = new HashSet<ProcessAssetDesc>();
        CollectionUtils.select(availableProcesses, new UnsecureByDeploymentIdPredicate(event.getDeploymentId()), outputCollection);

        availableProcesses.removeAll(outputCollection);
        deploymentsRoles.remove(event.getDeploymentId());
        userDeploymentIdsCache.clear();
    }

    @Override
    public void onActivate(DeploymentEvent event) {
        Collection<ProcessAssetDesc> outputCollection = new HashSet<ProcessAssetDesc>();
        CollectionUtils.select(availableProcesses, new UnsecureByDeploymentIdPredicate(event.getDeploymentId()), outputCollection);

        for (ProcessAssetDesc process : outputCollection) {
            process.setActive(true);
        }

    }

    @Override
    public void onDeactivate(DeploymentEvent event) {
        Collection<ProcessAssetDesc> outputCollection = new HashSet<ProcessAssetDesc>();
        CollectionUtils.select(availableProcesses, new UnsecureByDeploymentIdPredicate(event.getDeploymentId()), outputCollection);

        for (ProcessAssetDesc process : outputCollection) {
            process.setActive(false);
        }
    }

    /*
     * start
     * predicates for collection filtering
     */
    private class RegExPredicate extends SecurePredicate {

        private String pattern;

        private RegExPredicate(String pattern, List<String> roles) {
            super(roles, false);
            this.pattern = pattern;
        }

        @Override
        public boolean evaluate(Object object) {
            if (object instanceof ProcessAssetDesc) {
                ProcessAssetDesc pDesc = (ProcessAssetDesc) object;
                boolean hasAccess = super.evaluate(object);
                if (!hasAccess) {
                    return false;
                }
                if (pDesc.getId().matches(pattern)
                        || pDesc.getName().matches(pattern)) {
                    return true;
                }
            }
            return false;
        }

    }

    private class ByDeploymentIdPredicate extends SecurePredicate {

        private String deploymentId;

        private ByDeploymentIdPredicate(String deploymentId, List<String> roles) {
            super(roles, false);
            this.deploymentId = deploymentId;
        }

        @Override
        public boolean evaluate(Object object) {
            if (object instanceof ProcessAssetDesc) {
                ProcessAssetDesc pDesc = (ProcessAssetDesc) object;
                boolean hasAccess = super.evaluate(object);
                if (!hasAccess) {
                    return false;
                }
                if (pDesc.getDeploymentId().equals(deploymentId)) {
                    return true;
                }
            }
            return false;
        }

    }

    private class ByProcessIdPredicate extends SecurePredicate {

        private String processId;

        private ByProcessIdPredicate(String processId, List<String> roles) {
            super(roles, false);
            this.processId = processId;
        }

        @Override
        public boolean evaluate(Object object) {
            if (object instanceof ProcessAssetDesc) {
                ProcessAssetDesc pDesc = (ProcessAssetDesc) object;
                boolean hasAccess = super.evaluate(object);
                if (!hasAccess) {
                    return false;
                }
                if (pDesc.getId().equals(processId)) {
                    return true;
                }
            }
            return false;
        }

    }

    private class ByDeploymentIdProcessIdPredicate extends SecurePredicate {

        private String processId;
        private String depoymentId;

        private ByDeploymentIdProcessIdPredicate(String depoymentId, String processId, List<String> roles) {
            super(roles, false);
            this.depoymentId = depoymentId;
            this.processId = processId;
        }

        private ByDeploymentIdProcessIdPredicate(String depoymentId, String processId, List<String> roles, boolean skipActiveCheck) {
            super(roles, skipActiveCheck);
            this.depoymentId = depoymentId;
            this.processId = processId;
        }

        @Override
        public boolean evaluate(Object object) {
            if (object instanceof ProcessAssetDesc) {
                ProcessAssetDesc pDesc = (ProcessAssetDesc) object;
                boolean hasAccess = super.evaluate(object);
                if (!hasAccess) {
                    return false;
                }
                if (pDesc.getId().equals(processId) && pDesc.getDeploymentId().equals(depoymentId)) {
                    return true;
                }
            }
            return false;
        }
    }

    private class SecurePredicate extends ActiveOnlyPredicate {

        private List<String> roles;
        private boolean skipActivCheck;

        private SecurePredicate(List<String> roles, boolean skipActivCheck) {
            this.roles = roles;
            this.skipActivCheck = skipActivCheck;
        }

        public boolean evaluate(Object object) {
            if (!skipActivCheck) {
                boolean isActive = super.evaluate(object);
                if (!isActive) {
                    return false;
                }
            }
            ProcessAssetDesc pDesc = (ProcessAssetDesc) object;
            if (this.roles == null || this.roles.isEmpty() || pDesc.getRoles() == null || pDesc.getRoles().isEmpty()) {
                return true;
            }

            return CollectionUtils.containsAny(roles, pDesc.getRoles());
        }
    }

    private class UnsecureByDeploymentIdPredicate implements Predicate {

        private String deploymentId;

        private UnsecureByDeploymentIdPredicate(String deploymentId) {
            this.deploymentId = deploymentId;
        }

        @Override
        public boolean evaluate(Object object) {
            if (object instanceof ProcessAssetDesc) {
                ProcessAssetDesc pDesc = (ProcessAssetDesc) object;
                if (pDesc.getDeploymentId().equals(deploymentId)) {
                    return true;
                }
            }
            return false;
        }

    }

    private class ActiveOnlyPredicate implements Predicate {

        private ActiveOnlyPredicate() {
        }

        @Override
        public boolean evaluate(Object object) {
            if (object instanceof ProcessAssetDesc) {
                ProcessAssetDesc pDesc = (ProcessAssetDesc) object;
                if (pDesc.isActive()) {
                    return true;
                }
            }
            return false;
        }

    }

    protected <T> Collection<T> applyPaginition(List<T> input, QueryContext queryContext) {
        if (queryContext != null) {
            int start = queryContext.getOffset();
            int end = start + queryContext.getCount();
            if (input.size() < start) {
                // no elements in given range
                return new ArrayList<T>();
            } else if (input.size() >= end) {
                return Collections.unmodifiableCollection(new ArrayList<T>(input.subList(start, end)));
            } else if (input.size() < end) {
                return Collections.unmodifiableCollection(new ArrayList<T>(input.subList(start, input.size())));
            }

        }

        return Collections.unmodifiableCollection(input);
    }

    protected void applySorting(List<ProcessDefinition> input, final QueryContext queryContext) {
        if (queryContext != null && queryContext.getOrderBy() != null && !queryContext.getOrderBy().isEmpty()) {
            Collections.sort(input, new Comparator<ProcessDefinition>() {

                @Override
                public int compare(ProcessDefinition o1, ProcessDefinition o2) {
                    if ("ProcessName".equals(queryContext.getOrderBy())) {
                        return o1.getName().compareTo(o2.getName());
                    } else if ("ProcessVersion".equals(queryContext.getOrderBy())) {
                        return o1.getVersion().compareTo(o2.getVersion());
                    } else if ("Project".equals(queryContext.getOrderBy())) {
                        return o1.getDeploymentId().compareTo(o2.getDeploymentId());
                    }
                    return 0;
                }
            });

            if (!queryContext.isAscending()) {
                Collections.reverse(input);
            }
        }
    }
    /*
     * end
     * helper methods to index data upon deployment
     */

}
