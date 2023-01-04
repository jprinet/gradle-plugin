package hudson.plugins.gradle.enriched;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.util.Secret;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;

public class ScanDetailServiceDefaultImpl implements ScanDetailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanDetailServiceDefaultImpl.class);

    private final static ObjectMapper MAPPER = new ObjectMapper();

    private final Secret buildScanAccessToken;
    private final URI buildScanServer;

    private HttpClientProvider httpClientProvider;
    private EnrichedSummaryConfig enrichedSummaryConfig;

    public ScanDetailServiceDefaultImpl(Secret buildScanAccessToken, URI buildScanServer) {
        this.buildScanAccessToken = buildScanAccessToken;
        this.buildScanServer = buildScanServer;
        this.httpClientProvider = new HttpClientProviderDefaultImpl();
        this.enrichedSummaryConfig = new EnrichedSummaryConfigDefaultImpl();
    }

    public void setHttpClientProvider(HttpClientProvider httpClientProvider) {
        this.httpClientProvider = httpClientProvider;
    }

    public void setEnrichedSummaryConfig(EnrichedSummaryConfig enrichedSummaryConfig) {
        this.enrichedSummaryConfig = enrichedSummaryConfig;
    }

    @Override
    public ScanDetail getScanDetail(String buildScanUrl) {
        if (enrichedSummaryConfig.isEnabled()) {
            return doGetScanDetail(buildScanUrl);
        }

        return null;
    }

    private ScanDetail doGetScanDetail(String buildScanUrl) {
        int scanPathIndex = buildScanUrl.lastIndexOf("/s/");
        if (scanPathIndex != -1) {
            String scanId = buildScanUrl.substring(scanPathIndex + 3);

            URI baseApiUrl = buildScanServer != null ? buildScanServer
                    : URI.create(buildScanUrl).resolve("/");

            try (CloseableHttpClient httpclient = httpClientProvider.buildHttpClient()) {
                HttpGet httpGetApiBuilds = buildGetRequest(baseApiUrl.resolve("/api/builds/").resolve(scanId).toASCIIString());

                String buildToolType = "";
                String buildToolVersion = null;
                try (CloseableHttpResponse responseApiBuilds = httpclient.execute(httpGetApiBuilds)) {
                    if (responseApiBuilds.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                        HttpEntity httpEntity = responseApiBuilds.getEntity();
                        if (httpEntity != null) {
                            String retSrc = EntityUtils.toString(httpEntity);
                            JsonNode result = MAPPER.readTree(retSrc);
                            buildToolType = result.get("buildToolType").asText();
                            buildToolVersion = result.get("buildToolVersion").asText();
                            EntityUtils.consume(httpEntity);
                        }
                    } else {
                        LOGGER.info(String.format("Unable to fetch build scan data [%s]", responseApiBuilds.getStatusLine().getStatusCode()));
                    }
                }

                ScanDetail scanDetail = null;
                try {
                    BuildToolType buildToolTypeAsEnum = Enum.valueOf(BuildToolType.class, buildToolType);
                    switch (buildToolTypeAsEnum) {
                        case gradle:
                            HttpGet httpGetGradleAttributes = buildGetRequest(baseApiUrl + "/api/builds/" + scanId + "/gradle-attributes");

                            try (CloseableHttpResponse responseApiBuilds = httpclient.execute(httpGetGradleAttributes)) {
                                if (responseApiBuilds.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                                    HttpEntity httpEntity = responseApiBuilds.getEntity();
                                    if (httpEntity != null) {
                                        String retSrc = EntityUtils.toString(httpEntity);
                                        JsonNode result = MAPPER.readTree(retSrc);
                                        scanDetail = new ScanDetail.ScanDetailBuilder()
                                                .withProjectName(result.get("rootProjectName").asText())
                                                .withBuildToolType(buildToolType)
                                                .withBuildToolVersion(buildToolVersion)
                                                .withRequestedTasks(joinStringList(result.get("requestedTasks").elements()))
                                                .withHasFailed(result.get("hasFailed").asText())
                                                .withUrl(buildScanUrl)
                                                .build();
                                        EntityUtils.consume(httpEntity);
                                    }
                                } else {
                                    LOGGER.info(String.format("Unable to fetch build scan data [%s]", responseApiBuilds.getStatusLine().getStatusCode()));
                                }
                            }
                            break;
                        case maven:
                            HttpGet httpGetMavenAttributes = buildGetRequest(baseApiUrl + "/api/builds/" + scanId + "/maven-attributes");

                            try (CloseableHttpResponse responseApiBuilds = httpclient.execute(httpGetMavenAttributes)) {
                                if (responseApiBuilds.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                                    HttpEntity httpEntity = responseApiBuilds.getEntity();
                                    if (httpEntity != null) {
                                        String retSrc = EntityUtils.toString(httpEntity);
                                        JsonNode result = MAPPER.readTree(retSrc);
                                        scanDetail = new ScanDetail.ScanDetailBuilder()
                                                .withProjectName(result.get("topLevelProjectName").asText())
                                                .withBuildToolType(buildToolType)
                                                .withBuildToolVersion(buildToolVersion)
                                                .withRequestedTasks(joinStringList(result.get("requestedGoals").elements()))
                                                .withHasFailed(result.get("hasFailed").asText())
                                                .withUrl(buildScanUrl)
                                                .build();
                                        EntityUtils.consume(httpEntity);
                                    }
                                } else {
                                    LOGGER.info(String.format("Unable to fetch build scan data [%s]", responseApiBuilds.getStatusLine().getStatusCode()));
                                }
                            }
                            break;
                    }
                } catch (IllegalArgumentException ignored) {
                }

                return scanDetail;
            } catch (IOException e) {
                LOGGER.info(String.format("Error fetching data [%s]", e.getMessage()));
            }
        }
        return null;
    }

    private HttpGet buildGetRequest(String url) {
        HttpGet httpGet = new HttpGet(url);
        addBearerAuth(httpGet);
        return httpGet;
    }

    private void addBearerAuth(HttpGet httpGetApiBuilds) {
        if (buildScanAccessToken != null) {
            httpGetApiBuilds.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + buildScanAccessToken.getPlainText());
        }
    }

    private String joinStringList(Iterator<JsonNode> requestedTasks) {
        StringBuilder sb = new StringBuilder();
        while (requestedTasks.hasNext()) {
            sb.append(StringUtils.remove(requestedTasks.next().asText(), "\""));
            sb.append(", ");
        }

        String result = sb.toString();
        return StringUtils.removeEnd(result, ", ");
    }

    private enum BuildToolType {
        maven, gradle
    }
}