/*
 * Copyright 2016 ThoughtWorks, Inc.
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

package com.thoughtworks.go.agent.launcher;

import com.thoughtworks.go.agent.ServerUrlGenerator;
import com.thoughtworks.go.agent.common.ssl.GoAgentServerHttpClientBuilder;
import com.thoughtworks.go.agent.common.util.Downloader;
import com.thoughtworks.go.util.PerfTimer;
import com.thoughtworks.go.util.SslVerificationMode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.*;

public class ServerBinaryDownloader implements Downloader {

    private static final Log LOG = LogFactory.getLog(ServerBinaryDownloader.class);
    private final ServerUrlGenerator urlGenerator;
    private String md5 = null;
    private String sslPort;

    private static final String MD5_HEADER = "Content-MD5";
    @Deprecated // for backward compatibility
    private static final String SSL_PORT_HEADER = "Cruise-Server-Ssl-Port";
    private static final int HTTP_TIMEOUT_IN_MILLISECONDS = 5000;
    private GoAgentServerHttpClientBuilder httpClientBuilder;

    public ServerBinaryDownloader(ServerUrlGenerator urlGenerator, File rootCertFile, SslVerificationMode sslVerificationMode) throws Exception {
        this(new GoAgentServerHttpClientBuilder(rootCertFile, sslVerificationMode), urlGenerator);
    }

    protected ServerBinaryDownloader(GoAgentServerHttpClientBuilder httpClientBuilder, ServerUrlGenerator urlGenerator) {
        this.httpClientBuilder = httpClientBuilder;
        this.urlGenerator = urlGenerator;
    }

    public String getMd5() {
        return md5;
    }

    public String getSslPort() {
        return sslPort;
    }

    public boolean downloadIfNecessary(final DownloadableFile downloadableFile) {
        boolean updated = false;
        boolean downloaded = false;
        while (!updated) {
            try {
                fetchUpdateCheckHeaders(downloadableFile);
                if (downloadableFile.doesNotExist() || !downloadableFile.isChecksumEquals(getMd5())) {
                    PerfTimer timer = PerfTimer.start("Downloading new " + downloadableFile + " with md5 signature: " + md5);
                    downloaded = download(downloadableFile);
                    timer.stop();
                }
                updated = true;
            } catch (Exception e) {
                LOG.error("Couldn't update " + downloadableFile + ". Sleeping for 1m. Error: ", e);
                try {
                    int period = Integer.parseInt(System.getProperty("sleep.for.download", "60000"));
                    Thread.sleep(period);
                } catch (InterruptedException ie) { /* we don't care. Stupid checked exception.*/ }
            }
        }
        return downloaded;
    }

    void fetchUpdateCheckHeaders(DownloadableFile downloadableFile) throws Exception {
        String url = downloadableFile.validatedUrl(urlGenerator);
        final HttpRequestBase request = new HttpHead(url);
        request.setConfig(RequestConfig.custom().setConnectTimeout(HTTP_TIMEOUT_IN_MILLISECONDS).build());

        try (
                CloseableHttpClient httpClient = httpClientBuilder.build();
                CloseableHttpResponse response = httpClient.execute(request)
        ) {
            handleInvalidResponse(response, url);
            this.md5 = response.getFirstHeader(MD5_HEADER).getValue();
            this.sslPort = response.getFirstHeader(SSL_PORT_HEADER).getValue();
        }
    }

    protected synchronized boolean download(final DownloadableFile downloadableFile) throws Exception {
        File toDownload = downloadableFile.getLocalFile();
        LOG.info("Downloading " + toDownload);
        String url = downloadableFile.url(urlGenerator);
        final HttpRequestBase request = new HttpGet(url);
        request.setConfig(RequestConfig.custom().setConnectTimeout(HTTP_TIMEOUT_IN_MILLISECONDS).build());

        try (CloseableHttpClient httpClient = httpClientBuilder.build();
             CloseableHttpResponse response = httpClient.execute(request)) {
            LOG.info("Got server response");
            if (response.getEntity() == null) {
                LOG.error("Unable to read file from the server response");
                return false;
            }
            handleInvalidResponse(response, url);
            try (BufferedOutputStream outStream = new BufferedOutputStream(new FileOutputStream(downloadableFile.getLocalFile()))) {
                response.getEntity().writeTo(outStream);
                LOG.info("Piped the stream to " + downloadableFile);
            }
        }
        return true;
    }

    private void handleInvalidResponse(HttpResponse response, String url) throws IOException {
        StringWriter sw = new StringWriter();
        try (PrintWriter out = new PrintWriter(sw)) {
            out.print("Problem accessing server at ");
            out.println(url);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                LOG.info("Response code: " + response.getStatusLine().getStatusCode());
                out.println("Few Possible Causes: ");
                out.println("1. Your Go Server is down or not accessible.");
                out.println("2. This agent might be incompatible with your Go Server. Please fix the version mismatch between Go Server and Go Agent.");

                throw new ClientProtocolException(sw.toString());
            } else if (response.getFirstHeader(MD5_HEADER) == null || response.getFirstHeader(SSL_PORT_HEADER) == null) {
                out.print("Missing required headers '");
                out.print(MD5_HEADER);
                out.print("' and '");
                out.print(SSL_PORT_HEADER);
                out.println("' in response.");
                throw new ClientProtocolException(sw.toString());
            }
        }
    }
}
