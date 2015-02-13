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
import java.util.List;

import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.services.api.model.ProcessInstanceDesc;

/**
 *
 * @author salaboy
 */
public class ProcessInstanceDescHelper {

    public static List<ProcessInstanceDesc> adaptCollection(List<ProcessInstanceLog> pils) {
        List<ProcessInstanceDesc> processInstancesDescs = new ArrayList<ProcessInstanceDesc>(pils.size());
        for (ProcessInstanceLog pil : pils) {
            processInstancesDescs.add(adapt(pil));
        }
        return processInstancesDescs;
    }

    public static ProcessInstanceDesc adapt(ProcessInstanceLog pil) {
        return new org.jbpm.kie.services.impl.model.ProcessInstanceDesc(pil.getId(),
                pil.getProcessId(), pil.getProcessName(),
                pil.getProcessVersion(), pil.getStatus(), pil.getExternalId(),
                pil.getStart(), pil.getIdentity(), pil.getProcessInstanceDescription());
    }
}
