package de.gekko.websocketNew;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * Scraper to bypass Cloudflare DDOS/anti-bot security.
 * @author Maximilian Pfister
 *
 */
public class CloudflareScraper {

	/* constants */

	// TODO remove because unused
	private final String[] USER_AGENTS = {
			"Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36",
			"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.102 Safari/537.36",
			"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36",
			"Mozilla/5.0 (Windows NT 6.1; WOW64; rv:46.0) Gecko/20100101 Firefox/46.0",
			"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:41.0) Gecko/20100101 Firefox/41.0" };

	private final Pattern OPERATION_PATTERN = Pattern.compile("setTimeout\\(function\\(\\)\\{\\s+(var s,t,o,p,b,r,e,a,k,i,n,g,f.+?\\r?\\n[\\s\\S]+?a\\.value =.+?)\\r?\\n");
	private final Pattern PASS_PATTERN = Pattern.compile("name=\"pass\" value=\"(.+?)\"");
	private final Pattern CHALLENGE_PATTERN = Pattern.compile("name=\"jschl_vc\" value=\"(\\w+)\"");
	//TODO private final Pattern FORM_ACTION = Pattern.compile("<form id=\"challenge-form\" action=\"(.+?)\"");

	/* variables */

	private HttpClient httpClient;

	/* constructors */

	public CloudflareScraper(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	/* public methods */

	public boolean checkAndSolve(HttpResponse response, String domain) {
		boolean ret = false;
		HttpEntity entity = response.getEntity();
		String responseContent = "";
		try {
			responseContent = EntityUtils.toString(entity, StandardCharsets.UTF_8.name());
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		System.out.println(responseContent);
		
		// Check if Cloudflare anti-bot is on
		if (response.getLastHeader("Server").getValue().equals("cloudflare-nginx")
				&& responseContent.contains("jschl_vc")
				&& responseContent.contains("jschl_answer")) {
			if (!cloudFlareSolve(responseContent, domain)) {
				// TODO THROW EXCEPTION
			}
			ret = true;
		}

		try {
			EntityUtils.consume(entity);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ret;

	}
	
	/* private methods */

	/**
	 * Solves the Cloudflare challange and sends it as response.
	 * @param responseString
	 * @return
	 */
	private boolean cloudFlareSolve(String responseString, String domain) {
		// initialize Rhino
		Context rhino = Context.enter();
		try {

			// CF waits for response after some delay
			Thread.sleep(5000);

			// pull out the arithmetic
			Matcher operationSearch = OPERATION_PATTERN.matcher(responseString);
			Matcher challengeSearch = CHALLENGE_PATTERN.matcher(responseString);
			Matcher passSearch = PASS_PATTERN.matcher(responseString);
//			Matcher formAction = FORM_ACTION.matcher(responseString);
			if (!operationSearch.find() || !passSearch.find() || !challengeSearch.find()) {
				return false;
			}

			String rawOperation = operationSearch.group(1); // operation
			String challengePass = passSearch.group(1); // key
			String challenge = challengeSearch.group(1); // hash

			// Cut out the assignment of the variable
			String operation = rawOperation.replaceAll("a\\.value = (parseInt\\(.+?\\)).+", "$1")
					.replaceAll("\\s{3,}[a-z](?: = |\\.).+", "");

			// Strip characters that could be used to exit the string context
			// These characters are not currently used in Cloudflare's arithmetic snippet
			String js = operation.replace("\n", "");

			// rhino.setOptimizationLevel(-1); // without this line rhino will not run under
			// Android
			Scriptable scope = rhino.initStandardObjects(); // initialize the execution space

			// either do or die trying
			int result = ((Double) rhino.evaluateString(scope, js, "CloudFlare JS Challenge", 1, null)).intValue();
			String answer = String.valueOf(result + domain.length()); // answer to the javascript challenge

			// Construct answer request
			String host = domain + "/cdn-cgi/l/chk_jschl";

			URIBuilder builder = new URIBuilder();
			builder.setScheme("http").setHost(host).setParameter("jschl_vc", challenge)
					.setParameter("pass", challengePass).setParameter("jschl_answer", answer);

			HttpGet request = new HttpGet(builder.build());
			request.setHeader(HttpHeaders.REFERER, "http://" + domain + "/");

			// Send answer request
			HttpResponse response = httpClient.execute(request);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				// Do something with content if needed
//				String responseContent = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8.name());
//				System.out.println(responseContent);
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			Context.exit(); // turn off Rhino
		}
		return false;
	}

}
