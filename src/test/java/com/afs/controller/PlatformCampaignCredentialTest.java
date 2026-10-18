package com.afs.controller;

import com.afs.agent.ChannelCredentialController;
import com.afs.agent.ChannelCredentialController.PlatformCredential;
import com.afs.dao.AdAccountInfoMapper;
import com.afs.dao.AdResourceMapper;
import com.afs.dao.model.AdAccountInfo;
import com.afs.dao.model.AdResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PlatformCampaignCredentialTest {
    @ParameterizedTest
    @CsvSource({
            "TikTok,TIKTOK,https://business-api.tiktok.com/open_api/v1.3/campaign/create/,WEB_CONVERSIONS,$.operation_status,DISABLE",
            "Facebook,META,https://graph.facebook.com/v27.0/act_1234567890/campaigns,OUTCOME_TRAFFIC,$.status,PAUSED",
            "Google,GOOGLE,https://googleads.googleapis.com/v26/customers/1234567890/campaigns:mutate,DOES_NOT_CONTAIN_EU_POLITICAL_ADVERTISING,$.operations[0].create.status,PAUSED",
            "Taboola,TABOOLA,https://backstage.taboola.com/backstage/api/1.0/1234567890/campaigns/,unused,$.is_active,false"
    })
    void submitsSavedCredentialAndKeepsCampaignPaused(String platform, String provider, String url,
            String objective, String statusPath, String expectedStatus) throws Exception {
        var resources = mock(AdResourceMapper.class);
        var accounts = mock(AdAccountInfoMapper.class);
        var vault = mock(ChannelCredentialController.class);
        var customizer = new MockServerRestTemplateCustomizer();
        var controller = new PlatformCampaignController(resources, accounts, new ObjectMapper(),
                new RestTemplateBuilder(customizer), vault);
        var campaign = new AdResource();
        campaign.setAdResourceId("local-activity");
        campaign.setCampaign("Campaign credential test");
        campaign.setAdPlatform("[\"" + platform + "\"]");
        campaign.setState(AdResource.PASS);
        when(resources.getByAdResourceId("local-activity")).thenReturn(campaign);
        when(resources.claimPlatformCampaign("local-activity")).thenReturn(1);
        var account = new AdAccountInfo();
        account.setAdPlatform(platform);
        when(accounts.getByAccountId("1234567890")).thenReturn(account);
        when(vault.forPlatform(provider, "operations"))
                .thenReturn(new PlatformCredential("stored-access-token", "stored-developer-token", "998-123-4567"));

        String body = " { \"message\": \"Platform returned this exact text\", \"code\": 401 } ";
        var request = customizer.getServer().expect(requestTo(url)).andExpect(method(HttpMethod.POST))
                .andExpect(header(platform.equals("TikTok") ? "Access-Token" : "Authorization",
                        platform.equals("TikTok") ? "stored-access-token" : "Bearer stored-access-token"))
                .andExpect(platform.equals("Taboola") ? jsonPath(statusPath).value(false) : jsonPath(statusPath).value(expectedStatus));
        if (platform.equals("Google")) request.andExpect(header("developer-token", "stored-developer-token"))
                .andExpect(header("login-customer-id", "9981234567"));
        request.andRespond(withStatus(platform.equals("TikTok") ? HttpStatus.OK : HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body(body));

        var input = new PlatformCampaignController.Create("local-activity", "1234567890", objective, "123",
                "Brand", BigDecimal.ONE, BigDecimal.TEN, "operations");
        assertEquals(body, controller.create(input).msg);
        verify(resources).updateAdPlatformStateAndMsg("PLATFORM_REJECTED", body, "local-activity");
        customizer.getServer().verify();
    }
}
