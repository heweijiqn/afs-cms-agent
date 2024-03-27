package com.afs.task;

import com.afs.dao.ArticleInfoMapper;
import com.afs.dao.CrawlContentMapper;
import com.afs.dao.model.ArticleInfo;
import com.afs.dao.model.CrawlContent;
import com.afs.dao.model.User;
import com.afs.service.HttpService;
import com.afs.utils.JsonUtils;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.HttpWebConnection;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NewDzenCrawlTask {

    @Value("${env}")
    String env;

    @Autowired
    HttpService httpService;
    @Autowired
    CrawlContentMapper crawlContentMapper;
    @Autowired
    ArticleInfoMapper articleInfoMapper;

    static String mUrl = "";
    static List<String> initUrlList = new ArrayList<String>() {
        {
//            add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5165&clid=1400&lang=ru");
//            add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5164&clid=1400&lang=ru");
//            add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5168&clid=1400&lang=ru");
//            add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5169&clid=1400&lang=ru");
//            add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5167&clid=1400&lang=ru");
//            add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5166&clid=1400&lang=ru");
//            add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5161&clid=1400&lang=ru");
//            add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5162&clid=1400&lang=ru");

//              add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5175&clid=1400&lang=ru");
//              add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5171&clid=1400&lang=ru");
//              add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5170&clid=1400&lang=ru");
//              add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5168&clid=1400&lang=ru");
//              add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5177&clid=1400&lang=ru");

//              add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5177&clid=1400&lang=ru");//pets
//              add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5167&clid=1400&lang=ru");//food
//              add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5169&clid=1400&lang=ru");//beauty

//            add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5316&clid=1400&lang=ru");//fitness
//            add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=3731&clid=1400&lang=ru");//food
//            add("https://dzen.ru/api/web/v1/topic-channel-more?country_code=ru&content_type=article&topic_identity=5175&clid=1400&lang=ru");//auto

        }
    };

//    @Async
//    @Scheduled(fixedDelay = 120 * 60 * 60 * 1000)
    void crawlArticleUid() throws InterruptedException, URISyntaxException {
        if (!env.equals("prod")) {
            return;
        }
        for (String url : initUrlList) {
            mUrl = url;
            try {
                for (int i = 0; i < 100; i++) {
                    Thread.sleep(1200);
                    String newUrl = getNewUrlRemoveSomeQuery(mUrl);
                    getNextDzenInfoFromUrl(newUrl);
                }
            } catch (Exception exception) {
                continue;
            }
        }
    }

    private String getNewUrlRemoveSomeQuery(String mUrl) throws URISyntaxException {
        URI uri = new URI(mUrl);
        Map<String, String> queryPairs = new HashMap<>();
        String[] queryParts = uri.getQuery().split("&");
        for (String part : queryParts) {
            String[] pair = part.split("=");
            queryPairs.put(pair[0], pair.length > 1 ? pair[1] : "");
        }
        queryPairs.remove("_csrf");
//        queryPairs.remove("next_page_id");
        queryPairs.remove("rnd");
        String newQuery = String.join("&", queryPairs.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.toList()));
        String newUri = uri.getScheme() + "://" + uri.getAuthority() + uri.getPath() + "?" + newQuery;
        return newUri;
    }

    private void getNextDzenInfoFromUrl(String url) {
        String responseStr = httpService.getDzenRequest(url);
        Map<String, Object> map = JsonUtils.toMap(responseStr);
        List<Map> itemMapList = (List<Map>) map.get("items");
        for (Map itemMap : itemMapList) {
            CrawlContent crawlContent = new CrawlContent();
            crawlContent.setFid((String) itemMap.get("id"));
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("title", itemMap.get("title"));
//            resultMap.put("text", itemMap.get("text"));
            resultMap.put("shareLink", itemMap.get("shareLink"));
            resultMap.put("views", itemMap.get("views"));
            resultMap.put("publicationDate", itemMap.get("publicationDate"));
            if (itemMap.get("mostRelevantTopicChannel") != null) {
                resultMap.put("thematic", ((Map)itemMap.get("mostRelevantTopicChannel")).get("thematic"));
            }
            crawlContent.setContent(JsonUtils.toJsonString(resultMap));
            crawlContent.setOnline(1);
            crawlContent.setContent((String) ((Map)itemMap.get("mostRelevantTopicChannel")).get("thematic"));
            crawlContent.setSource("dzen.ru");
            crawlContent.setStatus(CrawlContent.StatusInit);
            crawlContentMapper.add(crawlContent);
        }
        String link = null;
        if (map.get("more") != null && ((Map<String, Object>) map.get("more")).get("link") != null) {
            Map<String, Object> moreMap = (Map<String, Object>) map.get("more");
            link = (String) moreMap.get("link");
        }
        mUrl = link;
    }

//    @Async
//    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000)
    void gptTransferData() {
        log.info("gptTransferData list size={}", env);
        if (!env.equals("prod")) {
            return;
        }

        List<String> list = crawlContentMapper.getFidListByCategory("auto", CrawlContent.StatusCrawlDetailDone, "dzen.ru", 300);
//        list.addAll(crawlContentMapper.getFidListByCategory("auto", CrawlContent.StatusCrawlDetailDone, "dzen.ru", 500));
//        list.addAll(crawlContentMapper.getFidListByCategory("food", CrawlContent.StatusCrawlDetailDone, "dzen.ru", 500));
        log.info("gptTransferData list size={}", list.size());
        for (String fid : list) {
            try {
                CrawlContent crawlContent = crawlContentMapper.getByFid(fid);
                String contentJson = crawlContent.getContent();
                Map map = JsonUtils.toMap(contentJson);
                String title = (String) map.get("title");
//            String text = (String) map.get("text");
                String text = getTextFromJson((String) map.get("text"), (String) map.get("banner"), (String) map.get("shareLink"));
                String shareLink = (String) map.get("shareLink");
                String imgUrl = (String) map.get("banner");
                String description = (String) map.get("description");
                Integer views = (Integer) map.get("views");
                String publishedTime = (String) map.get("publicationDate");
                String thematic = (String) map.get("thematic");
                String titlePro = "Please translate the following text into English, return only translated content: " + title;
                String titleGpt = createContent(titlePro);
                String descriptionPro = "Please translate the following text into English, return only translated content: " + description;
                String descriptionGpt = createContent(descriptionPro);
                String ctPro = "Please translate the following text into English, If you encounter Html tags, please keep them. return only translated content:" + text;
                String textGpt = createContent(ctPro);

                ArticleInfo articleInfo = new ArticleInfo();
                String articleId = "article_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                articleInfo.setArticleId(articleId);
                articleInfo.setTitle(titleGpt);
                articleInfo.setTextList(textGpt);
                articleInfo.setFirstPart(descriptionGpt);
                articleInfo.setImgUrl(imgUrl);
                articleInfo.setTerms(shareLink);
                articleInfo.setCategory(thematic);
                articleInfo.setStrategy("");
                articleInfo.setPartner("nbt");
                articleInfo.setState(ArticleInfo.AUDITING);
                articleInfo.setSite("");
                articleInfo.setOnline(1);
                articleInfo.setInsideShow(1);
                articleInfo.setLang("en");
                Map<String, Object> otherMap = new HashMap<>();
                otherMap.put("crawlUid", fid);
                articleInfo.setOtherInfo(JsonUtils.toJsonString(otherMap));
                articleInfo.setStatus(ArticleInfo.StatusEnabled);
                articleInfoMapper.add(articleInfo);
                crawlContentMapper.updateStatus(fid, CrawlContent.StatusCrawlTransDone);
            } catch (Exception e) {
                log.error("gptTransferData error, fid={}", fid, e);
            }
        }
    }

    private String getTextFromJson(String text, String imgUrl, String shareLink) {
        Map<String, Object> map = JsonUtils.toMap(text);
        String cs = (String) map.get("contentState");
        Map<String, Object> csMap = JsonUtils.toMap(cs);
        log.debug("getTextFromJson csMap={}", csMap);
        Map<String, Object> draftJsStateMap = (Map<String, Object>) csMap.get("draftJsState");
        List<Map> blocksMapList = (List<Map>) draftJsStateMap.get("blocks");
        StringBuilder sb = new StringBuilder();
        sb.append("<p><img src=\"" + imgUrl + " alt=\"article image\" /></p>");
        for (Map itemMap : blocksMapList) {
            sb.append("<p>" + itemMap.get("text") + "</p>");
        }
        return sb.toString();
    }

    private String createContent(String pt) {
        Map<String, Object> map = new HashMap<>();
        map.put("model", "gpt-4o-mini");
        List<Map> list = new ArrayList<>();
        Map<String, Object> roleMap = new HashMap<>();
        roleMap.put("role", "system");
        roleMap.put("content", "You are a helpful assistant.");
        list.add(roleMap);
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("role", "user");
        userMap.put("content", pt);
        list.add(userMap);
        map.put("messages", list);

        String body = httpService.postGpt(JsonUtils.toJsonString(map), "https://api.openai.com/v1/chat/completions");
        Map resultMap = JsonUtils.toMap(body);
        List<Map> choicesList = (List<Map>) resultMap.get("choices");
        Map choiceMap = choicesList.get(0);
        Map msgMap = (Map) choiceMap.get("message");
        String content = (String) msgMap.get("content");
        return content;
    }

//    @Async
//    @Scheduled(fixedDelay = 2 * 60 * 60 * 1000)
    void putCategory() {
        log.info("putCategory env={}", env);
        if (!env.equals("prod")) {
            return;
        }
        List<String> list = crawlContentMapper.getFidListBySourceAndSize("dzen.ru", 10000);
        for (String fid : list) {
            try {
                CrawlContent crawlContent = crawlContentMapper.getByFid(fid);
                Map<String, Object> mapDb = JsonUtils.toMap(crawlContent.getContent());
                String thematic = (String) mapDb.get("thematic");
                crawlContentMapper.updateCategory(fid, thematic);
            } catch (Exception exception) {
                log.error("putCategory error", exception);
            }
        }
    }

//    @Async
//    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000)
    void crawlHtml() {
        log.info("crawlHtml env={}", env);
        if (!env.equals("prod")) {
            return;
        }
//        List<String> list = crawlContentMapper.getFidListByPage(CrawlContent.StatusInit, "dzen.ru", 1000);
        List<String> list = crawlContentMapper.getFidListByCategory("auto", CrawlContent.StatusInit, "dzen.ru", 500);
//        list.addAll(crawlContentMapper.getFidListByCategory("auto", CrawlContent.StatusInit, "dzen.ru", 500));
//        list.addAll(crawlContentMapper.getFidListByCategory("food", CrawlContent.StatusInit, "dzen.ru", 500));
        log.info("crawlHtml list={}", list);
        for (String fid : list) {
            try {
                CrawlContent crawlContent = crawlContentMapper.getByFid(fid);
                Map<String, Object> mapDb = JsonUtils.toMap(crawlContent.getContent());
                Map<String, Object> map = new HashMap();
                String htmlPageStr = getHtmlPage((String) mapDb.get("shareLink"));
                if (StringUtils.isNotBlank(htmlPageStr)) {
                    Document doc = Jsoup.parse(htmlPageStr);
                    Element head = doc.head();
                    Elements metaElements = head.select("meta");
                    for (Element meta : metaElements) {
                        if ("description".equals(meta.attr("name"))) {
                            map.put("description", meta.attr("content"));
                        }
                        if ("og:image".equals(meta.attr("property"))) {
                            map.put("banner", meta.attr("content"));
                        }
                        log.debug("meta name={}, property={}, content={} " + meta.attr("name"), meta.attr("property"), meta.attr("content"));
                    }

                    Elements scriptElements = doc.select("script");
                    for (Element script : scriptElements) {
                        String scriptData = script.data();
                        if (scriptData.contains("articleContent") && scriptData.contains("contentState")) {
                            String str1 = scriptData.substring(scriptData.indexOf("\"articleContent\":") + 17, scriptData.indexOf("\"preview\":"));
                            String str2 = str1.substring(0, str1.lastIndexOf(","));
                            map.put("text", str2);
                        }
                    }
                    mapDb.putAll(map);
                }
                crawlContentMapper.updateContentByFid(fid, JsonUtils.toJsonString(mapDb));
                crawlContentMapper.updateStatus(fid, CrawlContent.StatusCrawlDetailDone);
            } catch (Exception exception) {
                log.error("crawlHtml error fid={}", fid, exception);
            }
        }
    }

    private String getHtmlPage(String url) {
        final WebClient webClient = new WebClient(BrowserVersion.CHROME);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setActiveXNative(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setDownloadImages(false);
        webClient.getOptions().setUseInsecureSSL(true);
        webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        try {
            HttpWebConnection webConnection = new HttpWebConnection(webClient);
            webClient.setWebConnection(webConnection);
            HtmlPage page =webClient.getPage(url);
            String content = page.asXml();
            webClient.waitForBackgroundJavaScript(30 * 1000);//异步JS执行需要耗时,所以这里线程要阻塞30秒,等待异步JS执行结束
            return content;
        } catch (Exception e) {
            log.error("handleHtmlFile error.", e);
        }finally {
            webClient.getCurrentWindow().getJobManager().removeAllJobs();
            webClient.close();
            System.gc();
        }
        return "";
    }
}
