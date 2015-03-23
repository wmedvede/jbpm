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
package org.jbpm.services.task.audit.impl.filters;

import java.util.List;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.TermQuery;
import org.hibernate.search.annotations.Factory;
import org.hibernate.search.annotations.Key;
import org.hibernate.search.filter.FilterKey;
import org.hibernate.search.filter.StandardFilterKey;

/**
 *
 * @author salaboy
 * This is a Task States filter
 */
public class TaskStatesFilterFactory {

    private List<String> states;

    public void setStates(List<String> states) {
        this.states = states;
    }

    @Key
    public FilterKey getKey() {
        StandardFilterKey key = new StandardFilterKey();
        key.addParameter(states);
        return key;
    }

    @Factory
    public Filter getFilter() {
        //some additional steps to cache the filter results per IndexReader
        Query query;
        if(states.size() == 1){
           query = new TermQuery(new Term("status", states.get(0))); 
        }else{
            query = new BooleanQuery();
            for(String state : states){
                ((BooleanQuery)query).add(new TermQuery(new Term("status", state)),Occur.SHOULD);
            }
        }
        return new CachingWrapperFilter(new QueryWrapperFilter(query));
    }
}
