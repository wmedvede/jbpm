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
import org.jbpm.kie.services.impl.model.NodeInstanceDesc;

/**
 *
 * @author salaboy
 */
public class NodeInstanceDescResultTransformer implements org.hibernate.transform.ResultTransformer{

    public NodeInstanceDescResultTransformer() {
    }

    @Override
    public Object transformTuple(Object[] os, String[] strings) {
        return new NodeInstanceDesc(((Long) os[0]).toString(), (String) os[1], (String) os[2], (String) os[3], (String) os[4], (Long) os[5],
                        (Date) os[6], (String) os[7],(Integer) os[8], (Long)os[9]);
    }

    @Override
    public List transformList(List list) {
        List<NodeInstanceDesc> summaries = new ArrayList<NodeInstanceDesc>(list.size());
        for (Object o : list) {
                Object[] oa = (Object[]) o;
                summaries.add((NodeInstanceDesc)transformTuple(oa, null));
            }
        return summaries;
    }
    
}
