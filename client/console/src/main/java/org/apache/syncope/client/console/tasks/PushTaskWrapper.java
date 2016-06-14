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
package org.apache.syncope.client.console.tasks;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.syncope.client.console.panels.search.SearchClause;
import org.apache.syncope.client.console.panels.search.SearchUtils;
import org.apache.syncope.client.lib.SyncopeClient;
import org.apache.syncope.common.lib.search.AbstractFiqlSearchConditionBuilder;
import org.apache.syncope.common.lib.to.PushTaskTO;

public class PushTaskWrapper implements Serializable {

    private static final long serialVersionUID = 8058288034211558377L;

    private final PushTaskTO pushTaskTO;

    private Map<String, List<SearchClause>> filterClauses;

    public PushTaskWrapper(final PushTaskTO pushTaskTO) {
        this.pushTaskTO = pushTaskTO;
        getFilterClauses();
    }

    public final Map<String, List<SearchClause>> getFilterClauses() {
        if (this.filterClauses == null) {
            this.filterClauses = SearchUtils.getSearchClauses(this.pushTaskTO.getFilters());
        }
        return this.filterClauses;
    }

    public void setFilterClauses(final Map<String, List<SearchClause>> aDynClauses) {
        this.filterClauses = aDynClauses;
    }

    public Map<String, String> getFilters() {
        final Map<String, String> res = new HashMap<>();
        if (this.filterClauses != null && !this.filterClauses.isEmpty()) {
            for (Map.Entry<String, List<SearchClause>> entry : this.filterClauses.entrySet()) {
                if (CollectionUtils.isNotEmpty(entry.getValue())) {
                    res.put(entry.getKey(), getFIQLString(entry.getValue(),
                            SyncopeClient.getAnyObjectSearchConditionBuilder(entry.getKey())));
                }
            }
        }

        return res;
    }

    private String getFIQLString(final List<SearchClause> clauses, final AbstractFiqlSearchConditionBuilder bld) {
        return SearchUtils.buildFIQL(clauses, bld);
    }

    public PushTaskTO fillFilterConditions() {
        this.pushTaskTO.getFilters().clear();
        this.pushTaskTO.getFilters().putAll(this.getFilters());
        return this.pushTaskTO;
    }
}
