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
package com.thoughtworks.go.apiv1.internalmaterialtest

import com.thoughtworks.go.api.SecurityTestTrait
import com.thoughtworks.go.api.spring.ApiAuthenticationHelper
import com.thoughtworks.go.config.CaseInsensitiveString
import com.thoughtworks.go.config.PipelineConfig
import com.thoughtworks.go.config.materials.PasswordDeserializer
import com.thoughtworks.go.config.materials.git.GitMaterial
import com.thoughtworks.go.domain.materials.MaterialConfig
import com.thoughtworks.go.domain.materials.ValidationBean
import com.thoughtworks.go.domain.materials.git.GitCommand
import com.thoughtworks.go.server.service.MaterialConfigConverter
import com.thoughtworks.go.server.service.SecretParamResolver
import com.thoughtworks.go.spark.ControllerTrait
import com.thoughtworks.go.spark.GroupAdminUserSecurity
import com.thoughtworks.go.spark.SecurityServiceTrait
import com.thoughtworks.go.util.SystemEnvironment
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.mockito.ArgumentMatchers.*
import static org.mockito.Mockito.*

@MockitoSettings(strictness = Strictness.LENIENT)
class InternalMaterialTestControllerV1Test implements SecurityServiceTrait, ControllerTrait<InternalMaterialTestControllerV1> {

  @Mock
  PasswordDeserializer passwordDeserializer

  @Mock
  MaterialConfigConverter materialConfigConverter

  @Mock
  SystemEnvironment systemEnvironment

  @Mock
  GitCommand gitCommand
  @Mock
  SecretParamResolver secretParamResolver


  @Override
  InternalMaterialTestControllerV1 createControllerInstance() {
    new InternalMaterialTestControllerV1(new ApiAuthenticationHelper(securityService, goConfigService), goConfigService, passwordDeserializer, materialConfigConverter, systemEnvironment, secretParamResolver)
  }

  @Nested
  class TestConnection {

    @Nested
    class Security implements SecurityTestTrait, GroupAdminUserSecurity {

      @Override
      String getControllerMethodUnderTest() {
        return "testConnection"
      }

      @Override
      void makeHttpCall() {
        postWithApiHeader(controller.controllerBasePath(), "{}")
      }
    }

    @Nested
    class AsAdminUser {
      @BeforeEach
      void setUp() {
        loginAsAdmin()
      }

      @Test
      void 'should render error if material type is invalid'() {
        postWithApiHeader(controller.controllerBasePath(), ["type": "some-random-type"])
        assertThatResponse()
          .isUnprocessableEntity()
          .hasJsonMessage("Your request could not be processed. Invalid material type 'some-random-type'. It has to be one of [git, hg, svn, p4, tfs, dependency, package, plugin].")
      }

      @Test
      void 'should render error if material type does not support check connection functionality'() {
        postWithApiHeader(controller.controllerBasePath(), ["type": "dependency"])
        assertThatResponse()
          .isUnprocessableEntity()
          .hasJsonMessage("Your request could not be processed. The material of type 'dependency' does not support connection testing.")
      }

      @Test
      void 'should render error if material is not valid'() {
        postWithApiHeader(controller.controllerBasePath(), [
          "type"      : "git",
          "attributes": [
            "url"               : "",
            "encrypted_password": "encrypted-password",
            "password"          : "password"
          ]
        ])

        def expectedJSON = [
          "message": "There was an error with the material configuration.\n- url: URL cannot be blank",
          "data"   : [
            "errors"    : [
              "url": ["URL cannot be blank"]
            ],
            "type"      : "git",
            "attributes": [
              "url"             : "",
              "destination"     : null,
              "filter"          : null,
              "invert_filter"   : false,
              "name"            : null,
              "auto_update"     : true,
              "branch"          : "master",
              "submodule_folder": null,
              "shallow_clone"   : false
            ]
          ]
        ]

        assertThatResponse()
          .isUnprocessableEntity()
          .hasJsonMessage("There was an error with the material configuration.\\n- url: URL cannot be blank")
          .hasJsonBody(expectedJSON)
      }

      @Test
      void 'should render error if cannot connect to material'() {
        def material = mock(GitMaterial.class)
        def validationBean = ValidationBean.notValid("some-error")
        when(material.checkConnection(any())).thenReturn(validationBean)
        when(materialConfigConverter.toMaterial(any(MaterialConfig.class))).thenReturn(material)

        postWithApiHeader(controller.controllerBasePath(), [
          "type"      : "git",
          "attributes": [
            "url": "some-url"
          ]
        ])

        assertThatResponse()
          .isUnprocessableEntity()
          .hasJsonMessage("some-error")
      }

      @Test
      void 'should render success response if can connect to material'() {
        def material = mock(GitMaterial.class)
        def validationBean = ValidationBean.valid()
        when(material.checkConnection(any())).thenReturn(validationBean)
        when(materialConfigConverter.toMaterial(any(MaterialConfig.class))).thenReturn(material)

        postWithApiHeader(controller.controllerBasePath(), [
          "type"      : "git",
          "attributes": [
            "url": "some-url"
          ]
        ])

        assertThatResponse()
          .isOk()
          .hasJsonMessage("Connection OK.")
      }

      @Test
      void 'should resolve secrets before connecting to scm material'() {
        def material = mock(GitMaterial.class)
        def validationBean = ValidationBean.valid()
        when(material.checkConnection(any())).thenReturn(validationBean)
        when(materialConfigConverter.toMaterial(any(MaterialConfig.class))).thenReturn(material)

        postWithApiHeader(controller.controllerBasePath(), [
          "type"      : "git",
          "attributes": [
            "url": "some-url"
          ]
        ])

        def mockOrders = inOrder(secretParamResolver, material)

        mockOrders.verify(secretParamResolver).resolve(eq(material), anyString())
        mockOrders.verify(material).checkConnection(any())
        assertThatResponse()
          .isOk()
          .hasJsonMessage("Connection OK.")
      }

      @Test
      void 'should resolve params when pipeline_name is provided'() {
        def material = mock(GitMaterial.class)
        def validationBean = ValidationBean.valid()
        when(material.checkConnection(any())).thenReturn(validationBean)
        when(materialConfigConverter.toMaterial(any(MaterialConfig.class))).thenReturn(material)
        when(goConfigService.pipelineConfigNamed(any(CaseInsensitiveString.class))).thenReturn(new PipelineConfig())

        postWithApiHeader(controller.controllerBasePath(), [
          "pipeline_name": "some-pipeline-name",
          "type"         : "git",
          "attributes"   : [
            "url": "http://some-url/val##"
          ]
        ])

        verify(goConfigService).pipelineConfigNamed(any(CaseInsensitiveString.class))

        def captor = ArgumentCaptor.forClass(MaterialConfig.class)
        verify(materialConfigConverter).toMaterial(captor.capture())
        assertEquals(captor.value.getUriForDisplay(), "http://some-url/val#")

        assertThatResponse()
          .isOk()
      }

      @Test
      void 'should resolve params even when pipeline_name is not provided'() {
        def material = mock(GitMaterial.class)
        def validationBean = ValidationBean.valid()
        when(material.checkConnection(any())).thenReturn(validationBean)
        when(materialConfigConverter.toMaterial(any(MaterialConfig.class))).thenReturn(material)

        postWithApiHeader(controller.controllerBasePath(), [
          "type"      : "git",
          "attributes": [
            "url": "http://some-url/val##"
          ]
        ])

        verify(goConfigService, never()).pipelineConfigNamed(any(CaseInsensitiveString.class))

        def captor = ArgumentCaptor.forClass(MaterialConfig.class)
        verify(materialConfigConverter).toMaterial(captor.capture())
        assertEquals(captor.value.getUriForDisplay(), "http://some-url/val#")

        assertThatResponse()
          .isOk()
      }
    }
  }
}
