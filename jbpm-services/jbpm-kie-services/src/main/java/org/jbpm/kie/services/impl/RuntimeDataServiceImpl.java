/*
 * Copyright 2012 JBoss by Red Hat.
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

import static org.kie.internal.query.QueryParameterIdentifiers.FILTER;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jbpm.services.api.model.NodeInstanceDesc;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import org.jbpm.services.api.model.UserTaskInstanceDesc;
import org.jbpm.services.api.model.VariableDesc;
import org.jbpm.shared.services.impl.QueryManager;
import org.jbpm.shared.services.impl.commands.QueryNameCommand;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.query.QueryContext;
import org.kie.internal.query.QueryFilter;
import org.kie.internal.task.api.AuditTask;
import org.kie.internal.task.api.InternalTaskService;
import org.kie.internal.task.api.model.TaskEvent;

public class RuntimeDataServiceImpl extends AbstractRuntimeDataService {

    public RuntimeDataServiceImpl() {
        QueryManager.get().addNamedQueries("META-INF/Servicesorm.xml");
        QueryManager.get().addNamedQueries("META-INF/TaskAuditorm.xml");
        QueryManager.get().addNamedQueries("META-INF/Taskorm.xml");
    }

    protected void applyQueryContext(Map<String, Object> params, QueryContext queryContext) {
        if (queryContext != null) {
            params.put("firstResult", queryContext.getOffset());
            params.put("maxResults", queryContext.getCount());

            if (queryContext.getOrderBy() != null && !queryContext.getOrderBy().isEmpty()) {
                params.put(QueryManager.ORDER_BY_KEY, queryContext.getOrderBy());

                if (queryContext.isAscending()) {
                    params.put(QueryManager.ASCENDING_KEY, "true");
                } else {
                    params.put(QueryManager.DESCENDING_KEY, "true");
                }
            }
        }
    }

    protected void applyDeploymentFilter(Map<String, Object> params) {
        List<String> deploymentIdForUser = getDeploymentsForUser();

        if (deploymentIdForUser != null && !deploymentIdForUser.isEmpty()) {
            params.put(FILTER, " log.externalId in (:deployments) ");
            params.put("deployments", deploymentIdForUser);
        }
    }

    /*
     * start
     * process instances methods
     */
    public Collection<ProcessInstanceDesc> getProcessInstances(QueryContext queryContext) {
        Map<String, Object> params = new HashMap<String, Object>();
        applyQueryContext(params, queryContext);
        applyDeploymentFilter(params);
        List<ProcessInstanceDesc> processInstances = commandService.execute(
                new QueryNameCommand<List<ProcessInstanceDesc>>("getProcessInstances", params));

        return Collections.unmodifiableCollection(processInstances);
    }

    public Collection<ProcessInstanceDesc> getProcessInstances(List<Integer> states, String initiator, QueryContext queryContext) {

        List<ProcessInstanceDesc> processInstances = null;
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("states", states);
        applyQueryContext(params, queryContext);
        applyDeploymentFilter(params);
        if (initiator == null) {

            processInstances = commandService.execute(
                    new QueryNameCommand<List<ProcessInstanceDesc>>("getProcessInstancesByStatus", params));
        } else {

            params.put("initiator", initiator);
            processInstances = commandService.execute(
                    new QueryNameCommand<List<ProcessInstanceDesc>>("getProcessInstancesByStatusAndInitiator", params));
        }
        return Collections.unmodifiableCollection(processInstances);
    }

    public Collection<ProcessInstanceDesc> getProcessInstancesByDeploymentId(String deploymentId, List<Integer> states, QueryContext queryContext) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("externalId", deploymentId);
        params.put("states", states);
        applyQueryContext(params, queryContext);
        applyDeploymentFilter(params);
        List<ProcessInstanceDesc> processInstances = commandService.execute(
                new QueryNameCommand<List<ProcessInstanceDesc>>("getProcessInstancesByDeploymentId",
                        params));
        return Collections.unmodifiableCollection(processInstances);

    }

    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessDefinition(String processDefId, QueryContext queryContext) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("processDefId", processDefId);
        applyQueryContext(params, queryContext);
        applyDeploymentFilter(params);
        List<ProcessInstanceDesc> processInstances = commandService.execute(
                new QueryNameCommand<List<ProcessInstanceDesc>>("getProcessInstancesByProcessDefinition",
                        params));

        return Collections.unmodifiableCollection(processInstances);
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessDefinition(String processDefId, List<Integer> states, QueryContext queryContext) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("processId", processDefId);
        params.put("states", states);
        applyQueryContext(params, queryContext);
        applyDeploymentFilter(params);
        List<ProcessInstanceDesc> processInstances = commandService.execute(
                new QueryNameCommand<List<ProcessInstanceDesc>>("getProcessInstancesByProcessIdAndStatus",
                        params));

        return Collections.unmodifiableCollection(processInstances);
    }

    public ProcessInstanceDesc getProcessInstanceById(long processId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("processId", processId);
        params.put("maxResults", 1);

        List<ProcessInstanceDesc> processInstances = commandService.execute(
                new QueryNameCommand<List<ProcessInstanceDesc>>("getProcessInstanceById",
                        params));

        if (!processInstances.isEmpty()) {
            ProcessInstanceDesc desc = processInstances.iterator().next();
            List<String> statuses = new ArrayList<String>();
            statuses.add(Status.Ready.name());
            statuses.add(Status.Reserved.name());
            statuses.add(Status.InProgress.name());

            params = new HashMap<String, Object>();
            params.put("processInstanceId", desc.getId());
            params.put("statuses", statuses);
            List<UserTaskInstanceDesc> tasks = commandService.execute(
                    new QueryNameCommand<List<UserTaskInstanceDesc>>("getTaskInstancesByProcessInstanceId", params));
            ((org.jbpm.kie.services.impl.model.ProcessInstanceDesc) desc).setActiveTasks(tasks);
            return desc;
        }
        return null;
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessId(List<Integer> states, String processId, String initiator, QueryContext queryContext) {
        List<ProcessInstanceDesc> processInstances = null;
        Map<String, Object> params = new HashMap<String, Object>();

        params.put("states", states);
        params.put("processId", processId);
        applyQueryContext(params, queryContext);
        applyDeploymentFilter(params);
        if (initiator == null) {

            processInstances = commandService.execute(
                    new QueryNameCommand<List<ProcessInstanceDesc>>("getProcessInstancesByProcessIdAndStatus", params));
        } else {
            params.put("initiator", initiator);

            processInstances = commandService.execute(
                    new QueryNameCommand<List<ProcessInstanceDesc>>("getProcessInstancesByProcessIdAndStatusAndInitiator", params));
        }

        return Collections.unmodifiableCollection(processInstances);
    }

    @Override
    public Collection<ProcessInstanceDesc> getProcessInstancesByProcessName(
            List<Integer> states, String processName, String initiator, QueryContext queryContext) {
        List<ProcessInstanceDesc> processInstances = null;
        Map<String, Object> params = new HashMap<String, Object>();

        params.put("states", states);
        params.put("processName", processName);
        applyQueryContext(params, queryContext);
        applyDeploymentFilter(params);
        if (initiator == null) {

            processInstances = commandService.execute(
                    new QueryNameCommand<List<ProcessInstanceDesc>>("getProcessInstancesByProcessNameAndStatus", params));
        } else {
            params.put("initiator", initiator);

            processInstances = commandService.execute(
                    new QueryNameCommand<List<ProcessInstanceDesc>>("getProcessInstancesByProcessNameAndStatusAndInitiator", params));
        }

        return Collections.unmodifiableCollection(processInstances);
    }

    /*
     * end
     * process instances methods
     */
    /*
     * start
     * node instances methods
     */
    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceHistoryActive(long processId, QueryContext queryContext) {
        return getProcessInstanceHistory(processId, false, queryContext);
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceHistoryCompleted(long processId, QueryContext queryContext) {
        return getProcessInstanceHistory(processId, true, queryContext);
    }

    protected Collection<NodeInstanceDesc> getProcessInstanceHistory(long processId, boolean completed, QueryContext queryContext) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("processId", processId);
        applyQueryContext(params, queryContext);
        List<NodeInstanceDesc> nodeInstances = Collections.emptyList();
        if (completed) {
            nodeInstances = commandService.execute(
                    new QueryNameCommand<List<NodeInstanceDesc>>("getProcessInstanceCompletedNodes",
                            params));
        } else {
            nodeInstances = commandService.execute(
                    new QueryNameCommand<List<NodeInstanceDesc>>("getProcessInstanceActiveNodes",
                            params));
        }

        return nodeInstances;
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceFullHistory(long processId, QueryContext queryContext) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("processId", processId);
        applyQueryContext(params, queryContext);
        List<NodeInstanceDesc> nodeInstances = commandService.execute(
                new QueryNameCommand<List<NodeInstanceDesc>>("getProcessInstanceFullHistory",
                        params));

        return nodeInstances;
    }

    @Override
    public Collection<NodeInstanceDesc> getProcessInstanceFullHistoryByType(long processId, EntryType type, QueryContext queryContext) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("processId", processId);
        params.put("type", type.getValue());
        applyQueryContext(params, queryContext);
        List<NodeInstanceDesc> nodeInstances = commandService.execute(
                new QueryNameCommand<List<NodeInstanceDesc>>("getProcessInstanceFullHistoryByType",
                        params));

        return nodeInstances;
    }

    @Override
    public NodeInstanceDesc getNodeInstanceForWorkItem(Long workItemId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("workItemId", workItemId);
        params.put("maxResults", 1);
        List<NodeInstanceDesc> nodeInstances = commandService.execute(
                new QueryNameCommand<List<NodeInstanceDesc>>("getNodeInstanceForWorkItem", params));

        if (!nodeInstances.isEmpty()) {
            return nodeInstances.iterator().next();
        }
        return null;
    }

    /*
     * end
     * node instances methods
     */
    /*
     * start 
     * variable methods
     */
    public Collection<VariableDesc> getVariablesCurrentState(long processInstanceId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("processInstanceId", processInstanceId);
        List<VariableDesc> variablesState = commandService.execute(
                new QueryNameCommand<List<VariableDesc>>("getVariablesCurrentState", params));

        return variablesState;
    }

    public Collection<VariableDesc> getVariableHistory(long processInstanceId, String variableId, QueryContext queryContext) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("processInstanceId", processInstanceId);
        params.put("variableId", variableId);
        applyQueryContext(params, queryContext);
        List<VariableDesc> variablesState = commandService.execute(
                new QueryNameCommand<List<VariableDesc>>("getVariableHistory",
                        params));

        return variablesState;
    }

    /*
     * end 
     * variable methods
     */
    /*
     * start
     * task methods
     */
    @Override
    public UserTaskInstanceDesc getTaskByWorkItemId(Long workItemId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("workItemId", workItemId);
        params.put("maxResults", 1);
        List<UserTaskInstanceDesc> tasks = commandService.execute(
                new QueryNameCommand<List<UserTaskInstanceDesc>>("getTaskInstanceByWorkItemId", params));

        if (!tasks.isEmpty()) {
            return tasks.iterator().next();
        }
        return null;
    }

    @Override
    public UserTaskInstanceDesc getTaskById(Long taskId) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("taskId", taskId);
        params.put("maxResults", 1);
        List<UserTaskInstanceDesc> tasks = commandService.execute(
                new QueryNameCommand<List<UserTaskInstanceDesc>>("getTaskInstanceById", params));

        if (!tasks.isEmpty()) {
            return tasks.iterator().next();
        }
        return null;
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsBusinessAdministrator(String userId, QueryFilter filter) {

        List<Status> allActiveStatus = new ArrayList<Status>();
        allActiveStatus.add(Status.Created);
        allActiveStatus.add(Status.Ready);
        allActiveStatus.add(Status.Reserved);
        allActiveStatus.add(Status.InProgress);
        allActiveStatus.add(Status.Suspended);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("userId", userId);
        params.put("status", allActiveStatus);
        applyQueryContext(params, filter);
        applyQueryFilter(params, filter);
        return (List<TaskSummary>) commandService.execute(
                new QueryNameCommand<List<TaskSummary>>("TasksAssignedAsBusinessAdministratorByStatus", params));

    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, QueryFilter filter) {
        return ((InternalTaskService) taskService).getTasksAssignedAsPotentialOwner(userId, null, null, filter);
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, List<String> groupIds, QueryFilter filter) {
        return ((InternalTaskService) taskService).getTasksAssignedAsPotentialOwner(userId, groupIds, null, filter);
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwner(String userId, List<String> groupIds, List<Status> status, QueryFilter filter) {
        return ((InternalTaskService) taskService).getTasksAssignedAsPotentialOwner(userId, groupIds, status, filter);
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwnerByStatus(String userId, List<Status> status, QueryFilter filter) {
        return ((InternalTaskService) taskService).getTasksAssignedAsPotentialOwner(userId, null, status, filter);
    }

    @Override
    public List<TaskSummary> getTasksAssignedAsPotentialOwnerByExpirationDateOptional(
            String userId, List<Status> status, Date from, QueryFilter filter) {
        List<TaskSummary> taskSummaries = null;
        if (from != null) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("expirationDate", from);
            QueryFilter qf = new QueryFilter("(t.taskData.expirationTime = :expirationDate or t.taskData.expirationTime is null)",
                    params, "order by t.id DESC", filter.getOffset(), filter.getCount());

            taskSummaries = ((InternalTaskService) taskService).getTasksAssignedAsPotentialOwner(userId, null, status, qf);
        } else {
            QueryFilter qf = new QueryFilter(filter.getOffset(), filter.getCount());
            taskSummaries = ((InternalTaskService) taskService).getTasksAssignedAsPotentialOwner(userId, null, status, qf);
        }
        return taskSummaries;
    }

    @Override
    public List<TaskSummary> getTasksOwnedByExpirationDateOptional(String userId, List<Status> strStatuses, Date from,
            QueryFilter filter) {
        List<TaskSummary> taskSummaries = null;
        if (from != null) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("expirationDate", from);
            QueryFilter qf = new QueryFilter("(t.taskData.expirationTime = :expirationDate or t.taskData.expirationTime is null)",
                    params, "order by t.id DESC", filter.getOffset(), filter.getCount());

            taskSummaries = ((InternalTaskService) taskService).getTasksOwned(userId, null, qf);
        } else {
            QueryFilter qf = new QueryFilter(filter.getOffset(), filter.getCount());
            taskSummaries = ((InternalTaskService) taskService).getTasksOwned(userId, null, qf);
        }
        return taskSummaries;
    }

    @Override
    public List<TaskSummary> getTasksOwned(String userId, QueryFilter filter) {

        return ((InternalTaskService) taskService).getTasksOwned(userId, null, filter);
    }

    @Override
    public List<TaskSummary> getTasksOwnedByStatus(String userId, List<Status> status, QueryFilter filter) {
        return ((InternalTaskService) taskService).getTasksOwned(userId, status, filter);
    }

    @Override
    public List<Long> getTasksByProcessInstanceId(Long processInstanceId) {
        return taskService.getTasksByProcessInstanceId(processInstanceId);
    }

    @Override
    public List<TaskSummary> getTasksByStatusByProcessInstanceId(Long processInstanceId, List<Status> status, QueryFilter filter) {

        if (status == null || status.isEmpty()) {

            status = new ArrayList<Status>();
            status.add(Status.Created);
            status.add(Status.Ready);
            status.add(Status.Reserved);
            status.add(Status.InProgress);
            status.add(Status.Suspended);
        }

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("processInstanceId", processInstanceId);
        params.put("status", status);
        applyQueryContext(params, filter);
        applyQueryFilter(params, filter);
        return (List<TaskSummary>) commandService.execute(new QueryNameCommand<List<TaskSummary>>("TasksByStatusByProcessId", params));
    }
    /*
     * end
     * task methods
     */

    /*
    * start
    *  task audit queries   
     */
    @Override
    public List<AuditTask> getAllAuditTask(String userId, QueryFilter filter) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("owner", userId);
        applyQueryContext(params, filter);
        applyQueryFilter(params, filter);
        List<AuditTask> auditTasks = commandService.execute(
                new QueryNameCommand<List<AuditTask>>("getAllAuditTasksByUser", params));
        return auditTasks;
    }

    public List<TaskEvent> getTaskEvents(long taskId, QueryFilter filter) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("taskId", taskId);
        applyQueryContext(params, filter);
        applyQueryFilter(params, filter);
        List<TaskEvent> taskEvents = commandService.execute(
                new QueryNameCommand<List<TaskEvent>>("getAllTasksEvents", params));
        return taskEvents;
    }

    /*
    * end
    *  task audit queries   
     */
    @Override
    public List<TaskSummary> getTasksAssignedAsBusinessAdministratorByStatus(String userId,
            List<Status> statuses,
            QueryFilter filter) {
        return ((InternalTaskService) taskService).getTasksAssignedAsBusinessAdministratorByStatus(userId, filter.getLanguage(), statuses);
    }

    protected void applyQueryFilter(Map<String, Object> params, QueryFilter queryFilter) {
        if (queryFilter != null) {
            applyQueryContext(params, queryFilter);
            if (queryFilter.getFilterParams() != null && !queryFilter.getFilterParams().isEmpty()) {
                params.put(FILTER, queryFilter.getFilterParams());
                for (String key : queryFilter.getParams().keySet()) {
                    params.put(key, queryFilter.getParams().get(key));
                }
            }
        }
    }

}
