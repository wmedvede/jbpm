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
import java.util.Date;
import java.util.List;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.TaskSummary;

/**
 *
 * @author salaboy
 */
public class TaskSummaryResultTransformer implements org.hibernate.transform.ResultTransformer{

    public TaskSummaryResultTransformer() {
    }

    @Override
    public Object transformTuple(Object[] os, String[] strings) {
        return new org.jbpm.services.task.query.TaskSummaryImpl((Long) os[0], (String) os[1], (String) os[2], Status.valueOf((String) os[3]), (Integer) os[4], (String) os[5],
                        (String) os[6], (Date) os[7], (Date) os[8], (Date) os[9], (String) os[10], (Long) os[11], (Long) os[12], (String) os[13]);
    }

    @Override
    public List transformList(List list) {
        List<TaskSummary> summaries = new ArrayList<TaskSummary>(list.size());
        for (Object o : list) {
                Object[] oa = (Object[]) o;
                summaries.add((TaskSummary)transformTuple(oa, null));
            }
        return summaries;
    }
    
}
