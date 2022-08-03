/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.workflow.java;

import org.apache.syncope.common.lib.patch.GroupPatch;
import org.apache.syncope.common.lib.to.GroupTO;
import org.apache.syncope.core.provisioning.api.PropagationByResource;
import org.apache.syncope.common.lib.types.ResourceOperation;
import org.apache.syncope.core.persistence.api.entity.group.Group;
import org.apache.syncope.core.provisioning.api.WorkflowResult;
import org.apache.syncope.core.provisioning.api.event.AnyLifecycleEvent;
import org.apache.syncope.core.spring.security.AuthContextUtils;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Simple implementation basically not involving any workflow engine.
 */
public class DefaultGroupWorkflowAdapter extends AbstractGroupWorkflowAdapter {

    @Autowired
    protected ApplicationEventPublisher publisher;

    @Override
    protected WorkflowResult<String> doCreate(final GroupTO groupTO) {
        Group group = entityFactory.newEntity(Group.class);
        dataBinder.create(group, groupTO);
        group = groupDAO.saveAndRefreshDynMemberships(group);

        publisher.publishEvent(
                new AnyLifecycleEvent<>(this, SyncDeltaType.CREATE, group, AuthContextUtils.getDomain()));

        PropagationByResource<String> propByRes = new PropagationByResource<>();
        propByRes.set(ResourceOperation.CREATE, groupDAO.findAllResourceKeys(group.getKey()));

        return new WorkflowResult<>(group.getKey(), propByRes, "create");
    }

    @Override
    protected WorkflowResult<GroupPatch> doUpdate(final Group group, final GroupPatch groupPatch) {
        PropagationByResource<String> propByRes = dataBinder.update(group, groupPatch);
     
        publisher.publishEvent(new AnyLifecycleEvent<>(
                this, SyncDeltaType.UPDATE, groupDAO.find(group.getKey()), AuthContextUtils.getDomain()));
        
        return new WorkflowResult<>(groupPatch, propByRes, "update");
    }

    @Override
    protected void doDelete(final Group group) {
        groupDAO.delete(group);

        publisher.publishEvent(
                new AnyLifecycleEvent<>(this, SyncDeltaType.DELETE, group, AuthContextUtils.getDomain()));
    }
}
