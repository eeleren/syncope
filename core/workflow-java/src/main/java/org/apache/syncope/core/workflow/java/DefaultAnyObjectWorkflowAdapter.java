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

import org.apache.syncope.common.lib.patch.AnyObjectPatch;
import org.apache.syncope.common.lib.to.AnyObjectTO;
import org.apache.syncope.core.provisioning.api.PropagationByResource;
import org.apache.syncope.common.lib.types.ResourceOperation;
import org.apache.syncope.core.persistence.api.entity.anyobject.AnyObject;
import org.apache.syncope.core.provisioning.api.WorkflowResult;
import org.apache.syncope.core.provisioning.api.event.AnyLifecycleEvent;
import org.apache.syncope.core.spring.security.AuthContextUtils;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Simple implementation basically not involving any workflow engine.
 */
public class DefaultAnyObjectWorkflowAdapter extends AbstractAnyObjectWorkflowAdapter {

    @Autowired
    protected ApplicationEventPublisher publisher;

    @Override
    protected WorkflowResult<String> doCreate(final AnyObjectTO anyObjectTO) {
        AnyObject anyObject = entityFactory.newEntity(AnyObject.class);
        dataBinder.create(anyObject, anyObjectTO);
        anyObject = anyObjectDAO.save(anyObject);

        publisher.publishEvent(
                new AnyLifecycleEvent<>(this, SyncDeltaType.CREATE, anyObject, AuthContextUtils.getDomain()));

        PropagationByResource<String> propByRes = new PropagationByResource<>();
        propByRes.set(ResourceOperation.CREATE, anyObjectDAO.findAllResourceKeys(anyObject.getKey()));

        return new WorkflowResult<>(anyObject.getKey(), propByRes, "create");
    }

    @Override
    protected WorkflowResult<AnyObjectPatch> doUpdate(final AnyObject anyObject, final AnyObjectPatch anyObjectPatch) {
        PropagationByResource<String> propByRes = dataBinder.update(anyObject, anyObjectPatch);

        publisher.publishEvent(new AnyLifecycleEvent<>(
                this, SyncDeltaType.UPDATE, anyObjectDAO.find(anyObject.getKey()), AuthContextUtils.getDomain()));

        return new WorkflowResult<>(anyObjectPatch, propByRes, "update");
    }

    @Override
    protected void doDelete(final AnyObject anyObject) {
        anyObjectDAO.delete(anyObject);

        publisher.publishEvent(
                new AnyLifecycleEvent<>(this, SyncDeltaType.DELETE, anyObject, AuthContextUtils.getDomain()));
    }
}
