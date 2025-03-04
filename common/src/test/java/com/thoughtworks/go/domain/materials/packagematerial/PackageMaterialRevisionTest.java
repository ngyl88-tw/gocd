/*
 * Copyright 2021 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.domain.materials.packagematerial;

import java.util.Date;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class PackageMaterialRevisionTest {

    @Test
    public void shouldFindPackageMaterialRevisionEqual() {
        Date date = new Date();
        PackageMaterialRevision revisionOne = new PackageMaterialRevision("go-agent-12.1.0", date);
        PackageMaterialRevision revisionTwo = new PackageMaterialRevision("go-agent-12.1.0", date);
        assertThat(revisionOne.equals(revisionTwo), is(true));
    }
}
