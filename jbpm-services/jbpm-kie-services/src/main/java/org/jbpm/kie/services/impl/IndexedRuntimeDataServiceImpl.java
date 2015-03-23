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
import org.jbpm.services.task.query.TaskSummaryImpl;
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
        states.add(Status.Ready.toString());
        states.add(Status.Reserved.toString());
        states.add(Status.InProgress.toString());
        List<TaskSummaryImpl> taskSummaryImpl = executeQueryAgainstIndex(AuditTaskImpl.class, null, "", new String[]{},
                new GetTasksByProcessInstanceIdAndStatusIndexCommand(processInstanceId, states));
        List<UserTaskInstanceDesc> userTaskInstanceDescs = UserTaskInstanceDescHelper.adaptCollectionTs(taskSummaryImpl);
        ((org.jbpm.kie.services.impl.model.ProcessInstanceDesc) pi).setActiveTasks(userTaskInstanceDescs);

        return pi;

    }

    @Override
    public NodeInstanceDesc getNodeInstanceForWorkItem(Long workItemId) {
        return (NodeInstanceDesc) executeQueryAgainstIndex(NodeInstanceLog.class, true, null, "", new String[]{},
                new GetNodeInstanceByWorkItemIdIndexCommand(workItemId));

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
        if (completed) {
            return executeQueryAgainstIndex(NodeInstanceLog.class, queryContext, "", new String[]{},
                    new GetNodeInstanceByProcessInstanceIdAndStateIndexCommand(processInstanceId, 1));
        } else {
            return executeQueryAgainstIndex(NodeInstanceLog.class, queryContext, "", new String[]{},
                    new GetNodeInstanceByProcessInstanceIdAndStateIndexCommand(processInstanceId, 0));
        }

    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceFullHistory(long processInstanceId, QueryContext queryContext) {
        return executeQueryAgainstIndex(NodeInstanceLog.class, queryContext, "", new String[]{},
                new GetNodeInstanceByProcessInstanceIdIndexCommand(processInstanceId));

    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceFullHistoryByType(long processInstanceId, EntryType type, QueryContext queryContext) {
        return executeQueryAgainstIndex(NodeInstanceLog.class, queryContext, "", new String[]{},
                new GetNodeInstanceByProcessInstanceIdAndTypeIndexCommand(processInstanceId, type));

    }

    @Override
    public Collection<VariableDesc> getVariablesCurrentState(long processInstanceId) {
        return executeQueryAgainstIndex(VariableInstanceLog.class, null, "", new String[]{},
                new GetVariablesByProcessInstanceIdIndexCommand(processInstanceId));

    }

    @Override
    public Collection<VariableDesc> getVariableHistory(long processInstanceId, String variableId, QueryContext queryContext) {
        return executeQueryAgainstIndex(VariableInstanceLog.class, queryContext, "", new String[]{},
                new GetVariablesByIdAndProcessInstanceIdIndexCommand(variableId, processInstanceId));

    }

    @Override
    public UserTaskInstanceDesc getTaskByWorkItemId(Long workItemId) {
        return (UserTaskInstanceDesc) UserTaskInstanceDescHelper.adaptTs((TaskSummaryImpl) executeQueryAgainstIndex(AuditTaskImpl.class, true, null, "", new String[]{},
                new GetTasksByWorkItemIdIndexCommand(workItemId)));

    }

    @Override
    public UserTaskInstanceDesc getTaskById(Long taskId) {
        return (UserTaskInstanceDesc) UserTaskInstanceDescHelper.adaptTs((TaskSummaryImpl) executeQueryAgainstIndex(AuditTaskImpl.class, true, null, "", new String[]{},
                new GetTasksByTaskIdIndexCommand(taskId)));
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsBusinessAdministrator(String adminId, QueryFilter filter) {
        return executeQueryAgainstIndex(AuditTaskImpl.class, filter, "", new String[]{},
                new GetTasksByBusinessAdministratorIndexCommand(adminId));

    }

    @Override
    public List<TaskSummary> getTasksAssignedAsBusinessAdministratorByStatus(String userId, List<Status> statuses, QueryFilter filter) {
        throw new UnsupportedOperationException("This method wasn't implemented against the audit logs"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, QueryFilter filter) {
        return executeQueryAgainstIndex(AuditTaskImpl.class, filter, "", new String[]{},
                new GetTasksByPotentialOwnerIndexCommand(userId));

    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, List<String> groupIds, QueryFilter filter) {
        throw new UnsupportedOperationException("This method wasn't implemented against the audit logs"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwnerByStatus(String userId, List<Status> states, QueryFilter queryContext) {
        List<String> status = new ArrayList<String>(states.size());
        for (Status s : states) {
            status.add(s.toString());
        }
        return executeQueryAgainstIndex(AuditTaskImpl.class, queryContext, "", new String[]{},
                new GetTasksByPotentialOwnerAndStatusIndexCommand(status, userId));

    }

//    protected String extractGroupIdsString(List<String> groupIds) {
//        StringBuilder sb = new StringBuilder();
//        for (String g : groupIds) {
//            sb.append(g).append(",");
//        }
//        return sb.toString();
//    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, List<String> groupIds, List<Status> status, QueryFilter filter) {
        List<String> potentialOwners = new ArrayList<String>();
        potentialOwners.add(userId);
        potentialOwners.addAll(groupIds);
        List<String> statuses = new ArrayList<String>(status.size());
        for (Status s : status) {
            statuses.add(s.toString());
        }
        return executeQueryAgainstIndex(AuditTaskImpl.class, filter, "", new String[]{},
                new GetTasksByPotentialOwnersAndStatusIndexCommand(potentialOwners, statuses));

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
        return executeQueryAgainstIndex(AuditTaskImpl.class, filter, "", new String[]{},
                new GetTasksByOwnerIndexCommand(userId));

    }

    @Override
    public List<TaskSummary> getTasksOwnedByStatus(String userId, List<Status> status, QueryFilter filter) {
        List<String> statuses = new ArrayList<String>(status.size());
        for (Status s : status) {
            statuses.add(s.toString());
        }
        return executeQueryAgainstIndex(AuditTaskImpl.class, filter, "", new String[]{},
                new GetTasksByOwnerAndStatusIndexCommand(userId, statuses));

    }

    @Override
    // This should be getTaskIdsByProcessInstanceId
    public List<Long> getTasksByProcessInstanceId(Long processInstanceId) {

        return executeQueryAgainstIndex(AuditTaskImpl.class, null, "", new String[]{},
                new GetTasksIdByProcessInstanceIdIndexCommand(processInstanceId));

    }

    @Override
    public List<TaskSummary> getTasksByStatusByProcessInstanceId(Long processInstanceId, List<Status> status, QueryFilter filter) {
        throw new UnsupportedOperationException("This method wasn't implemented against the audit logs"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    // We need to standarize the model for these methods. 
    public List<AuditTask> getAllAuditTask(String userId, QueryFilter filter) {
        return executeQueryAgainstIndex(AuditTaskImpl.class, filter, "", new String[]{},
                new GetAuditTasksByOwnerIndexCommand(userId));
//        FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(emf.createEntityManager());
//        try {
//            QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();
//
//            BooleanJunction<BooleanJunction> bool = qb.bool();
//
//            bool.must(qb.keyword().onField("actualOwner").matching(userId).createQuery());
//
//            Query query = bool.createQuery();
//
//            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
//
//            //Apply Pagination & Sort Here
//            applyQueryContext(fullTextQuery, filter);
//
//            List<AuditTask> auditTaskLog = fullTextQuery.getResultList();
//            // Just because of the interface I need to translate ProcessInstanceLog to ProcessInstanceDesc
//
//            return auditTaskLog;
//        } finally {
//            fullTextEntityManager.close();
//        }
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

//    protected Query applyDeploymentFilter(QueryBuilder qb) {
//        List<String> deploymentIdForUser = getDeploymentsForUser();
//
//        if (deploymentIdForUser != null && !deploymentIdForUser.isEmpty()) {
//            return qb.keyword().onField("externalId").matching(deploymentIdForUser).createQuery();
////    		params.put(FILTER, " log.externalId in (:deployments) ");
////    		params.put("deployments", deploymentIdForUser);
//        }
//        return null;
//    }

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

    private class BaseVariableIndexedCommand implements IndexCommand {

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            fullTextQuery.setProjection("variableId", "variableInstanceId", "oldValue", "newValue", "externalId", "processInstanceId", "date");
            fullTextQuery.setResultTransformer(new VariableDescResultTransformer());
        }

    }

    private class GetVariablesByProcessInstanceIdIndexCommand extends BaseVariableIndexedCommand {

        private Long processInstanceId;

        public GetVariablesByProcessInstanceIdIndexCommand(Long processInstanceId) {
            this.processInstanceId = processInstanceId;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (processInstanceId != null && processInstanceId != 0) {
                fullTextQuery.enableFullTextFilter("processInstanceId").setParameter("processInstanceId", processInstanceId);
            }
            super.execute(fullTextQuery);
        }

    }

    private class GetVariablesByIdAndProcessInstanceIdIndexCommand extends GetVariablesByProcessInstanceIdIndexCommand {

        private String variableId;

        public GetVariablesByIdAndProcessInstanceIdIndexCommand(String variableId, Long processInstanceId) {
            super(processInstanceId);
            this.variableId = variableId;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (variableId != null && !variableId.isEmpty()) {
                fullTextQuery.enableFullTextFilter("variableId").setParameter("variableId", variableId);
            }
            super.execute(fullTextQuery);
        }

    }

    private class BaseAuditTaskIndexedCommand implements IndexCommand {

        @Override
        public void execute(FullTextQuery fullTextQuery) {

            fullTextQuery.setProjection("taskId", "name", "status", "activationTime", "actualOwner", "description",
                    "priority", "createdBy", "createdOn", "dueDate",
                    "processInstanceId", "process", "processSessionId",
                    "deployment", "parentId", "workItemId"
//                    "potentialOwners", "businessAdministrators"
            );
            fullTextQuery.setResultTransformer(new AuditTaskResultTransformer());
        }

    }
    

    private class BaseTaskIndexedCommand implements IndexCommand {

        @Override
        public void execute(FullTextQuery fullTextQuery) {

            fullTextQuery.setProjection("taskId", "name", "description", "status", "priority", "actualOwner", "createdBy",
                    "createdOn", "activationTime", "dueDate", "process", "processInstanceId", "parentId",
                    "deployment");
            fullTextQuery.setResultTransformer(new TaskSummaryResultTransformer());
        }

    }

    private class BaseTaskIdIndexedCommand implements IndexCommand {

        @Override
        public void execute(FullTextQuery fullTextQuery) {

            fullTextQuery.setProjection("taskId");
            fullTextQuery.setResultTransformer(new TaskIdResultTransformer());
        }

    }

    private class GetTasksByWorkItemIdIndexCommand extends BaseTaskIndexedCommand {

        private Long workItemId;

        public GetTasksByWorkItemIdIndexCommand(Long workItemId) {
            this.workItemId = workItemId;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (workItemId != null && workItemId > 0) {
                fullTextQuery.enableFullTextFilter("workItemId").setParameter("workItemId", workItemId);
            }
            super.execute(fullTextQuery);
        }

    }

    private class GetTasksByTaskIdIndexCommand extends BaseTaskIndexedCommand {

        private Long taskId;

        public GetTasksByTaskIdIndexCommand(Long taskId) {
            this.taskId = taskId;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (taskId != null && taskId > 0) {
                fullTextQuery.enableFullTextFilter("taskId").setParameter("taskId", taskId);
            }
            super.execute(fullTextQuery);
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

    
    private class GetAuditTasksByOwnerIndexCommand extends BaseAuditTaskIndexedCommand {

        private String owner;

        public GetAuditTasksByOwnerIndexCommand(String owner) {
            this.owner = owner;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (owner != null && !owner.isEmpty()) {
                fullTextQuery.enableFullTextFilter("owner").setParameter("owner", owner);
            }
            super.execute(fullTextQuery);
        }

    }
    
    private class GetTasksByBusinessAdministratorIndexCommand extends BaseTaskIndexedCommand {

        private String admin;

        public GetTasksByBusinessAdministratorIndexCommand(String admin) {
            this.admin = admin;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (admin != null && !admin.isEmpty()) {
                fullTextQuery.enableFullTextFilter("businessAdministrator").setParameter("businessAdministrator", admin);
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

    private class GetTasksByPotentialOwnersAndStatusIndexCommand extends GetTasksByStatusIndexCommand {

        private List<String> owners;

        public GetTasksByPotentialOwnersAndStatusIndexCommand(List<String> owners, List<String> states) {
            super(states);
            this.owners = owners;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (owners != null && !owners.isEmpty()) {
                for (String owner : owners) {
                    fullTextQuery.enableFullTextFilter("potentialOwner").setParameter("potentialOwner", owner);
                }
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
            fullTextQuery.enableFullTextFilter("states").setParameter("states", states);
            super.execute(fullTextQuery);
        }

    }

    private class GetTasksByOwnerIndexCommand extends BaseTaskIndexedCommand {

        private String owner;

        public GetTasksByOwnerIndexCommand(String owner) {
            this.owner = owner;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            fullTextQuery.enableFullTextFilter("owner").setParameter("owner", owner);
            super.execute(fullTextQuery);
        }

    }

    private class GetTasksByOwnerAndStatusIndexCommand extends GetTasksByStatusIndexCommand {

        private String owner;

        public GetTasksByOwnerAndStatusIndexCommand(String owner, List<String> status) {
            super(status);
            this.owner = owner;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            fullTextQuery.enableFullTextFilter("owner").setParameter("owner", owner);
            super.execute(fullTextQuery);
        }

    }

    private class GetTasksIdByProcessInstanceIdIndexCommand extends BaseTaskIdIndexedCommand {

        private Long processInstanceId;

        public GetTasksIdByProcessInstanceIdIndexCommand(Long processInstanceId) {
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

    private class BaseNodeInstanceIndexCommand implements IndexCommand {

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            fullTextQuery.setProjection("id", "nodeId", "nodeName", "nodeType", "externalId", "processInstanceId", "date", "connection", "type", "workItemId"); //, ProjectionConstants.EXPLANATION, ProjectionConstants.DOCUMENT);
            fullTextQuery.setResultTransformer(new NodeInstanceDescResultTransformer());
        }

    }

    private class GetNodeInstanceByWorkItemIdIndexCommand extends BaseNodeInstanceIndexCommand {

        Long workItemId;

        public GetNodeInstanceByWorkItemIdIndexCommand(Long workItemId) {
            this.workItemId = workItemId;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (workItemId != null && workItemId > 0) {
                fullTextQuery.enableFullTextFilter("workItemId").setParameter("workItemId", workItemId);
            }
            super.execute(fullTextQuery);

        }
    }

    private class GetNodeInstanceByProcessInstanceIdIndexCommand extends BaseNodeInstanceIndexCommand {

        Long processInstanceId;

        public GetNodeInstanceByProcessInstanceIdIndexCommand(Long processInstanceId) {
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

    private class GetNodeInstanceByProcessInstanceIdAndStateIndexCommand extends GetNodeInstanceByProcessInstanceIdIndexCommand {

        private Integer state;

        public GetNodeInstanceByProcessInstanceIdAndStateIndexCommand(Long processInstanceId, Integer state) {
            super(processInstanceId);
            this.state = state;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {
            if (state != null && state > 0) {
                fullTextQuery.enableFullTextFilter("status").setParameter("status", state);
            }
            super.execute(fullTextQuery);

        }
    }

    private class GetNodeInstanceByProcessInstanceIdAndTypeIndexCommand extends GetNodeInstanceByProcessInstanceIdIndexCommand {

        private EntryType type;

        public GetNodeInstanceByProcessInstanceIdAndTypeIndexCommand(Long processInstanceId, EntryType type) {
            super(processInstanceId);
            this.type = type;
        }

        @Override
        public void execute(FullTextQuery fullTextQuery) {

            fullTextQuery.enableFullTextFilter("nodeType").setParameter("type", String.valueOf(type.getValue()));

            super.execute(fullTextQuery);

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
            Query query = null;
            BooleanJunction<BooleanJunction> paramsFragments = null;
            if (queryContext instanceof QueryFilter) {

                Map<String, Object> params = ((QueryFilter) queryContext).getParams();
                if (!params.isEmpty()) {
                    paramsFragments = qb.bool();
                    //Get the parameters from the query filter and create filter for()
                    for (String key : params.keySet()) {
                        Object o = params.get(key);
                        if (o instanceof Collection) {
                            for (Object oc : ((Collection) o)) {
                                paramsFragments.should(qb.keyword().onField(key).matching(oc).createQuery());
                            }
                        } else {
                            paramsFragments.must(qb.keyword().onField(key).matching(o).createQuery());
                        }
                    }
                }

            }

            if (keyword != null && !keyword.isEmpty() && fields.length > 0) {
                String matching;
                if (keyword.contains("%")) {
                    matching = keyword.replace("%", "*");
                } else {
                    matching = keyword;
                }
                if (paramsFragments == null) {
                    query = qb.keyword().onFields(fields).matching(matching).createQuery();
                } else {
                    paramsFragments.must(qb.keyword().onFields(fields).matching(matching).createQuery());
                }
            }

            if (query == null && paramsFragments == null) {
                query = qb.all().createQuery();
            } else if (paramsFragments != null) {
                query = paramsFragments.createQuery();
            }

            FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, entity);

            cmd.execute(fullTextQuery);
            applyQueryContext(fullTextQuery, queryContext);
            if (single) {
                return fullTextQuery.getSingleResult();
            } else {
                return fullTextQuery.getResultList();
            }
        } finally {
            fullTextEntityManager.close();
        }
    }

}
