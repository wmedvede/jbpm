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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.jbpm.kie.services.impl.model.ProcessAssetDesc;
import org.jbpm.services.api.DeploymentEvent;
import org.jbpm.services.api.DeploymentEventListener;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.model.DeployedAsset;
import org.jbpm.services.api.model.ProcessDefinition;
import org.jbpm.shared.services.impl.TransactionalCommandService;
import org.kie.api.task.TaskService;
import org.kie.internal.identity.IdentityProvider;
import org.kie.internal.query.QueryContext;

/**
 *
 * @author salaboy
 */
public abstract class AbstractRuntimeDataService implements RuntimeDataService, DeploymentEventListener {

    private static final int MAX_CACHE_ENTRIES = Integer.parseInt(System.getProperty("org.jbpm.service.cache.size", "100"));

    protected Set<ProcessDefinition> availableProcesses = new HashSet<ProcessDefinition>();
    protected Map<String, List<String>> deploymentsRoles = new HashMap<String, List<String>>();

    protected Map<String, List<String>> userDeploymentIdsCache = new LinkedHashMap<String, List<String>>() {
        private static final long serialVersionUID = -2324394641773215253L;

        protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    protected TransactionalCommandService commandService;

    protected IdentityProvider identityProvider;

    protected TaskService taskService;

    public void setCommandService(TransactionalCommandService commandService) {
        this.commandService = commandService;
    }

    public void setIdentityProvider(IdentityProvider identityProvider) {
        this.identityProvider = identityProvider;
    }

    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
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
    /*
     * end
     * predicates for collection filtering
     */

}
