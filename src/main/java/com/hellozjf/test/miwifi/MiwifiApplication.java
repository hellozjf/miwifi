package com.hellozjf.test.miwifi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.NetworkInterface;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class MiwifiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiwifiApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CommandLineRunner commandLineRunner(RestTemplate restTemplate, ObjectMapper objectMapper) {
        return args -> {

        };
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    private static String getMACAddress(InetAddress ia)throws Exception{
        //获得网络接口对象（即网卡），并得到mac地址，mac地址存在于一个byte数组中。
        byte[] mac = NetworkInterface.getByInetAddress(ia).getHardwareAddress();

        //下面代码是把mac地址拼装成String
        StringBuffer sb = new StringBuffer();

        for(int i=0;i<mac.length;i++){
            if(i!=0){
                sb.append(":");
            }
            //mac[i] & 0xFF 是为了把byte转化为正整数
            String s = Integer.toHexString(mac[i] & 0xFF);
            sb.append(s.length()==1?0+s:s);
        }

        //把字符串所有小写字母改为大写成为正规的mac地址并返回
        return sb.toString().toLowerCase();
    }

    /**
     * 每五分钟，获取一次IP地址，发送给阿里云服务器
     */
    @Scheduled(fixedRate = 1000 * 60 * 5)
    public void reportCurrent(){
        try {

            String key = "a2ffa5c9be07488bbb04a3a47d3c5f6a";
            String iv = "64175472480004614961023454661220";
            String pwd = "zjf13579";

            int type = 0;
            String deviceId = getMACAddress(InetAddress.getLocalHost());
            long time = System.currentTimeMillis() / 1000;
            int random = (int) (Math.random() * 10000);

            String username = "admin";
            String logtype = "2";
            String nonce = type + "_" + deviceId + "_" + time + "_" + random;
            String sha1Hex = DigestUtils.sha1Hex(pwd + key);
            String oldPwd = DigestUtils.sha1Hex(nonce + sha1Hex);

            // 获取token
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/x-www-form-urlencoded");
            MultiValueMap<String, Object> postParameters = new LinkedMultiValueMap<>();
            postParameters.add("username", username);
            postParameters.add("password", oldPwd);
            postParameters.add("logtype", logtype);
            postParameters.add("nonce", nonce);
            HttpEntity<MultiValueMap<String, Object>> httpEntity = new HttpEntity<>(postParameters, headers);
            String s = restTemplate.postForObject("http://miwifi.com/cgi-bin/luci/api/xqsystem/login", httpEntity, String.class);
            log.debug("s = {}", s);
            JsonNode jsonNode = objectMapper.readTree(s);
            String token = jsonNode.get("token").textValue();
            log.debug("token = {}", token);

            // 获取公网ip地址
            String url = "http://miwifi.com/cgi-bin/luci/;stok=" + token + "/api/xqnetwork/pppoe_status";
            String result = restTemplate.getForObject(url, String.class);
            jsonNode = objectMapper.readTree(result);
            String address = jsonNode.get("ip").get("address").textValue();
            log.debug("address = {}", address);

            // 向阿里云发送ip地址
            url = "http://aliyun.hellozjf.com:12306/postHomeAddr?ip=" + address;
            result = restTemplate.postForObject(url, null, String.class);
            log.debug("result = {}", result);

        } catch (Exception e) {
            log.error("e = {}", e);
        }
    }
}

