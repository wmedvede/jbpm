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
import org.jbpm.process.audit.VariableInstanceLog;
import org.jbpm.services.api.model.VariableDesc;

/**
 *
 * @author salaboy
 */
public class VariableDescHelper {

    public static VariableDesc adapt(VariableInstanceLog vil) {
        return new org.jbpm.kie.services.impl.model.VariableStateDesc(vil.getVariableId(), vil.getVariableInstanceId(),
                vil.getOldValue(), vil.getValue(), vil.getExternalId(), vil.getProcessInstanceId(), vil.getDate());
    }

    public static List<VariableDesc> adaptCollection(List<VariableInstanceLog> vils) {
        List<VariableDesc> vds = new ArrayList<VariableDesc>();
        for (VariableInstanceLog vil : vils) {
            vds.add(adapt(vil));
        }
        return vds;
    }

}
