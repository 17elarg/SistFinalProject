package data.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.tomcat.util.json.JSONParser;
import org.apache.tomcat.util.json.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import data.dto.UserDto;
import data.mapper.LoginMapper;

@Controller
public class NaverService {
	
	@Autowired
	LoginMapper mapper;

  private String CLIENT_ID = "lAKSwHKCFttsryyK8HWl"; //애플리케이션 클라이언트 아이디값";
  private String CLI_SECRET = "LsHuqKXvaP"; //애플리케이션 클라이언트 시크릿값";
  
  /**
   * 콜백 페이지 컨트롤러
   * @param session
   * @param request
   * @param model
   * @return
   * @throws IOException
   * @throws ParseException
   */
  @RequestMapping("/login/callback1")
  public String naverCallback1(HttpSession session, HttpServletRequest request, Model model) throws IOException, ParseException {

    String code = request.getParameter("code");
    String state = request.getParameter("state");
    String redirectURI = URLEncoder.encode("http://localhost:9003/login/callback1", "UTF-8");

    String apiURL;
    apiURL = "https://nid.naver.com/oauth2.0/token?grant_type=authorization_code&";
    apiURL += "client_id=" + CLIENT_ID;
    apiURL += "&client_secret=" + CLI_SECRET;
    apiURL += "&redirect_uri=" + redirectURI;
    apiURL += "&code=" + code;
    apiURL += "&state=" + state;
    System.out.println("apiURL=" + apiURL);

    String res = requestToServer(apiURL);
    if(res != null && !res.equals("")) {
      model.addAttribute("res", res);
      Map<String, Object> parsedJson = new JSONParser(res).parseObject();
      System.out.println(parsedJson);
      session.setAttribute("currentUser", res);
      session.setAttribute("currentAT", parsedJson.get("access_token"));
      session.setAttribute("currentRT", parsedJson.get("refresh_token"));
    } else {
      model.addAttribute("res", "Login failed!");
    }
    
    return "redirect:/login/getProfile";
  }

  /**
   * 토큰 갱신 요청 페이지 컨트롤러
   * @param session
   * @param request
   * @param model
   * @param refreshToken
   * @return
   * @throws IOException
   * @throws ParseException
   */
  @RequestMapping("/login/refreshToken")
  public String refreshToken(HttpSession session, HttpServletRequest request, Model model, String refreshToken) throws IOException, ParseException {

    String apiURL;

    apiURL = "https://nid.naver.com/oauth2.0/token?grant_type=refresh_token&";
    apiURL += "client_id=" + CLIENT_ID;
    apiURL += "&client_secret=" + CLI_SECRET;
    apiURL += "&refresh_token=" + refreshToken;

    System.out.println("apiURL=" + apiURL);

    String res = requestToServer(apiURL);
    model.addAttribute("res", res);
    session.invalidate();
    return "test-naver-callback";
  }

  /**
   * 토큰 삭제 컨트롤러
   * @param session
   * @param request
   * @param model
   * @param accessToken
   * @return
   * @throws IOException
   */
  @RequestMapping("/login/deleteToken")
  public String deleteToken(HttpSession session, HttpServletRequest request, Model model, String accessToken) throws IOException {

    String apiURL;

    apiURL = "https://nid.naver.com/oauth2.0/token?grant_type=delete&";
    apiURL += "client_id=" + CLIENT_ID;
    apiURL += "&client_secret=" + CLI_SECRET;
    apiURL += "&access_token=" + accessToken;
    apiURL += "&service_provider=NAVER";

    System.out.println("apiURL=" + apiURL);

    String res = requestToServer(apiURL);
    model.addAttribute("res", res);
    session.invalidate();
    return "test-naver-callback";
  }

  /**
   * 액세스 토큰으로 네이버에서 프로필 받기
   * @param accessToken
   * @return
   * @throws IOException
   */
  
  @RequestMapping("/login/getProfile")
  public String getProfileFromNaver(HttpSession session) throws IOException {
	  
	  String token = (String)session.getAttribute("currentAT"); //네이버 로그인 접근 토근
	  String header = "Bearer " + token; // Bearer 다음에 공백 추가
	  
	  String apiURL = "https://openapi.naver.com/v1/nid/me";
	  
	  Map<String, String> requestHeaders = new HashMap<>();
      requestHeaders.put("Authorization", header);
      String responseBody = get(apiURL,requestHeaders); //JSON으로 반환


      System.out.println("response body : " + responseBody);
    
	  JsonParser parser = new JsonParser();
	  JsonElement element = parser.parse(responseBody); // json > java 변환
	  JsonObject response = element.getAsJsonObject().get("response").getAsJsonObject();
	  
	  String name = response.getAsJsonObject().get("name").getAsString();
	  String email = response.getAsJsonObject().get("email").getAsString();
      int idx = email.indexOf("@");
      String id = email.substring(0, idx);
      
      if(mapper.getIdCheck(id) == 0) {
      	UserDto dto = new UserDto();
      	dto.setId(id);
          dto.setName(name);
          dto.setEmail(email);
          mapper.insertUser(dto);
      }
	  
	  session.setAttribute("nick", name);
      session.setAttribute("logintype", "user");
      session.setAttribute("snspass", "yes");
      session.setAttribute("loginok", "yes");
      session.setAttribute("myid", id);
    
	  return "/login/tomain";
}
  
  private static String get(String apiUrl, Map<String, String> requestHeaders){
      HttpURLConnection con = connect(apiUrl);
      try {
          con.setRequestMethod("GET");
          for(Map.Entry<String, String> header :requestHeaders.entrySet()) {
              con.setRequestProperty(header.getKey(), header.getValue());
          }


          int responseCode = con.getResponseCode();
          if (responseCode == HttpURLConnection.HTTP_OK) { // 정상 호출
              return readBody(con.getInputStream());
          } else { // 에러 발생
              return readBody(con.getErrorStream());
          }
      } catch (IOException e) {
          throw new RuntimeException("API 요청과 응답 실패", e);
      } finally {
          con.disconnect();
      }
  }
  
  private static HttpURLConnection connect(String apiUrl){
      try {
          URL url = new URL(apiUrl);
          return (HttpURLConnection)url.openConnection();
      } catch (MalformedURLException e) {
          throw new RuntimeException("API URL이 잘못되었습니다. : " + apiUrl, e);
      } catch (IOException e) {
          throw new RuntimeException("연결이 실패했습니다. : " + apiUrl, e);
      }
  }


  private static String readBody(InputStream body){
      InputStreamReader streamReader = new InputStreamReader(body);


      try (BufferedReader lineReader = new BufferedReader(streamReader)) {
          StringBuilder responseBody = new StringBuilder();


          String line;
          while ((line = lineReader.readLine()) != null) {
              responseBody.append(line);
          }


          return responseBody.toString();
      } catch (IOException e) {
          throw new RuntimeException("API 응답을 읽는데 실패했습니다.", e);
      }
  }

  /**
   * 세션 무효화(로그아웃)
   * @param session
   * @return
   */
  @RequestMapping("/login/invalidate")
  public String invalidateSession(HttpSession session) {
    session.invalidate();
    return "redirect:/naver";
  }

  /**
   * 서버 통신 메소드
   * @param apiURL
   * @return
   * @throws IOException
   */
  private String requestToServer(String apiURL) throws IOException {
    return requestToServer(apiURL, "");
  }

  /**
   * 서버 통신 메소드
   * @param apiURL
   * @param headerStr
   * @return
   * @throws IOException
   */
  private String requestToServer(String apiURL, String headerStr) throws IOException {
    URL url = new URL(apiURL);
    HttpURLConnection con = (HttpURLConnection)url.openConnection();
    con.setRequestMethod("GET");

    System.out.println("header Str: " + headerStr);
    if(headerStr != null && !headerStr.equals("") ) {
      con.setRequestProperty("Authorization", headerStr);
    }

    int responseCode = con.getResponseCode();
    BufferedReader br;

    System.out.println("responseCode="+responseCode);

    if(responseCode == 200) { // 정상 호출
      br = new BufferedReader(new InputStreamReader(con.getInputStream()));
    } else {  // 에러 발생
      br = new BufferedReader(new InputStreamReader(con.getErrorStream()));
    }
    String inputLine;
    StringBuffer res = new StringBuffer();
    while ((inputLine = br.readLine()) != null) {
      res.append(inputLine);
    }
    br.close();
    if(responseCode==200) {
      return res.toString();
    } else {
      return null;
    }

  }

}