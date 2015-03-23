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
import org.jbpm.services.task.audit.impl.model.AuditTaskImpl;

/**
 *
 * @author salaboy
 */
public class AuditTaskResultTransformer implements org.hibernate.transform.ResultTransformer{

    public AuditTaskResultTransformer() {
    }

    @Override
    public Object transformTuple(Object[] os, String[] strings) {
        return new AuditTaskImpl((Long) os[0], (String) os[1], (String) os[2], (Date) os[3], (String) os[4] , (String) os[5],(Integer) os[6], 
                        (String) os[7], (os[8] != null)?(Date)os[8]:null, (os[9] != null)?(Date)os[9]:null,   (Long) os[10], (String) os[11], (os[12] != null)?(Long)os[12]:null, (String) os[13], 
                        (Long) os[14], (Long) os[15], null, null);
                //(Set<String>)os[16], (Set<String>)os[17]);
    }

    @Override
    public List transformList(List list) {
        List<AuditTaskImpl> summaries = new ArrayList<AuditTaskImpl>(list.size());
        for (Object o : list) {
                Object[] oa = (Object[]) o;
                summaries.add((AuditTaskImpl)transformTuple(oa, null));
            }
        return summaries;
    }
    
}
