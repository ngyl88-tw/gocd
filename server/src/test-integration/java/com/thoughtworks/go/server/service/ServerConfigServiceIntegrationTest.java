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
package com.thoughtworks.go.server.service;

import com.thoughtworks.go.config.*;
import com.thoughtworks.go.config.exceptions.GoConfigInvalidException;
import com.thoughtworks.go.domain.SecureSiteUrl;
import com.thoughtworks.go.domain.SiteUrl;
import com.thoughtworks.go.util.GoConfigFileHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {
        "classpath:/applicationContext-global.xml",
        "classpath:/applicationContext-dataLocalAccess.xml",
        "classpath:/testPropertyConfigurer.xml",
        "classpath:/spring-all-servlet.xml",
})
public class ServerConfigServiceIntegrationTest {

    @Autowired
    GoConfigService goConfigService;
    @Autowired
    ServerConfigService serverConfigService;
    @Autowired
    UserService userService;
    @Autowired
    GoConfigDao goConfigDao;

    private GoConfigFileHelper configHelper = new GoConfigFileHelper();

    @BeforeEach
    public void setup() throws Exception {
        configHelper.usingCruiseConfigDao(goConfigDao);
        configHelper.onSetUp();

        goConfigService.forceNotifyListeners();
        goConfigService.security().securityAuthConfigs().add(new SecurityAuthConfig("file", "cd.go.authentication.passwordfile"));
    }

    @AfterEach
    public void tearDown() throws Exception {
        configHelper.onTearDown();
    }

    @Test
    public void shouldSiteUrlForGivenUrl() throws URISyntaxException {
        configHelper.setBaseUrls(new SiteUrl("http://foo.com"), new SecureSiteUrl("https://bar.com"));
        assertThat(serverConfigService.siteUrlFor("http://test.host/foo/bar", true), is("https://bar.com/foo/bar"));
        assertThat(serverConfigService.siteUrlFor("http://test.host/foo/bar", false), is("http://foo.com/foo/bar"));
    }

    @Test
    public void shouldReturnTheSameURLWhenNothingIsConfigured() throws URISyntaxException {
        assertThat(serverConfigService.siteUrlFor("http://test.host/foo/bar", true), is("http://test.host/foo/bar"));
        assertThat(serverConfigService.siteUrlFor("http://test.host/foo/bar", false), is("http://test.host/foo/bar"));
    }

    @Test
    public void shouldUseTheSiteUrlWhenSecureSiteUrlIsNotPresentAndOnlyIfSiteUrlIsHttps() throws URISyntaxException {
        configHelper.setBaseUrls(new SiteUrl("https://foo.com"), new SecureSiteUrl());
        assertThat(serverConfigService.siteUrlFor("http://test.host/foo/bar", true), is("https://foo.com/foo/bar"));
        assertThat(serverConfigService.siteUrlFor("http://test.host/foo/bar", false), is("https://foo.com/foo/bar"));
    }

    @Test
    public void shouldUseTheSecureSiteUrlInspiteOfCallerNotForcingSsl_whenAlreadyUsingHTTPS() throws URISyntaxException {
        configHelper.setBaseUrls(new SiteUrl("http://foo.com:80"), new SecureSiteUrl("https://bar.com:443"));
        assertThat(serverConfigService.siteUrlFor("https://test.host:1000/foo/bar", false), is("https://bar.com:443/foo/bar"));
        assertThat(serverConfigService.siteUrlFor("http://test.host/foo/bar", false), is("http://foo.com:80/foo/bar"));
    }

    @Test
    public void shouldSetDefaultJobTimeout() {
        serverConfigService.createOrUpdateDefaultJobTimeout("10");

        assertThat(serverConfigService.getDefaultJobTimeout(), is("10"));
    }

    @Test
    public void shouldUpdateDefaultJobTimeout() {
        serverConfigService.createOrUpdateDefaultJobTimeout("10");

        assertThat(serverConfigService.getDefaultJobTimeout(), is("10"));

        serverConfigService.createOrUpdateDefaultJobTimeout("5");

        assertThat(serverConfigService.getDefaultJobTimeout(), is("5"));
    }

    @Test
    public void shouldUpdateArtifactConfigWithoutPurgeSettingsIfValid() {
        ArtifactConfig artifactConfig = new ArtifactConfig();
        artifactConfig.setArtifactsDir(new ArtifactDirectory("test"));

        assertThat(goConfigService.serverConfig().artifactsDir(), is("artifactsDir"));

        serverConfigService.updateArtifactConfig(artifactConfig);

        assertThat(goConfigService.serverConfig().artifactsDir(), is("test"));
    }

    @Test
    public void shouldUpdateArtifactConfigWithPurgeSettingsIfValid() {
        ArtifactConfig artifactConfig = new ArtifactConfig();
        artifactConfig.setArtifactsDir(new ArtifactDirectory("test"));
        PurgeSettings purgeSettings = new PurgeSettings();
        purgeSettings.setPurgeStart(new PurgeStart(10.0));
        purgeSettings.setPurgeUpto(new PurgeUpto(20.0));
        artifactConfig.setPurgeSettings(purgeSettings);

        assertThat(goConfigService.serverConfig().artifactsDir(), is("artifactsDir"));
        assertNull(goConfigService.serverConfig().getPurgeStart());
        assertNull(goConfigService.serverConfig().getPurgeUpto());

        serverConfigService.updateArtifactConfig(artifactConfig);

        assertThat(goConfigService.serverConfig().artifactsDir(), is("test"));
        assertThat(goConfigService.serverConfig().getPurgeStart(), is(10.0));
        assertThat(goConfigService.serverConfig().getPurgeUpto(), is(20.0));
    }

    @Test
    public void shouldNotUpdateArtifactConfigIfInvalid() {
        ArtifactConfig artifactConfig = new ArtifactConfig();

        assertThat(goConfigService.serverConfig().artifactsDir(), is("artifactsDir"));

        assertThatThrownBy(() -> serverConfigService.updateArtifactConfig(artifactConfig))
                .isInstanceOf(GoConfigInvalidException.class);
    }
}
