/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.jbpm.services.task.audit.test;

import java.util.Date;
import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;
import org.apache.lucene.search.Query;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.hibernate.search.query.engine.spi.FacetManager;
import org.hibernate.search.query.facet.Facet;
import org.hibernate.search.query.facet.FacetSortOrder;
import org.hibernate.search.query.facet.FacetingRequest;

import org.jbpm.services.task.HumanTaskServicesBaseTest;
import org.jbpm.services.task.audit.impl.model.AuditTaskImpl;
import org.jbpm.services.task.audit.service.TaskAuditService;
import org.jbpm.services.task.utils.TaskFluent;
import org.junit.Assert;
import org.junit.Test;
import org.kie.api.task.model.Task;
import org.kie.internal.query.QueryFilter;
import org.kie.internal.task.api.AuditTask;

public abstract class TaskAuditSearchBaseTest extends HumanTaskServicesBaseTest {

    @Inject
    protected TaskAuditService taskAuditService;

    protected FullTextEntityManager fullTextEntityManager;
    
    private Task task0 = new TaskFluent().setName("This is my task name")
                .setDescription("This is my task description keyword")
                .addPotentialUser("salaboy")
                .setAdminUser("Administrator")
                .setProcessId("ad-hoc")
                .setDeploymentId("ad-hoc")
                .setPriority(10)
                .getTask();
    
    private Task task1 = new TaskFluent().setName("This is my task name")
                .setDescription("This is my task description keyword")
                .addPotentialUser("salaboy")
                .setAdminUser("Administrator")
                .setPriority(10)
                .setProcessId("process definition 1")
                .setDeploymentId("deployment2")
                .getTask();

    private Task task2 = new TaskFluent().setName("This is my task name keyworkd")
                .setDescription("This is my task description")
                .addPotentialUser("salaboy")
                .setProcessId("process definition 1")
                .setAdminUser("Administrator")
                .setDeploymentId("deployment1")
                .setPriority(2)
                .getTask();

    private Task task3 = new TaskFluent().setName("This is my task name keyword")
                .setDescription("This is my task description keyword")
                .addPotentialUser("salaboy")
                .setAdminUser("Administrator")
                .setProcessId("process definition 2")
                .setDeploymentId("deployment2")
                .setPriority(1)
                .getTask();
    
    private Task task4 = new TaskFluent().setName("This is my task name keyword")
                .setDescription("This is my task description keyword")
                .addPotentialUser("salaboy")
                .setAdminUser("Administrator")
                .setProcessId("process definition 2")
                .setDeploymentId("deployment1")
                .setPriority(9)
                .getTask();
    
     private Task task5 = new TaskFluent().setName("This is my task name keyword")
                .setDescription("This is my task description keyword")
                .addPotentialUser("otherUser")
                .setAdminUser("Administrator")
                .setProcessId("process definition 2")
                .setDeploymentId("deployment2")
                .setPriority(1)
                .getTask();
    
    @Test
    public void testSimpleSearch()  {

        

        taskService.addTask(task1, new HashMap<String, Object>());

        taskService.addTask(task2, new HashMap<String, Object>());

        taskService.addTask(task3, new HashMap<String, Object>());

        long beforeAudit = new Date().getTime();
        List<AuditTask> allHistoryAuditTasks = taskAuditService.getAllAuditTasks(new QueryFilter(0, 0));
        long afterAudit = new Date().getTime();
        long totalAudit = afterAudit - beforeAudit;
        System.out.println("Total Audit: " + totalAudit);
        assertEquals(3, allHistoryAuditTasks.size());
        
        Assert.assertNotNull(fullTextEntityManager);
        
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();
        String userInput = "keyword";

        // DSL Search option
        //Query query = qb.phrase().onField("taskName").andField("taskDescription").sentence(userInput).createQuery(); // tried different approaches
        //Query query = qb.keyword().onFields("name", "description").matching(userInput).createQuery();// tried different approaches
        
        //Query query = qb.keyword().onFields("name", "description").matching(userInput).createQuery(); // this one works as an AND instead of OR
        
        Query query = qb.all().createQuery();

        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
        fullTextQuery.setFirstResult(0);
        fullTextQuery.setMaxResults(10);
        fullTextQuery.setSort(org.apache.lucene.search.Sort.RELEVANCE);

        int resultSize = fullTextQuery.getResultSize();
        Assert.assertEquals(3, resultSize);
        long beforeSearch = new Date().getTime();
        List resultList = fullTextQuery.getResultList();
        long afterSearch = new Date().getTime();
        long totalSearch = afterSearch - beforeSearch;
        System.out.println("Total Search: " + totalSearch);
        
        Assert.assertEquals(3, resultList.size());
    }
    
     @Test
    public void testSimpleFacetedSearch()  {
        
        taskService.addTask(task0, new HashMap<String, Object>());
        
        taskService.addTask(task1, new HashMap<String, Object>());

        taskService.addTask(task2, new HashMap<String, Object>());

        taskService.addTask(task3, new HashMap<String, Object>());
        
        taskService.addTask(task4, new HashMap<String, Object>());
        
        taskService.addTask(task5, new HashMap<String, Object>());
        
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();
        
        FacetingRequest processFacetingRequest = qb.facet()
            .name("processFacet")
            .onField("process")
            .discrete()
            .orderedBy(FacetSortOrder.COUNT_DESC)
            .includeZeroCounts(true)
            .maxFacetCount(3)
            .createFacetingRequest();
        
        FacetingRequest deploymentFacetingRequest = qb.facet()
            .name("deploymentFacet")
            .onField("deployment")
            .discrete()
            .orderedBy(FacetSortOrder.COUNT_DESC)
            .includeZeroCounts(true)
            .maxFacetCount(3)
            .createFacetingRequest();
        
        Query query = qb.all().createQuery();

        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
        
        FacetManager facetManager = fullTextQuery.getFacetManager();
        facetManager.enableFaceting(processFacetingRequest);
        facetManager.enableFaceting(deploymentFacetingRequest);
        
        List resultList = fullTextQuery.getResultList();
        Assert.assertEquals(6, resultList.size());
        
        List<Facet> processDefFacets = facetManager.getFacets("processFacet");
        
        List<Facet> deploymentFacets = facetManager.getFacets("deploymentFacet");
        
        
        Assert.assertNotNull(processDefFacets);
        Assert.assertEquals(3, processDefFacets.size());
        Assert.assertTrue(processDefFacets.get(0).getFacetingName().equals("processFacet"));
        Assert.assertTrue(processDefFacets.get(0).getValue().equals("process definition 2"));
        Assert.assertTrue(processDefFacets.get(0).getCount() == 3);
        Assert.assertTrue(processDefFacets.get(1).getFacetingName().equals("processFacet"));
        Assert.assertTrue(processDefFacets.get(1).getValue().equals("process definition 1"));
        Assert.assertTrue(processDefFacets.get(1).getCount() == 2);
        Assert.assertTrue(processDefFacets.get(2).getFacetingName().equals("processFacet"));
        Assert.assertTrue(processDefFacets.get(2).getValue().equals("ad-hoc"));
        Assert.assertTrue(processDefFacets.get(2).getCount() == 1);
        
        Assert.assertNotNull(deploymentFacets);
        Assert.assertEquals(3, deploymentFacets.size());
        Assert.assertTrue(deploymentFacets.get(0).getFacetingName().equals("deploymentFacet"));
        Assert.assertTrue(deploymentFacets.get(0).getValue().equals("deployment2"));
        Assert.assertTrue(deploymentFacets.get(0).getCount() == 3);
        Assert.assertTrue(deploymentFacets.get(1).getFacetingName().equals("deploymentFacet"));
        Assert.assertTrue(deploymentFacets.get(1).getValue().equals("deployment1"));
        Assert.assertTrue(deploymentFacets.get(1).getCount() == 2);
        Assert.assertTrue(deploymentFacets.get(2).getFacetingName().equals("deploymentFacet"));
        Assert.assertTrue(deploymentFacets.get(2).getValue().equals("ad-hoc"));
        Assert.assertTrue(deploymentFacets.get(2).getCount() == 1);
        
    }
    
    @Test
    public void moreComplexFilters(){
        taskService.addTask(task0, new HashMap<String, Object>());
        
        taskService.addTask(task1, new HashMap<String, Object>());

        taskService.addTask(task2, new HashMap<String, Object>());

        taskService.addTask(task3, new HashMap<String, Object>());
        
        taskService.addTask(task4, new HashMap<String, Object>());
        
        taskService.addTask(task5, new HashMap<String, Object>());
        
        
    
    }
    
    

}
