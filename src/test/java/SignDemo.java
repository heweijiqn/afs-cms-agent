import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

@Slf4j
public class SignDemo {
    public static final String secure_key = "local-signing-example-key";

    public static void main(String[] args) {
        TreeMap<String, Object> request = new TreeMap<String, Object>();
        request.put("appId", "app_bc41a296ba");
        request.put("timestamp", 1715184000000l);
//        request.put("regionList", "CHN");
        request.put("dateStart", "2024-05-01");
        request.put("dateEnd", "2024-05-01");
        log.info("sign={}", getSign(request, secure_key));
    }

    /*
    param: secure_key: your secure_key
     */
    public static String getSign(Map<String, Object> request, String secure_key) {

        Set<Map.Entry<String, Object>> entries = request.entrySet();
        String str = "";
        int i = 0;
        for (Map.Entry<String, Object> e : entries) {
            i++;
            str += e.getKey();
            str += "=";
            str += e.getValue().toString();
            if (i != request.size()) {
                str += "&";
            }
        }
        str += "&secretKey=" + secure_key;
        return getMD5Str(str);

    }

    public static String getMD5Str(String str) {
        byte[] digest = null;
        try {
            MessageDigest md5 = MessageDigest.getInstance("md5");
            digest = md5.digest(str.getBytes("utf-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        StringBuilder md5StrBuilder = new StringBuilder();
        for (byte tmp : Objects.requireNonNull(digest)) {
            md5StrBuilder.append(String.format("%02x", tmp & 0xff));
        }
        return md5StrBuilder.toString();
    }

}