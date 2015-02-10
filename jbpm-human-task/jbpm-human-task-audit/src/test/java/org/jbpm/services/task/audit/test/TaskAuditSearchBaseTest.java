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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.util.Version;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.query.dsl.QueryBuilder;

import org.jbpm.services.task.HumanTaskServicesBaseTest;
import org.jbpm.services.task.audit.impl.model.AuditTaskImpl;
import org.kie.internal.task.api.AuditTask;
import org.jbpm.services.task.audit.service.TaskAuditService;
import org.jbpm.services.task.utils.TaskFluent;
import org.junit.Assert;
import org.junit.Test;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.query.QueryFilter;

public abstract class TaskAuditSearchBaseTest extends HumanTaskServicesBaseTest {

    @Inject
    protected TaskAuditService taskAuditService;
    
    protected FullTextEntityManager fullTextEntityManager;
    
    @Test
    public void testSimpleSearch() throws ParseException {
      
        Task task = new TaskFluent().setName("This is my task name")
                                    .setDescription("This is my task description")
                                    .addPotentialUser("salaboy")
                                    .setAdminUser("Administrator")
                                    .getTask();
       
        
        
        taskService.addTask(task, new HashMap<String, Object>());
        long taskId = task.getId();
         
        
        List<AuditTask> allHistoryAuditTasks = taskAuditService.getAllAuditTasks(new QueryFilter(0,0));
        assertEquals(2, allHistoryAuditTasks.size());
        
        Assert.assertNotNull(fullTextEntityManager);
        QueryBuilder qb = fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(AuditTaskImpl.class).get();
        String userInput = "task name";
        // Lucene option
        QueryParser qp = new QueryParser(Version.LUCENE_36, "name", fullTextEntityManager.getSearchFactory().getAnalyzer(AuditTaskImpl.class));
        //Query luceneQuery = qp.parse(userInput);
        
        // DSL Search option
        Query query = qb.phrase().onField("name").andField("description").sentence(userInput).createQuery();
        //qb.all() -> it will match all the tasks with no filter 
        
        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(query, AuditTaskImpl.class);
        //
        fullTextQuery.setSort(org.apache.lucene.search.Sort.RELEVANCE);
        
//        FullTextQuery fullTextQuery = fullTextEntityManager.createFullTextQuery(luceneQuery, AuditTaskImpl.class);
        List resultList = fullTextQuery.getResultList();
        
        Assert.assertNotEquals(0, resultList.size());
    }
    
    
 

    

   
}